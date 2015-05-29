package lib

trait MailArchive {
  val providerName: String

  val url: String

  def linkFor(messageId: String): String
}

case class Gmane(groupName: String) extends MailArchive {
  val providerName = "Gmane"

  val url = s"http://dir.gmane.org/gmane.$groupName"

  def linkFor(messageId: String) = s"http://mid.gmane.org/$messageId"
}

object Gmane {
  val Git = Gmane("comp.version-control.git")
}

case class MailArchiveDotCom(emailAddress: String) extends MailArchive {
  val providerName = "mail-archive.com"

  val url = s"https://www.mail-archive.com/$emailAddress"

  // mail-archive.com actually doesn't not seem to work on message-id search for recent Git messages
  def linkFor(messageId: String) = s"http://mid.mail-archive.com/$messageId"
}

case class GoogleGroup(groupName: String) extends MailArchive {

  val providerName = "Google Groups"

  val url = s"https://groups.google.com/forum/#!forum/$groupName"

  // https://groups.google.com/d/msgid/submitgit-test/0000014d9a92ef17-abf8da02-8ed6-4f1e-b959-db3d4617e750-000000@eu-west-1.amazonses.com
  def linkFor(messageId: String) = s"https://groups.google.com/d/msgid/$groupName/$messageId"

  // submitgit-test@googlegroups.com
  val emailAddress = s"$groupName@googlegroups.com"

  val mailingList = MailingList(emailAddress, Seq(this))
}
