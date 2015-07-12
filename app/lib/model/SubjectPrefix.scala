package lib.model

import fastparse.all._

case class SubjectPrefix(descriptor: String, version: Option[Int] = None) {
  lazy val suggestsNext = copy(version = Some(version.fold(2)(_ + 1)))

  override lazy val toString = (descriptor +: version.map("v"+_).toSeq).mkString(" ")
}

object SubjectPrefixParsing {
  val number: P[Int] = P( CharIn('0'to'9').rep(1).!.map(_.toInt) )

  val version: P[Int] = P( CharIn("vV") ~ number )

  val patchIndexAndBombSize = P(number ~"/" ~ number)

  val word: P[String] = P(CharsWhile(!Set('\n',' ', ']').contains(_)).!)

  val nonDescriptorTerms = version | patchIndexAndBombSize

  val descriptorWord = word.filter(!nonDescriptorTerms.parse(_).isInstanceOf[Result.Success[_]])

  val descriptor: P[String] = P( descriptorWord.rep(1, sep = " ").! )

  val patchPrefixContent: P[SubjectPrefix] =
    P( descriptor ~ (" " ~ version).? ~ (" " ~ patchIndexAndBombSize).map(_ => ()).? ).map(SubjectPrefix.tupled)

  val subjectLine: P[SubjectPrefix] =
    P( "[" ~ patchPrefixContent ~ "]")

  def parse(subjectLineText: String): Option[SubjectPrefix] = {
    subjectLine.parse(subjectLineText) match {
      case Result.Success(subjectPrefix, _) => Some(subjectPrefix)
      case _ => None
    }
  }
}
