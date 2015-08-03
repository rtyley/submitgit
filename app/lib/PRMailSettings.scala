package lib

import lib.model.SubjectPrefixParsing
import play.api.libs.json.Json

case class PRMailSettings(subjectPrefix: String, inReplyTo: Option[String] = None, coverLetter: Option[String] = None) {
  def afterBeingUsedToSend(messageId: String) = copy(
    subjectPrefix =
      SubjectPrefixParsing.patchPrefixContent.parse(subjectPrefix).get.value.suggestsNext.toString,
    inReplyTo = Some(messageId)
  )
}

object PRMailSettings {
  implicit val formats = Json.format[PRMailSettings]
}

