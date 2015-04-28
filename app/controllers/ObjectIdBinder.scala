package controllers

import org.eclipse.jgit.lib.ObjectId
import play.api.mvc.PathBindable.Parsing

object ObjectIdBinder {

  implicit object bindableTier extends Parsing[ObjectId](
    ObjectId.fromString, _.name, (key: String, e: Exception) => s"Cannot parse parameter $key as a commit id: ${e.getMessage}"
  )
}
