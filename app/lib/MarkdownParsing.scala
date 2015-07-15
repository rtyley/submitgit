package lib

import fastparse.all._

object MarkdownParsing {

  val hiddenLinkRegex = """\[\]\((.+?)\)""".r

  def parseHiddenLinksOutOf(markdown: String): Seq[String] =
    (for (m <- hiddenLinkRegex.findAllMatchIn(markdown)) yield m.group(1)).toSeq
}
