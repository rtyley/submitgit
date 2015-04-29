package lib

import controllers.{routes, Application}
import org.kohsuke.github.{GHPullRequest, GHMyself}
import play.api.mvc.RequestHeader
import lib.github.Implicits._

sealed trait MailType {
  val slug: String

  val subjectPrefix: Option[String]

  def addressing(pr: GHMyself): Email.Addresses

  def footer(pr: GHPullRequest)(implicit request: RequestHeader): String
}

object MailType {
  val all = Seq(Preview, Live)

  val bySlug = all.map(mt => mt.slug -> mt).toMap

  object Preview extends MailType {
    override val slug = "submit-preview"

    def footer(pr: GHPullRequest)(implicit request: RequestHeader): String = {
      val repo = pr.getRepository
      val headCommitId = pr.getHead.objectId
      val acknowledgementUrl = routes.Application.acknowledgePreview(repo.getOwnerName, repo.getName, pr.getNumber, headCommitId, PreviewSignatures.signatureFor(headCommitId)).absoluteURL
      s"Click here to confirm you've previewed this submission:\n$acknowledgementUrl"
    }

    override def addressing(user: GHMyself) = Email.Addresses(
      from = "submitGit <submitgit@gmail.com>",
      to = Seq(user.primaryEmail.getEmail)
    )

    override val subjectPrefix = Some("[TEST]")
  }

  object Live extends MailType {
    override def footer(pr: GHPullRequest)(implicit request: RequestHeader): String = pr.getHtmlUrl.toString

    override def addressing(user: GHMyself) = Email.Addresses(
      from = user.primaryEmail.getEmail,
      to = Seq("submitgit-test@googlegroups.com"),
      bcc = Seq(user.primaryEmail.getEmail)
    )

    override val subjectPrefix = None
    override val slug = "submit"
  }
}
