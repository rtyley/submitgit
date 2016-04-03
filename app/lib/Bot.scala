package lib

import com.madgag.scalagithub.{GitHub, GitHubCredentials}

import scalax.file.ImplicitConversions._
import scalax.file.Path

object Bot {

  val workingDir = Path.fromString("/tmp") / "bot" / "working-dir"

  import play.api.Play.current
  val config = play.api.Play.configuration

  val accessToken = config.getString("github.botAccessToken").get

  val ghCreds = GitHubCredentials.forAccessKey(accessToken, workingDir.toPath).get

  def conn() = new GitHub(ghCreds)
}
