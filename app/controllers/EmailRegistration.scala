package controllers

import com.madgag.playgithub.auth.GHRequest
import lib.AppUser
import lib.actions.Actions._
import lib.aws.SES._
import lib.aws.SesAsyncHelpers._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

object EmailRegistration extends Controller {

  val GitHubUserWithVerifiedEmail: ActionBuilder[GHRequest] =
    GitHubAuthenticatedAction andThen EnsureGitHubVerifiedEmail

  def isRegisteredEmail(email: String) = GitHubUserWithVerifiedEmail.async {
    for (status <- ses.getIdentityVerificationStatusFor(email)) yield Ok(status.map(_.string).getOrElse("Unknown"))
  }

  def registerEmail = GitHubUserWithVerifiedEmail.async { req =>
    for {
      appUser <- AppUser.from(req)
      res <- ses.sendVerificationEmailTo(appUser.address.getAddress)
    } yield Ok("Registration email sent")
  }

}
