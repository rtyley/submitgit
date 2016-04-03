package lib

import javax.mail.internet.InternetAddress

import com.github.nscala_time.time.Imports._
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.commands.CreateComment
import com.madgag.scalagithub.model.PullRequest
import controllers.routes
import lib.actions.Requests._
import lib.checks.{Check, GHChecks, PRChecks}
import lib.model.PatchBomb
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait MailType {
  def afterSending(pr: PullRequest, patchBomb: PatchBomb, messageId: String, prMailSettings: PRMailSettings)(implicit g: GitHub): PRMailSettings

  val slug: String

  val subjectPrefix: Option[String]

  def addressing(mailingList: MailingList, userEmailAddress: InternetAddress): Email.Addresses

  def footer(pr: PullRequest)(implicit request: RequestHeader): String

  val checks: Seq[Check[GHPRRequest[_]]]
}

object MailType {

  def internetAddressFor(emailAddress: String, displayName: String) =
    new InternetAddress(emailAddress, displayName)

  val all = Seq(Preview, Live)

  val bySlug = all.map(mt => mt.slug -> mt).toMap

  def proposedMailFor(mt: MailType)(implicit req: GHPRRequest[_]): Future[ProposedMail] = for {
    errors <- Check.all(req, mt.checks)
  } yield ProposedMail(mt.addressing(Project.byRepoId(req.repo.repoId).mailingList, req.user.address), errors)

  def proposedMailByTypeFor(implicit req: GHPRRequest[_]): Future[Map[MailType, ProposedMail]] =
    Future.traverse(MailType.all)(mt => proposedMailFor(mt).map(mt -> _)).map(_.toMap)

  object Preview extends MailType {
    override val slug = "submit-preview"

    def footer(pr: PullRequest)(implicit request: RequestHeader): String = {
      val headCommitId = pr.head.sha
      val ackUrl = routes.Application.acknowledgePreview(pr.prId, headCommitId, PreviewSignatures.signatureFor(headCommitId)).absoluteURL
      s"This is a preview - if you want to send this message for real, click here:\n$ackUrl"
    }

    override def addressing(mailingList: MailingList, userEmail: InternetAddress) = Email.Addresses(
      from = new InternetAddress("submitGit <submitgit@gmail.com>"),
      to = Seq(userEmail)
    )

    override val subjectPrefix = Some("TEST")

    import GHChecks._
    import PRChecks._

    val checks = Seq(
      accountIsOlderThan(1.hour),
      EmailVerified,
      HasAReasonableNumberOfCommits
    )

    override def afterSending(request: PullRequest, patchBomb: PatchBomb, messageId: String, prMailSettings: PRMailSettings)(implicit g: GitHub) = {
      // Nothing for preview
      prMailSettings
    }
  }

  object Live extends MailType {
    override def footer(pr: PullRequest)(implicit request: RequestHeader): String = pr.html_url

    override def addressing(mailingList: MailingList, userEmail: InternetAddress) = Email.Addresses(
      from = userEmail,
      to = Seq(mailingList.emailAddress),
      bcc = Seq(userEmail)
    )

    override val subjectPrefix = None
    override val slug = "submit"

    import GHChecks._
    import PRChecks._

    val AccountAgeThreshold = 1.day

    val generalChecks = Seq(
      EmailVerified,
      accountIsOlderThan(AccountAgeThreshold),
      UserHasNameSetInProfile,
      RegisteredEmailWithSES
    )

    val checks = generalChecks ++ Seq(
      UserOwnsPR,
      PRIsOpen,
      HasBeenPreviewed,
      HasAReasonableNumberOfCommits
    )

    override def afterSending(pr: PullRequest, patchBomb: PatchBomb, messageId: String, prMailSettings: PRMailSettings)(implicit g: GitHub) = {
      val mailingListLinks = Project.byRepoId(pr.baseRepo.repoId).mailingList.archives.map(a => s"[${a.providerName}](${a.linkFor(messageId)})").mkString(", ")

      val numCommits = patchBomb.patchCommits.size
      val patchBombDesc = if (numCommits == 1) {
        s"this commit (${pr.compareUrl}) as a patch"
      } else {
        s"these $numCommits commits (${pr.compareUrl}) as a set of patches"
      }

      pr.comments2.create(CreateComment(
        s"${pr.user.atLogin} sent $patchBombDesc to the mailing list with [_submitGit_](https://github.com/rtyley/submitgit) - " +
          s"here on $mailingListLinks []($messageId)"
      ))
      prMailSettings.afterBeingUsedToSend(messageId)
    }
  }
}






