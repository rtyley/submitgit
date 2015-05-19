package lib

import org.specs2.mutable.Specification

class MailArchiveSpec extends Specification {
  "Google Groups" should {
    val submitGitGoogleGroup = GoogleGroup("submitgit-test")

    "have a proper link for a message-id" in {
      GoogleGroup("foo").linkFor("bar@baz.com").toString mustEqual
        "https://groups.google.com/forum/#!searchin/foo/messageid:bar@baz.com"

      submitGitGoogleGroup.linkFor("0000014d6db28338-50d4e2f9-590b-485f-a43d-3f8bceebf0ed-000000@eu-west-1.amazonses.com").toString mustEqual
        "https://groups.google.com/forum/#!searchin/submitgit-test/messageid:0000014d6db28338-50d4e2f9-590b-485f-a43d-3f8bceebf0ed-000000@eu-west-1.amazonses.com"
    }
    "link to google group" in {
      submitGitGoogleGroup.url mustEqual "https://groups.google.com/forum/#!forum/submitgit-test"
    }
    "derive correct email address" in {
      submitGitGoogleGroup.emailAddress mustEqual "submitgit-test@googlegroups.com"
    }
  }
  "Gmane" should {
    "have a proper link for a message-id" in {
      Gmane.Git.linkFor("1431830650-111684-1-git-send-email-shawn@churchofgit.com").toString mustEqual
        "http://mid.gmane.org/1431830650-111684-1-git-send-email-shawn@churchofgit.com"
    }
  }
}
