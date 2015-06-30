package lib

import play.api.libs.json.Json

case class PRMailSettings(subjectPrefix: String, inReplyTo: Option[String] = None)

object PRMailSettings {
  implicit val formats = Json.format[PRMailSettings]
}

