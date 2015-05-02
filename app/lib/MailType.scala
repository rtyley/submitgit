package lib

import controllers.{GHPRRequest, routes}
import lib.checks.{Check, GHChecks, PRChecks}
import lib.github.Implicits._
import org.kohsuke.github.{GHMyself, GHPullRequest}
import play.api.mvc.RequestHeader

import com.github.nscala_time.time.Imports._

sealed trait MailType {
  val slug: String

  val subjectPrefix: Option[String]

  def addressing(pr: GHMyself): Email.Addresses

  def footer(pr: GHPullRequest)(implicit request: RequestHeader): String

  val checks: Seq[Check[GHPRRequest[_]]]
}

object MailType {
  
  def userEmailString(user: GHMyself): String = s"${user.displayName} <${user.primaryEmail.getEmail}>"
  
  val all = Seq(Preview, Live)

  val bySlug = all.map(mt => mt.slug -> mt).toMap

  object Preview extends MailType {
    override val slug = "submit-preview"

    def footer(pr: GHPullRequest)(implicit request: RequestHeader): String = {
      val repo = pr.getRepository
      val headCommitId = pr.getHead.objectId
      val ackUrl = routes.Application.acknowledgePreview(repo.getOwnerName, repo.getName, pr.getNumber, headCommitId, PreviewSignatures.signatureFor(headCommitId)).absoluteURL
      s"Click here to confirm you've previewed this submission:\n$ackUrl"
    }

    override def addressing(user: GHMyself) = Email.Addresses(
      from = "submitGit <submitgit@gmail.com>",
      to = Seq(userEmailString(user))
    )

    override val subjectPrefix = Some("[TEST]")

    import GHChecks._
    val checks = Seq(
      EmailVerified
    )
  }

  object Live extends MailType {
    override def footer(pr: GHPullRequest)(implicit request: RequestHeader): String = pr.getHtmlUrl.toString

    override def addressing(user: GHMyself) = {
      val userEmail = userEmailString(user)
      Email.Addresses(
        from = userEmail,
        to = Seq("submitgit-test@googlegroups.com"),
        bcc = Seq(userEmail)
      )
    }

    override val subjectPrefix = None
    override val slug = "submit"

    import GHChecks._
    import PRChecks._

    val checks = Seq(
      EmailVerified,
      accountIsOlderThan(3.months),
      UserHasNameSetInProfile,
      UserOwnsPR,
      PRIsOpen,
      HasBeenPreviewed
    )
  }
}






