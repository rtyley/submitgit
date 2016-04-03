package lib.model

import java.text.NumberFormat

import lib.Email
import lib.Email.Addresses
import lib.model.PatchBomb.{countTextFor, fromBodyPrefixOpt}

case class PatchBomb(
  patchCommits: Seq[PatchCommit],
  addresses: Addresses,
  shortDescription: String = "PATCH",
  additionalPrefix: Option[String] = None,
  footer: String
) {

  lazy val emails: Seq[Email] = {
    for ((patchCommit, index) <- patchCommits.zipWithIndex) yield {
      val patchDescriptionAndIndex = (Seq(shortDescription) ++ countTextFor(index + 1, patchCommits.size)).mkString(" ")
      val prefixes = (additionalPrefix.toSeq :+ patchDescriptionAndIndex).map("["+_+"]")

      Email(
        addresses,
        subject = (prefixes :+ patchCommit.commit.subject).mkString(" "),
        bodyText =
          (fromBodyPrefixOpt(patchCommit, addresses).toSeq :+ s"${patchCommit.patch.body}\n--\n$footer").mkString("\n")
      )
    }
  }
}

object PatchBomb {

  def fromBodyPrefixOpt(patchCommit: PatchCommit, addresses: Addresses): Option[String] = {
    val author = patchCommit.commit.author

    val shouldAddFromBodyPrefix =
      author.email == addresses.from.getAddress || author.email.contains("noreply")

    if (shouldAddFromBodyPrefix) None else Some(s"From: ${author.addressString}\n")
  }

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