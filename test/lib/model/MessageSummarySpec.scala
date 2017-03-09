package lib.model

import org.scalatestplus.play.PlaySpec

import scalax.io.Resource

class MessageSummarySpec extends PlaySpec {

  val awsSesPost = Resource.fromClasspath("samples/raw.posts/raw.posted-by-aws-ses.txt").string
  val googleGroupsPost = Resource.fromClasspath("samples/raw.posts/raw.posted-by-google-groups.txt").string
  val publicInbox = Resource.fromClasspath("samples/raw.posts/raw.public-inbox.txt").string

  "Message summary from raw" should {
    "parse a message posted by AWS SES" in {
      val messageSummary = MessageSummary.fromRawMessage(awsSesPost, "")
      messageSummary.subject mustEqual "[PATCH/RFC v245] Update gc.c"
    }

    "parse a message posted by the Google Groups interface" in {
      val messageSummary = MessageSummary.fromRawMessage(googleGroupsPost, "")
      messageSummary.subject mustEqual "Scary fudge"
    }

    "parse a message stored on public-inbox.org" in {
      val messageSummary = MessageSummary.fromRawMessage(publicInbox, "")
      messageSummary.subject mustEqual "[PATCH] daemon: add systemd support"
    }
  }
}
