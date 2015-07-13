package controllers

import javax.inject.Inject

import com.madgag.github.RepoId
import lib._
import lib.model.MessageSummary
import play.api.cache.Cached
import play.api.libs.json.Json._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Api @Inject() (cached: Cached) extends Controller {

  def messageLookup(repoId: RepoId, query: String) = cached(s"$repoId $query") {
    Action.async {
      for {
        messagesOpt <- Project.byRepoId(repoId).mailingList.lookupMessage(query)
      } yield Ok(toJson(messagesOpt: Seq[MessageSummary]))
    }
  }

}



