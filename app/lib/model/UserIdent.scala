package lib.model

import org.kohsuke.github.GitUser

case class UserIdent(name: String, email: String) {
  lazy val userEmailString = s"$name <$email>"
}

object UserIdent {
  def from(gu: GitUser) = UserIdent(gu.getName, gu.getEmail)
}
