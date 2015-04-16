package lib.github

import play.api.libs.json.Json

object GitHubAuthResponse {
  implicit val readsGitHubAuthResponse = Json.reads[GitHubAuthResponse]
}

case class GitHubAuthResponse(access_token: String)
