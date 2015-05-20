package lib

import com.github.nscala_time.time.Imports._
import controllers.{GHPRRequest, routes}
import lib.checks.{Check, GHChecks, PRChecks}
import lib.github.Implicits._
import org.kohsuke.github.{GHPullRequestCommitDetail, GHMyself, GHPullRequest}
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait MailType {
  def afterSending(pr: GHPullRequest, commitsAndPatches: Seq[(GHPullRequestCommitDetail, Patch)], messageId: String)

  val slug: String

  val subjectPrefix: Option[String]

  def addressing(mailingList: MailingList, user: GHMyself): Email.Addresses

  def footer(pr: GHPullRequest)(implicit request: RequestHeader): String

  val checks: Seq[Check[GHPRRequest[_]]]
}

object MailType {
  
  def userEmailString(user: GHMyself): String = s"${user.displayName} <${user.primaryEmail.getEmail}>"
  
  val all = Seq(Preview, Live)

  val bySlug = all.map(mt => mt.slug -> mt).toMap

  def proposedMailFor(mt: MailType)(implicit req: GHPRRequest[_]): Future[ProposedMail] = for {
    errors <- Check.all(req, mt.checks)
  } yield ProposedMail(mt.addressing(Project.byRepoName(req.repo.id).mailingList, req.user), errors)

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

    override val subjectPrefix = Some("[TEST]")

    import GHChecks._
    import PRChecks._

    val checks = Seq(
      accountIsOlderThan(1.hour),
      EmailVerified,
      HasAReasonableNumberOfCommits
    )

    override def afterSending(request: GHPullRequest, commitsAndPatches: Seq[(GHPullRequestCommitDetail, Patch)], messageId: String) {
      // Nothing for preview
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

    override def afterSending(pr: GHPullRequest, commitsAndPatches: Seq[(GHPullRequestCommitDetail, Patch)], messageId: String) {
      val mailingListLinks = Project.byRepoName(pr.id.repoName).mailingList.archives.map(a => s"[${a.providerName}](${a.linkFor(messageId)})").mkString(", ")
      pr.comment(
        s"I've sent this PR to the mailing list with [_submitGit_](https://github.com/rtyley/submitgit) - " +
          s"here on $mailingListLinks []($messageId)")
    }
  }
}






