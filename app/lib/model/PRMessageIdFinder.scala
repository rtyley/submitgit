package lib.model

import java.time.Instant

import lib.MarkdownParsing
import org.kohsuke.github.{GHIssueComment, GHPullRequest}

import scala.collection.convert.wrapAll._

object PRMessageIdFinder {

  def messageIdsByMostRecentUsageIn(pr: GHPullRequest): Seq[String] = {
    val messageIdAndCreationTime = for {
      comment: GHIssueComment <- pr.listComments()
      messageId <- MarkdownParsing.parseHiddenLinksOutOf(comment.getBody)
    } yield messageId -> comment.getCreatedAt.toInstant

    val messageIdsWithMostRecentUsageTime: Map[String, Instant] = messageIdAndCreationTime.groupBy(_._1).mapValues(_.map(_._2).max)

    messageIdsWithMostRecentUsageTime.toSeq.sortBy(_._2).map(_._1).reverse
  }
}
