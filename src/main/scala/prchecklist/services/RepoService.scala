package prchecklist.services

import prchecklist.models._

import com.github.tarao.slickjdbc.interpolation.SQLInterpolation

import slick.driver.PostgresDriver.api.DBIO
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods // q.transactionally

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object RepoService extends SQLInterpolation {
  def get(owner: String, name: String): Future[Option[Repo]] = {
    val db = Database.get
    db.run(getQuery(owner, name))
  }

  def create(githubRepo: GitHubTypes.Repo, defaultAccessToken: String): Future[(Repo, Boolean)] = {
    val db = Database.get

    val (owner, name) = (githubRepo.owner, githubRepo.name)

    val q = getQuery(owner, name).flatMap {
      case Some(repo) =>
        DBIO.successful((repo, false))

      case None =>
        sql"""
          | INSERT INTO github_repos
          |   (owner, name, default_access_token)
          | VALUES
          |   (${owner}, ${name}, ${defaultAccessToken})
          | RETURNING id
        """.as[Int].head.map {
          id => (Repo(id, owner, name, defaultAccessToken), true)
        }
    }

    db.run(q.transactionally)
  }

  // TODO: visibility
  // TODO: paging
  def list(): Future[List[Repo]] = {
    val db = Database.get

    val q = sql"""
      | SELECT id, owner, name, default_access_token FROM github_repos
    """.as[(Int, String, String, String)].map {
      _.map(Repo.tupled).toList
    }

    db.run(q)
  }

  private def getQuery(owner: String, name: String): DBIO[Option[Repo]] = {
    sql"""
      | SELECT id, default_access_token
      | FROM github_repos
      | WHERE owner = ${owner}
      |   AND name = ${name}
      | LIMIT 1
    """.as[(Int, String)].map(_.headOption.map {
      case (id, defaultAccessToken) => Repo(id, owner, name, defaultAccessToken)
    })
  }

}
