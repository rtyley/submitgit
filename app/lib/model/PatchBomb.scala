package lib.model

import java.text.NumberFormat

import lib.Email
import lib.Email.Addresses
import lib.model.PatchBomb.countTextFor

import scala.collection.SortedMap

case class PatchBomb(
  patchCommits: Seq[PatchCommit],
  addresses: Addresses,
  shortDescription: String = "PATCH",
  additionalPrefix: Option[String] = None,
  coverLetterOpt: Option[MessageSnowflake],
  footer: String
) {
  lazy val emails: Seq[Email] = {
    val patchCommitSnowflakes: Seq[(Int, MessageSnowflake)] = for ((patchCommit, commitIndex) <- patchCommits.zipWithIndex) yield {
      val authorEmailString = patchCommit.commit.author.userEmailString
      val fromBodyPrefixOpt = Some(s"From: $authorEmailString\n").filterNot(_ => authorEmailString == addresses.from)
      commitIndex + 1 -> MessageSnowflake(
        patchCommit.commit.subject,
        (fromBodyPrefixOpt.toSeq :+ patchCommit.patch.body).mkString("\n")
      )
    }

    val snowflakesWithIndices = SortedMap[Int, MessageSnowflake](patchCommitSnowflakes ++ coverLetterOpt.map(0 -> _):_*)

    for ((index, snowflake) <- snowflakesWithIndices.toSeq) yield {
      val patchDescriptionAndIndex = (Seq(shortDescription) ++ countTextFor(index, snowflakesWithIndices.size)).mkString(" ")
      val prefixes = (additionalPrefix.toSeq :+ patchDescriptionAndIndex).map("["+_+"]")

      Email(
        addresses,
        subject = (prefixes :+ snowflake.title).mkString(" "),
        bodyText = s"${snowflake.body}\n--\n$footer"
      )
    }
  }
}

object PatchBomb {

  def numberFormatFor(size: Int) = {
    val nf = NumberFormat.getIntegerInstance
    nf.setMinimumIntegerDigits(size.toString.length)
    nf.setGroupingUsed(false)
    nf
  }

  def countTextFor(index: Int, size: Int): Option[String] = {
    if (size <= 1) None else {
      val formattedIndex = numberFormatFor(size).format(index)
      Some(f"$formattedIndex/$size")
    }
  }
}