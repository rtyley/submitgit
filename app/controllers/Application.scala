package controllers

import java.io.{File, FileInputStream}

import com.madgag.github.Implicits._
import com.madgag.github.{PullRequestId, RepoId}
import lib.MailType.proposedMailByTypeFor
import lib._
import lib.actions.Actions._
import lib.actions.Requests._
import lib.aws.SES._
import lib.aws.SesAsyncHelpers._
import lib.model.PRMessageIdFinder.messageIdsByMostRecentUsageIn
import lib.model.{MessageSnowflake, PatchBomb}
import org.eclipse.jgit.lib.ObjectId
import org.kohsuke.github._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc._
import views.html.pullRequestSent

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

    lazy val defaultInitialSettings =
      PRMailSettings("PATCH", messageIdsByMostRecentUsageIn(req.pr).headOption, Some(req.pr.getBody))

    val settings = (for {
      data <- req.session.get(prId.slug)
      s <- Json.parse(data).validate[PRMailSettings].asOpt
    } yield s).getOrElse(defaultInitialSettings)

    implicit val form = mailSettingsForm.fill(settings)
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
      "subjectPrefix" -> default(text(maxLength = 20), "PATCH"),
      "inReplyTo" -> optional(text),
      "coverLetter" -> optional(text)
    )(PRMailSettings.apply)(PRMailSettings.unapply)
  )

  def mailPullRequest(prId: PullRequestId, mailType: MailType) = (githubPRAction(prId) andThen mailChecks(mailType)).async(parse.form(mailSettingsForm)) {
    implicit req =>
      val mailingList = Project.byRepoId(req.repo.id).mailingList

      val addresses = mailType.addressing(mailingList, req.user)

      val settings = req.body
      val coverLetterOpt = settings.coverLetter.map(body => MessageSnowflake(req.pr.getTitle,body))
      
      for {
        patchCommits <- req.patchCommitsF
        patchBomb = PatchBomb(patchCommits, addresses, settings.subjectPrefix, mailType.subjectPrefix, coverLetterOpt, mailType.footer(req.pr))
        initialEmail = patchBomb.emails.head
        initialMessageId <- ses.send(settings.inReplyTo.fold(initialEmail)(initialEmail.inReplyTo))
      } yield {
        for (email <- patchBomb.emails.tail) {
          ses.send(email.inReplyTo(initialMessageId))
        }
        val updatedSettings = mailType.afterSending(req.pr, initialMessageId, settings)
        Ok(pullRequestSent(req.pr, req.user, mailType)).addingToSession(prId.slug -> toJson(updatedSettings).toString)
      }
  }

  lazy val gitCommitId = gitCommitIdFromHerokuFile.getOrElse(app.BuildInfo.gitCommitId)

  def gitCommitIdFromHerokuFile: Option[String]  = {
    val existingFileOpt: Option[File] = herokuMetadataFile()

    Logger.debug(s"Heroku dyno metadata: $existingFileOpt")

    for {
      existingFile <- existingFileOpt
      commitId <- (Json.parse(new FileInputStream(existingFile)) \ "release" \ "commit").asOpt[String]
    } yield {
      Logger.debug(s"Heroku dyno commit id: $commitId")
      commitId
    }
  }

  def herokuMetadataFile(): Option[File] = {
    val file = new File("/etc/heroku/dyno")
    if (file.exists && file.isFile) Some(file) else None
  }
}
