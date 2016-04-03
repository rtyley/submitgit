package lib

import javax.mail.internet.InternetAddress

import com.madgag.playgithub.auth.GHRequest
import com.madgag.scalagithub
import scala.concurrent.ExecutionContext.Implicits.global

case class AppUser(
  ghUser: scalagithub.model.User,
  primaryEmail: scalagithub.model.Email
) {
  lazy val address = new InternetAddress(primaryEmail.email, ghUser.displayName)
}

object AppUser {
  implicit def oGHUser(appUser: AppUser): scalagithub.model.User = appUser.ghUser

  def from(ghRequest: GHRequest[_]) = for {
    user <- ghRequest.userF
    userEmails <- ghRequest.userEmailsF
  } yield AppUser(user, userEmails.find(_.primary).get)
}