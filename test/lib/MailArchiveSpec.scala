package lib

import javax.mail.internet.MailDateFormat

import com.madgag.okhttpscala._
import com.netaporter.uri.dsl._
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request.Builder
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scalax.io.Resource

object Network {
  import scala.concurrent.duration._
  
  val okClient = new OkHttpClient()
  val secureGoogleRequest = new Builder().url("https://www.google.com/").build()

  def isAvailable: Boolean =
    Try(Await.result(okClient.execute(secureGoogleRequest)(_.isSuccessful), 2 second)).getOrElse(false)
}

class MailArchiveSpec extends PlaySpec with ScalaFutures with IntegrationPatience {
  "Google Groups" should {
    val submitGitGoogleGroup = GoogleGroup("submitgit-test")

    "have a proper link for a message-id" in {
      GoogleGroup("foo").linkFor("bar@baz.com").toString mustEqual
        "https://groups.google.com/d/msgid/foo/bar@baz.com"

      submitGitGoogleGroup.linkFor("0000014d9a92ef17-abf8da02-8ed6-4f1e-b959-db3d4617e750-000000@eu-west-1.amazonses.com").toString mustEqual
        "https://groups.google.com/d/msgid/submitgit-test/0000014d9a92ef17-abf8da02-8ed6-4f1e-b959-db3d4617e750-000000@eu-west-1.amazonses.com"
    }
    "link to google group" in {
      submitGitGoogleGroup.url mustEqual "https://groups.google.com/forum/#!forum/submitgit-test"
    }
    "derive correct email address" in {
      submitGitGoogleGroup.emailAddress.toString mustEqual "submitgit-test@googlegroups.com"
    }
    "give correct raw article url" in {
      submitGitGoogleGroup.rawUrlFor("/forum/#!msg/submitgit-test/-cq4q1w7jyY/A-EH61BAZaUJ").toString mustEqual
        "https://groups.google.com/forum/message/raw?msg=submitgit-test/-cq4q1w7jyY/A-EH61BAZaUJ"
    }
    "get message data" in {
      assume(Network.isAvailable)
      val messageId = "349d78e1-3c4f-4415-908d-599a57d15008@googlegroups.com"
      whenReady(submitGitGoogleGroup.lookupMessage(messageId)) { s =>
        val messageSummary = s.head
        messageSummary.id mustEqual messageId
        messageSummary.subject mustEqual "Chalk"
      }
    }
    "get '[PATCH/RFC v245] Update gc.c' message data posted by submitGit using AWS SES" in {
      assume(Network.isAvailable)
      val messageId = "0000014e69391015-5de3c8c6-458f-4eb6-b222-14cfc8a6b055-000000@eu-west-1.amazonses.com"
      whenReady(submitGitGoogleGroup.lookupMessage(messageId)) { s =>
        val messageSummary = s.head
        messageSummary.id mustEqual messageId
        messageSummary.subject mustEqual "[PATCH/RFC v245] Update gc.c"
      }
    }
  }
  "Gmane" should {
    "have a proper link for a message-id" in {
      Gmane.Git.linkFor("1431830650-111684-1-git-send-email-shawn@churchofgit.com").toString mustEqual
        "http://mid.gmane.org/1431830650-111684-1-git-send-email-shawn@churchofgit.com"
    }
    "give correct raw article url" in {
      Gmane.Git.rawUrlFor("http://article.gmane.org/gmane.comp.version-control.git/269205").toString mustEqual
        "http://article.gmane.org/gmane.comp.version-control.git/269205/raw"
    }
    "get message data" in {
      assume(Network.isAvailable)
      val messageId = "1431830650-111684-1-git-send-email-shawn@churchofgit.com"
      whenReady(Gmane.Git.lookupMessage(messageId)) { s =>
        val messageSummary = s.head
        messageSummary.id mustEqual messageId
        messageSummary.subject mustEqual "[PATCH] daemon: add systemd support"
      }
    }
  }
  "MARC" should {
    "deobfuscate this crazy stuff" in {
      Marc.deobfuscate("Roberto Tyley &lt;roberto.tyley () gmail ! com&gt;") mustEqual
        "Roberto Tyley <roberto.tyley@gmail.com>"
    }
    "have MessageSummary parsed (totally hackishly) from html - because the alternate raw version excludes headers" in {
      val message =
        Marc.messageSummaryFor(Resource.fromClasspath("samples/mailarchives/marc/m.143228360912708.html").string)

      message.id mustEqual "CAFY1edY3+Wt-p2iQ5k64Fg-nMk2PmRSvhVkQSVNw94R18uPV2Q@mail.gmail.com"
      message.date.toInstant mustEqual new MailDateFormat().parse("Fri, 22 May 2015 09:33:20 +0100").toInstant
      message.subject mustEqual
        "[Announce] submitGit for patch submission (was \"Diffing submodule does not yield complete logs\")"
    }
  }
}
