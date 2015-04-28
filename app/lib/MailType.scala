package lib

import controllers.Application
import org.kohsuke.github.{GHPullRequest, GHMyself}
import play.api.mvc.RequestHeader
import lib.github.Implicits._

sealed trait MailType {
  val subjectPrefix: Option[String]

  def addressing(pr: GHMyself): Email.Addresses

  def footer(pr: GHPullRequest)(implicit request: RequestHeader): String
}

object MailType {
  object Preview extends MailType {
    def footer(pr: GHPullRequest)(implicit request: RequestHeader): String = s"${Application.routeMailPullRequest(pr).absoluteURL}"

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
  }
}
