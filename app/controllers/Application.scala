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

import com.squareup.okhttp
import com.squareup.okhttp.OkHttpClient
import controllers.Actions._
import lib.Email.Addresses
import lib._
import lib.aws.SES._
import lib.aws.SesAsyncHelpers._
import lib.github.GitHubAuthResponse
import lib.github.Implicits._
import lib.okhttpscala._
import org.eclipse.jgit.lib.ObjectId
import org.kohsuke.github._
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._
import views.html.pullRequestSent

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object Application extends Controller {

  type AuthRequest[A] = AuthenticatedRequest[A, GitHub]

  val repoWhiteList= Set("git/git", "submitgit/pretend-git")

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
      Redirect(routes.Application.listPullRequests("git","git")).withSession(AccessTokenSessionKey -> accessToken)
    }
  }

  def listPullRequests(repoOwner: String, repoName: String) = githubRepoAction(repoOwner, repoName) { implicit req =>
    val myself = req.gitHub.getMyself
    val openPRs = req.repo.getPullRequests(GHIssueState.OPEN)
    val (userPRs, otherPRs) = openPRs.partition(_.getUser.equals(myself))

    Ok(views.html.listPullRequests(userPRs, otherPRs))
  }

  def routeReviewPullRequest(pr: GHPullRequest) = {
    val repo = pr.getRepository
    routes.Application.reviewPullRequest(repo.getOwnerName, repo.getName, pr.getNumber)
  }

  def routeMailPullRequest(pr: GHPullRequest, mailType: MailType) = {
    val repo = pr.getRepository
    routes.Application.mailPullRequest(repo.getOwnerName, repo.getName, pr.getNumber, mailType)
  }

  def reviewPullRequest(repoOwner: String, repoName: String, number: Int) = githubPRAction(repoOwner, repoName, number) { implicit req =>
    val myself = req.gitHub.getMyself

    Ok(views.html.reviewPullRequest(req.pr, myself))
  }

  def acknowledgePreview(repoOwner: String, repoName: String, number: Int, headCommit: ObjectId, signature: String) =
    (Action andThen verifyCommitSignature(headCommit, Some(signature))) {
    implicit req =>
    Redirect(routes.Application.reviewPullRequest(repoOwner, repoName, number)).addingToSession(PreviewSignatures.keyFor(headCommit) -> signature)
  }

  /**
   * Test emails: your email must be verified with GitHub and older than 1 week? Can do anyone's PR (but still restrict to only whitelisted repos?)
   * Mailing list emails: GitHub account older than 3 months & email registered with submitGit. You must have created the PR
   */
  def mailPullRequest(repoOwner: String, repoName: String, number: Int, mailType: MailType) = (githubPRAction(repoOwner, repoName, number) andThen legitFilter).async {
    implicit req =>

      val addresses = mailType.addressing(req.user)

      def emailFor(patch: Patch)= Email(
          addresses,
          subject = (mailType.subjectPrefix ++ Seq(patch.subject)).mkString(" "),
          bodyText = s"${patch.body}\n---\n${mailType.footer(req.pr)}"
        )

      val pullRequest = req.repo.getPullRequest(number)

      val commits = pullRequest.listCommits().toSeq

      require(commits.size < 10)

      val patchUrl = pullRequest.getPatchUrl.toString
      for {
        resp <- new OkHttpClient().execute(new okhttp.Request.Builder().url(patchUrl).build())
      } yield {
        val patch = resp.body.string

        val commitsAndPatches = Patches.commitsAndPatches(commits.map(c => ObjectId.fromString(c.getSha)), patch)
        for (initialMessageId <- ses.send(emailFor(commitsAndPatches.head._2))) {
          for ((commit, patch) <- commitsAndPatches.drop(1)) {
            ses.send(emailFor(patch).copy(headers = Seq("References" -> initialMessageId, "In-Reply-To" -> initialMessageId)))
          }
        }

        // pullRequest.comment("Closed by submitgit")
        // pullRequest.close()
        Ok(pullRequestSent(pullRequest, req.user, mailType))
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



