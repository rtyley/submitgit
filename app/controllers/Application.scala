package controllers

import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.model.{PullRequestId, RepoId, User}
import lib.MailType.proposedMailByTypeFor
import lib._
import lib.actions.Actions._
import lib.actions.Requests._
import lib.aws.SES._
import lib.aws.SesAsyncHelpers._
import lib.model.PRMessageIdFinder.messageIdsByMostRecentUsageIn
import lib.model.PatchBomb
import org.eclipse.jgit.lib.ObjectId
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc._
import views.html.pullRequestSent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {

  import play.api.Play.current

  def index = Action { implicit req =>
    Ok(views.html.index())
  }

  def listPullRequests(repoId: RepoId) = githubRepoAction(repoId).async { implicit req =>
    implicit val g = req.gitHub // use bot instead?

    for {
      openPRs <- req.repo.pullRequests.list(Map("state"->"open")).all()
      closedPRs <- req.repo.pullRequests.list(Map("state"->"closed")).all()
      myself: User <- req.userF
    } yield {
      req.repo.issues.list(Map("creator"->myself.login)).map(_.flatMap(_.pull_request).map(_.fetch()))

      val (userOpenPRs, otherOpenPRs) = openPRs.partition(_.user.id.equals(myself.id))

      val alternativePRs = otherOpenPRs ++ closedPRs

      Ok(views.html.listPullRequests(userOpenPRs, alternativePRs.take(3)))
    }
  }

  def reviewPullRequest(prId: PullRequestId) = githubPRAction(prId).async { implicit req =>
    implicit val g = req.gitHub
    for {
      messageIds <- messageIdsByMostRecentUsageIn(req.pr)
      commits <- req.pr.commits.list().all()
      proposedMailByType <- proposedMailByTypeFor(req)
    } yield {
      implicit val form = mailSettingsForm.fill(settingsFor(req, messageIds))
      Ok(views.html.reviewPullRequest(commits, proposedMailByType, messageIds))
    }
  }

  def settingsFor(req: GHPRRequest[_], messageIds: Seq[String]): PRMailSettings = (for {
      data <- req.session.get(req.pr.prId.slug)
      s <- Json.parse(data).validate[PRMailSettings].asOpt
    } yield s).getOrElse(PRMailSettings("PATCH", messageIds.headOption))

  def acknowledgePreview(prId: PullRequestId, headCommit: ObjectId, signature: String) =
    (GitHubAuthenticatedAction andThen verifyCommitSignature(headCommit, Some(signature))).async {
      implicit req =>
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
          userPrimaryEmail <- req.userPrimaryEmailF
          userEmail = userPrimaryEmail.email
          verificationStatusOpt <- ses.getIdentityVerificationStatusFor(userEmail)
          flashOpt <- whatDoWeTellTheUser(userEmail, verificationStatusOpt)
        } yield {
          Redirect(routes.Application.reviewPullRequest(prId)).addingToSession(PreviewSignatures.keyFor(headCommit) -> signature).flashing(flashOpt.toSeq: _*)
        }
    }

  val mailSettingsForm = Form(
    mapping(
      "subjectPrefix" -> default(text(maxLength = 20), "PATCH"),
      "inReplyTo" -> optional(text)
    )(PRMailSettings.apply)(PRMailSettings.unapply)
  )

  def mailPullRequest(prId: PullRequestId, mailType: MailType) = (githubPRAction(prId) andThen mailChecks(mailType)).async(parse.form(mailSettingsForm)) {
    implicit req =>
      implicit val g = req.gitHub
      val mailingList = Project.byRepoId(req.repo.repoId).mailingList

      val addresses = mailType.addressing(mailingList, req.user.address)

      val settings = req.body
      
      for {
        patchCommits <- req.patchCommitsF
        patchBomb = PatchBomb(patchCommits, addresses, settings.subjectPrefix, mailType.subjectPrefix, mailType.footer(req.pr))
        initialEmail = patchBomb.emails.head
        initialMessageId <- ses.send(settings.inReplyTo.fold(initialEmail)(initialEmail.inReplyTo))
      } yield {
        for (email <- patchBomb.emails.tail) {
          ses.send(email.inReplyTo(initialMessageId))
        }
        val updatedSettings = mailType.afterSending(req.pr, patchBomb, initialMessageId, settings)
        Ok(pullRequestSent(req.user, mailType)).addingToSession(prId.slug -> toJson(updatedSettings).toString)
      }
  }
}
