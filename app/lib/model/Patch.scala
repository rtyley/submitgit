package lib.model

import fastparse.all._
import org.eclipse.jgit.lib.ObjectId

case class Patch(commitId: ObjectId, body: String)

object PatchParsing {

  val line = P(CharsWhile(_ != '\n', min = 0) ~ "\n")

  val nonEmptyLine = P(CharsWhile(_ != '\n', min = 1) ~ "\n")

  val hexDigit = P( CharIn('0'to'9', 'a'to'f', 'A'to'F') )

  val objectId: P[ObjectId] = P(hexDigit.rep(40).!).map(ObjectId.fromString)

  val patchFromHeader = P("From " ~ objectId ~ " Mon Sep 17 00:00:00 2001\n")

  val patchHeaderRegion: P[ObjectId] = P(patchFromHeader ~/ nonEmptyLine.rep)

  val headerKey = P(CharsWhile(_ != ':', min = 1).! ~ ": ")

  val newlineChars = Set('\n', '\r')

  val newline = "\r\n" | "\n"

  val whitespace = P(CharIn(" \t"))

  val nonNewlineChars = CharsWhile(!newlineChars(_), min = 1)

  val headerValue = P((nonNewlineChars ~ (newline ~ whitespace.rep(min = 1) ~ nonNewlineChars).rep).!)

  val header: P[(String, String)] = P(headerKey ~ headerValue)

  val headers: P[Seq[(String, String)]] = P(header.rep(sep = newline))

  val nonHeaderLines = (!header ~ line).rep(sep = newline)

  /* For some reason the 'raw' messages at public-inbox.org have an initial line which isn't a standard
   * message header - eg: "From mboxrd@z Thu Jan  1 00:00:00 1970" does not have a colon (':')
   *
   * https://public-inbox.org/git/6a72bfd4-5032-5e40-5c6d-8b77ca5ae775@gmail.com/raw
   */
  val messageHeaders: P[Seq[(String, String)]] = nonHeaderLines ~ headers

  val patchBodyRegion = P((line ~ !patchFromHeader).rep.!)

  val patch: P[Patch] = P(patchHeaderRegion ~ "\n" ~/ patchBodyRegion).map(Patch.tupled)

  val patches: P[Seq[Patch]] = P(patch.rep(sep = "\n"))

}