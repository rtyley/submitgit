package lib.github

import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc.RequestHeader

object MinimalGHPerson {

  val SessionKey = "user"

  implicit val formatsPerson = Json.format[MinimalGHPerson]

  def fromRequest(implicit req: RequestHeader) = for {
    userJson <- req.session.get(SessionKey)
    user <- Json.parse(userJson).validate[MinimalGHPerson].asOpt
  } yield user

}

case class MinimalGHPerson(login: String, avatarUrl: String) {

  lazy val sessionTuple: (String, String) = MinimalGHPerson.SessionKey -> toJson(this).toString()
}