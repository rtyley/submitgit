/*
 * Copyright 2014 The Guardian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.io.File

import com.squareup.okhttp.OkHttpClient
import lib._
import lib.github.{GitHubAuthResponse, Implicits}
import lib.github.Implicits._
import org.eclipse.jgit.lib.ObjectId
import org.kohsuke.github._
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.Json
import play.api.libs.mailer.{Email, MailerPlugin}
import play.api.libs.ws.WS
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc._
import lib.okhttpscala._
import com.squareup.okhttp

import scala.collection.convert.wrapAsScala._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object Application extends Controller {

  val rootRepo = "rtyley/bfg-repo-cleaner" // "git/git"

  val AccessTokenSessionKey = "githubAccessToken"

  import GithubAppConfig._
  val ghAuthUrl = s"$authUrl?client_id=$clientId&scope=$scope"

  def index = Action { implicit req =>
    Ok(views.html.index())
  }

  val bareAccessTokenRequest = {
    import GithubAppConfig._
    WS.url(accessTokenUrl)
      .withQueryString(("client_id", clientId), ("client_secret", clientSecret))
      .withHeaders(("Accept", "application/json"))
  }

  def oauthCallback(code: String) = Action.async {
    for (response <- bareAccessTokenRequest.withQueryString("code" -> code).post("")) yield {
      val accessToken = response.json.validate[GitHubAuthResponse].get.access_token
      Redirect("/").withSession(AccessTokenSessionKey -> accessToken)
    }
  }

  val redirectToGitHubForAuth = Redirect(ghAuthUrl)

  def userGitHubConnectionForSessionAccessToken(req: RequestHeader): Option[GitHub] = for {
    accessToken <- req.session.get(AccessTokenSessionKey)
    gitHubConn <- Try(GitHub.connectUsingOAuth(accessToken)).toOption
  } yield gitHubConn

  val GitHubAuthenticatedAction = new AuthenticatedBuilder(userGitHubConnectionForSessionAccessToken, _ => redirectToGitHubForAuth)

  def listPullRequests = GitHubAuthenticatedAction { implicit req =>
    val myself = req.user.getMyself
    val userPRs = req.user.getRepository(rootRepo).getPullRequests(GHIssueState.OPEN).filter(_.getUser.equals(myself))

    Ok(views.html.listPullRequests(userPRs, myself))
  }

  def mailPullRequest(number: Int) = GitHubAuthenticatedAction.async {


    implicit req =>

      def emailFor(patch: Patch): Email = {
        val user = req.user.getMyself

        Email(
          subject = patch.subject,
          from = user.primaryEmail.getEmail, //commit.getAuthor.getEmail
          to = Seq(user.primaryEmail.getEmail),
          cc = Seq("autoanswer@vger.kernel.org"),
          bodyText = Some(patch.body)
        )
      }
    val pullRequest = req.user.getRepository(rootRepo).getPullRequest(number)

    val commits = pullRequest.listCommits().toSeq

    require(commits.size < 10)

    val patchUrl = pullRequest.getPatchUrl.toString
    for {
      resp <- new OkHttpClient().execute(new okhttp.Request.Builder().url(patchUrl).build())
    } yield {
      val patch = resp.body.string

      val commitsAndPatches = Patches.commitsAndPatches(commits.map(c => ObjectId.fromString(c.getSha)), patch)

      val initialMessageId = MailerPlugin.send(emailFor(commitsAndPatches.head._2))

      for ((commit, patch) <- commitsAndPatches.drop(1)) {
        println(MailerPlugin.send(emailFor(patch).copy(headers = Seq("References" -> initialMessageId))))
      }

      // pullRequest.comment("Closed by submitgit")
      // pullRequest.close()
      Ok("whatever")
    }
  }

  lazy val gitCommitId = {
    val g = gitCommitIdFromHerokuFile
    Logger.info(s"Heroku dyno commit id $g")
    g.getOrElse(app.BuildInfo.gitCommitId)
  }

  def gitCommitIdFromHerokuFile: Option[String]  = {
    val file = new File("/etc/heroku/dyno")
    val existingFile = if (file.exists && file.isFile) Some(file) else None

    Logger.info(s"Heroku dyno metadata $existingFile")

    for {
      f <- existingFile
      text <- (Json.parse(scala.io.Source.fromFile(f).mkString) \ "release" \ "commit").asOpt[String]
      objectId <- Try(ObjectId.fromString(text)).toOption
    } yield objectId.name
  }
}


