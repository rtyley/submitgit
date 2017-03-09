package lib.model

import java.time.{ZoneId, ZonedDateTime}
import javax.mail.internet.{InternetAddress, MailDateFormat}

import fastparse.core.Parsed
import lib.Email.Addresses
import play.api.libs.json._
import play.api.mvc.Headers

object MessageSummary {

  implicit val writesInternetAddress = new Writes[InternetAddress] {
    override def writes(ia: InternetAddress) = JsString(ia.toUnicodeString)
  }
  implicit val writesAddresses = Json.writes[Addresses]
  implicit val writesMessageSummary = new Writes[MessageSummary] {
    override def writes(o: MessageSummary): JsValue = {
      val suggestion = for {
        prefix <- o.suggestedPrefixForNextPatchBombOpt
      } yield "suggestsPrefix" -> JsString(prefix)

      Json.writes[MessageSummary].writes(o).asInstanceOf[JsObject] ++ JsObject(suggestion.toSeq)
    }
  }

  def fromRawMessage(rawMessage: String, articleUrl: String): MessageSummary = {
    val Parsed.Success(headerTuples, _) = PatchParsing.messageHeaders.parse(rawMessage)
    val headers = Headers(headerTuples: _*)
    val messageId = headers("Message-Id").stripPrefix("<").stripSuffix(">")
    val from = new InternetAddress(headers("From"))
    val date = new MailDateFormat().parse(headers("Date")).toInstant.atZone(ZoneId.of("UTC"))
    MessageSummary(messageId, headers("Subject"), date, Addresses(from), articleUrl)
  }
}


case class MessageSummary(
  id: String,
  subject: String,
  date: ZonedDateTime,
  addresses: Addresses,
  groupLink: String
) {
  lazy val suggestedPrefixForNextPatchBombOpt: Option[String] =
    SubjectPrefixParsing.parse(subject).map(_.suggestsNext.toString)
}
