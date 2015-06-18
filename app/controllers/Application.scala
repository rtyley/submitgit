package controllers

import java.io.File

import com.madgag.github.Implicits._
import com.madgag.github.{PullRequestId, RepoId}
import lib.MailType.proposedMailByTypeFor
import lib._
import lib.actions.Actions._
import lib.actions.Requests._
import lib.aws.SES._
import lib.aws.SesAsyncHelpers._
import lib.model.{PatchBomb, PatchCommit, Patch}
import org.eclipse.jgit.lib.ObjectId
import org.kohsuke.github._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc._
import views.html.pullRequestSent
import play.api.i18n.Messages.Implicits._

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object Application extends Controller {

  import play.api.Play.current

  def index = Action { implicit req =>
    Ok(views.html.index())
  }

  def listPullRequests(repoId: RepoId) = githubRepoAction(repoId) { implicit req =>
    val myself = req.gitHub.getMyself
    val openPRs = req.repo.getPullRequests(GHIssueState.OPEN)
    val (userPRs, otherPRs) = openPRs.partition(_.getUser.equals(myself))
    val alternativePRs = otherPRs.toStream ++ req.repo.listPullRequests(GHIssueState.CLOSED).toStream

    Ok(views.html.listPullRequests(userPRs, alternativePRs.take(3)))
  }

  def reviewPullRequest(prId: PullRequestId) = githubPRAction(prId).async { implicit req =>
    val myself = req.gitHub.getMyself

    implicit val form = mailSettingsForm.fill(PRMailSettings("PATCH"))
    for (proposedMailByType <- proposedMailByTypeFor(req)) yield {
      Ok(views.html.reviewPullRequest(req.pr, myself, proposedMailByType))
    }
  }

  def acknowledgePreview(prId: PullRequestId, headCommit: ObjectId, signature: String) =
    (GitHubAuthenticatedAction andThen verifyCommitSignature(headCommit, Some(signature))).async {
      implicit req =>
        val userEmail = req.userEmail.getEmail

        def whatDoWeTellTheUser(userEmail: String, verificationStatusOpt: Option[VerificationStatus]): Future[Option[(String, String)]] = {
          verificationStatusOpt match {
            case Some(VerificationStatus.Success) => // Nothing to do
              Future.successful(None)
            case Some(VerificationStatus.Pending) => // Remind user to click the link in their email
              Future.successful(Some("notifyEmailVerification" -> userEmail))
            case _ => // send verification email, tell user to click on it
              ses.sendVerificationEmailTo(userEmail).map(_ => Some("notifyEmailVerification" -> userEmail))
          }
        }

        for {
          verificationStatusOpt <- ses.getIdentityVerificationStatusFor(userEmail)
          flashOpt <- whatDoWeTellTheUser(userEmail, verificationStatusOpt)
        } yield {
          Redirect(routes.Application.reviewPullRequest(prId)).addingToSession(PreviewSignatures.keyFor(headCommit) -> signature).flashing(flashOpt.toSeq: _*)
        }
    }

  val mailSettingsForm = Form(
    mapping(
      "subjectPrefix" -> nonEmptyText(maxLength = 20)
    )(PRMailSettings.apply)(PRMailSettings.unapply)
  )

  def mailPullRequest(prId: PullRequestId, mailType: MailType) = (githubPRAction(prId) andThen mailChecks(mailType)).async(parse.form(mailSettingsForm)) {
    implicit req =>
      val mailingList = Project.byRepoId(req.repo.id).mailingList

      val addresses = mailType.addressing(mailingList, req.user)
      
      for (patchCommits <- req.patchCommitsF) yield {
        val patchBomb = PatchBomb(patchCommits, addresses, req.body.subjectPrefix, mailType.subjectPrefix, mailType.footer(req.pr))
        for (initialMessageId <- ses.send(patchBomb.emails.head)) {
          for (email <- patchBomb.emails.drop(1)) {
            ses.send(email.inReplyTo(initialMessageId))
          }

          mailType.afterSending(req.pr, initialMessageId)
        }
        Ok(pullRequestSent(req.pr, req.user, mailType))
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



