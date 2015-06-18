package lib

import java.nio.file.Paths

import com.madgag.github.OkGitHub

object Bot {

  val okGitHub = new OkGitHub(Paths.get("/tmp/submitgit/working-dir/http-response-cache/"))

  import play.api.Play.current
  val config = play.api.Play.configuration

  val accessToken = config.getString("github.botAccessToken").get

  def conn() = okGitHub.conn(accessToken)
}
