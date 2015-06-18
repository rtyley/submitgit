package lib

import play.api.libs.json.Json

case class PRMailSettings(subjectPrefix: String)

object PRMailSettings {
  implicit val formats = Json.format[PRMailSettings]
}

