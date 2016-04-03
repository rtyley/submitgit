package lib.model

import java.time.ZonedDateTime

import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.model._
import lib.MarkdownParsing

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PRMessageIdFinder {

  def messageIdsByMostRecentUsageIn(pr: PullRequest)(implicit g: GitHub): Future[Seq[String]] = {
    for {
      comments <- pr.comments2.list().all()
    } yield {
      val messageIdAndCreationTime = for {
        comment <- comments
        messageId <- MarkdownParsing.parseHiddenLinksOutOf(comment.body)
      } yield {
        messageId -> comment.created_at
      }

      implicit def dateTimeOrdering: Ordering[ZonedDateTime] = Ordering.fromLessThan(_ isBefore _)

      val messageIdsWithMostRecentUsageTime: Map[String, ZonedDateTime] =
        messageIdAndCreationTime.groupBy(_._1).mapValues(_.map(_._2).max)

      messageIdsWithMostRecentUsageTime.toSeq.sortBy(_._2).map(_._1).reverse
    }
  }
}
