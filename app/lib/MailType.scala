package lib

import com.github.nscala_time.time.Imports._
import com.madgag.github.Implicits._
import controllers.routes
import lib.PRMailSettings
import lib.actions.Requests._
import lib.checks.{Check, GHChecks, PRChecks}
import org.kohsuke.github.{GHMyself, GHPullRequest}
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait MailType {
  def afterSending(pr: GHPullRequest, messageId: String, prMailSettings: PRMailSettings): PRMailSettings

  val slug: String

  val subjectPrefix: Option[String]

  def addressing(mailingList: MailingList, user: GHMyself): Email.Addresses

  def footer(pr: GHPullRequest)(implicit request: RequestHeader): String

  val checks: Seq[Check[GHPRRequest[_]]]
}

object MailType {
  
  def userEmailString(user: GHMyself) = s"${user.displayName} <${user.primaryEmail.getEmail}>"
  
  val all = Seq(Preview, Live)

  val bySlug = all.map(mt => mt.slug -> mt).toMap

  def proposedMailFor(mt: MailType)(implicit req: GHPRRequest[_]): Future[ProposedMail] = for {
    errors <- Check.all(req, mt.checks)
  } yield ProposedMail(mt.addressing(Project.byRepoId(req.repo.id).mailingList, req.user), errors)

  def proposedMailByTypeFor(implicit req: GHPRRequest[_]): Future[Map[MailType, ProposedMail]] =
    Future.traverse(MailType.all)(mt => proposedMailFor(mt).map(mt -> _)).map(_.toMap)

  object Preview extends MailType {
    override val slug = "submit-preview"

    def footer(pr: GHPullRequest)(implicit request: RequestHeader): String = {
      val repo = pr.getRepository
      val headCommitId = pr.getHead.objectId
      val ackUrl = routes.Application.acknowledgePreview(pr.id, headCommitId, PreviewSignatures.signatureFor(headCommitId)).absoluteURL
      s"This is a preview - if you want to send this message for real, click here:\n$ackUrl"
    }

    override def addressing(mailingList: MailingList, user: GHMyself) = Email.Addresses(
      from = "submitGit <submitgit@gmail.com>",
      to = Seq(userEmailString(user))
    )

    override val subjectPrefix = Some("TEST")

    import GHChecks._
    import PRChecks._

    val checks = Seq(
      accountIsOlderThan(1.hour),
      EmailVerified,
      HasAReasonableNumberOfCommits
    )

    override def afterSending(request: GHPullRequest, messageId: String, prMailSettings: PRMailSettings) = {
      // Nothing for preview
      prMailSettings
    }
  }

  object Live extends MailType {
    override def footer(pr: GHPullRequest)(implicit request: RequestHeader): String = pr.getHtmlUrl.toString

    override def addressing(mailingList: MailingList, user: GHMyself) = {
      val userEmail = userEmailString(user)
      Email.Addresses(
        from = userEmail,
        to = Seq(mailingList.emailAddress),
        bcc = Seq(userEmail)
      )
    }

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

    override def afterSending(pr: GHPullRequest, messageId: String, prMailSettings: PRMailSettings) = {
      val mailingListLinks = Project.byRepoId(pr.id.repo).mailingList.archives.map(a => s"[${a.providerName}](${a.linkFor(messageId)})").mkString(", ")
      pr.comment(
        s"${pr.getUser.atLogin} sent this to the mailing list with [_submitGit_](https://github.com/rtyley/submitgit) - " +
          s"here on $mailingListLinks []($messageId)")
      prMailSettings.afterBeingUsedToSend(messageId)
    }
  }
}






