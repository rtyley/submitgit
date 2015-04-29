package controllers

import lib.MailType
import org.eclipse.jgit.lib.ObjectId
import play.api.mvc.PathBindable.Parsing

object Binders {

  implicit object bindableObjectId extends Parsing[ObjectId](
    ObjectId.fromString, _.name, (key: String, e: Exception) => s"Cannot parse parameter $key as a commit id: ${e.getMessage}"
  )

  implicit object bindableMailType extends Parsing[MailType](
    MailType.bySlug, _.slug, (key: String, e: Exception) => s"Cannot parse parameter $key as a commit id: ${e.getMessage}"
  )
}
