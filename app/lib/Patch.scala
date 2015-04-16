package lib

case class Patch(lines: Seq[String]) {
  val headerLines = lines.drop(1).takeWhile(_.contains(": "))

  val headers: Map[String, String] = (headerLines.map {
    line =>
      val (key, value) = line.splitAt(line.indexOf(": "))
      key -> value.drop(2)
  }).toMap

  val subject = headers("Subject")

  val body = lines.dropWhile(_.trim.nonEmpty).drop(1).mkString
}
