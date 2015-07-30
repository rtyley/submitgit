package lib

import org.scalatestplus.play.PlaySpec

class MarkdownParsingSpec extends PlaySpec {

  val prComment =
    """
      |@rtyley sent this to the mailing list with [_submitGit_](https://github.com/rtyley/submitgit) - here on [Google Groups](https://groups.google.com/d/msgid/submitgit-test/0000014e6f0b65e9-e3f67cd3-6679-41ed-b2e2-819766dbe2a0-000000@eu-west-1.amazonses.com) [](0000014e6f0b65e9-e3f67cd3-6679-41ed-b2e2-819766dbe2a0-000000@eu-west-1.amazonses.com)
    """.stripMargin

  "Markdown parsing" should {
    "get hidden message ids" in {
      val links = MarkdownParsing.parseHiddenLinksOutOf(
        "[](0000014e69391015-5de3c8c6-458f-4eb6-b222-14cfc8a6b055-000000@eu-west-1.amazonses.com)"
      )

      links mustEqual Seq("0000014e69391015-5de3c8c6-458f-4eb6-b222-14cfc8a6b055-000000@eu-west-1.amazonses.com")
    }
    "get hidden message id in real message" in {
      val links = MarkdownParsing.parseHiddenLinksOutOf(prComment)

      links mustEqual Seq("0000014e6f0b65e9-e3f67cd3-6679-41ed-b2e2-819766dbe2a0-000000@eu-west-1.amazonses.com")
    }
  }
}

