package lib

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField._
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import javax.mail.internet.MailDateFormat

import com.madgag.okhttpscala._
import com.squareup.okhttp
import com.squareup.okhttp.OkHttpClient
import controllers.Application._
import fastparse.core.Result
import lib.Email.Addresses
import lib.model.PatchParsing
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import play.api.libs.json.Json

import scala.collection.convert.wrapAsScala._
import scala.concurrent.{ExecutionContext, Future}

case class MessageSummary(id: String, subject: String, date: ZonedDateTime, addresses: Addresses, groupLink: String)

object MessageSummary {
  implicit val formatsAddresses = Json.format[Addresses]
  implicit val formatsMessageSummary = Json.format[MessageSummary]

  def fromRawMessage(rawMessage: String, articleUrl: String): MessageSummary = {
    val Result.Success(headers, _) = PatchParsing.headers.parse(rawMessage)
    val headerMap = headers.toMap

    val messageId = headerMap("Message-ID").stripPrefix("<").stripSuffix(">")
    val from = headerMap("From")
    val date = new MailDateFormat().parse(headerMap("Date")).toInstant.atZone(ZoneId.of("UTC"))
    MessageSummary(messageId, headerMap("Subject"), date, Addresses(from), articleUrl)
  }
}

object RedirectCapturer {
  val okClient = {
    val c = new OkHttpClient()
    c.setFollowRedirects(false)
    c
  }

  def redirectFor(url: String)(implicit ec: ExecutionContext): Future[Option[String]] = for {
    resp <- okClient.execute(new okhttp.Request.Builder().url(url).build())
  } yield {
    resp.code match {
      case FOUND => Some(resp.header(LOCATION))
      case _ => None
    }
  }
}


trait MailArchive {
  val providerName: String

  val url: String

  def linkFor(messageId: String): String

  def lookupMessage(query: String)(implicit ec: ExecutionContext): Future[Seq[MessageSummary]] = Future.successful(Seq.empty)
}

case class Gmane(groupName: String) extends MailArchive {
  val providerName = "Gmane"

  val url = s"http://dir.gmane.org/gmane.$groupName"

  def linkFor(messageId: String) = s"http://mid.gmane.org/$messageId"

  override def lookupMessage(query: String)(implicit ec: ExecutionContext) = {
    for {
      gmaneArticleUrlOpt <- gmaneArticleUrlFor(query)
      gmaneRawArticleOpt <- gmaneRawArticleFor(gmaneArticleUrlOpt)
    } yield gmaneRawArticleOpt.toSeq
  }

  def gmaneRawArticleFor(articleUrlOpt: Option[String])(implicit ec: ExecutionContext): Future[Option[MessageSummary]] = {
    articleUrlOpt match {
      case Some(articleUrl) =>
        val okClient = new OkHttpClient()
        for {
          resp <- okClient.execute(new okhttp.Request.Builder().url(articleUrl+"/raw").build())
        } yield Some(MessageSummary.fromRawMessage(resp.body.string, articleUrl))
      case None => Future.successful(None)
    }
  }

  def gmaneArticleUrlFor(messageId: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    RedirectCapturer.redirectFor(linkFor(messageId))

}

object Marc {
  val Git = Marc("git")

  def deobfuscate(emailOrMessageId: String) = emailOrMessageId.replace(" () ","@").replace(" ! ", ".")

  /*
   * Hacks EVERYWHERE - MARC unfortunately doesn't expose this data in a nice format for us
   */
  def messageSummaryFor(articleHtml: String): MessageSummary = {
    val elements = Jsoup.parse(articleHtml).select("""pre b font[size="+1"]""")
    val nodes = elements.get(0).childNodes().toList
    val headerMap = (for { header :: value :: Nil <- nodes.grouped(2) } yield {
      header.outerHtml.trim.stripSuffix(":") -> value.asInstanceOf[Element].html()
    }).toMap
    val messageId = deobfuscate(headerMap("Message-ID"))
    val from = deobfuscate(headerMap("From"))

    val ISO_LOCAL_TIME = new DateTimeFormatterBuilder().appendValue(HOUR_OF_DAY).appendLiteral(':').appendValue(MINUTE_OF_HOUR).optionalStart.appendLiteral(':').appendValue(SECOND_OF_MINUTE).toFormatter
    val ISO_LOCAL_DATE_TIME = new DateTimeFormatterBuilder().parseCaseInsensitive.append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral('T').append(ISO_LOCAL_TIME).toFormatter

    val date = LocalDateTime.parse(headerMap("Date").replace(' ', 'T'), ISO_LOCAL_DATE_TIME).atZone(ZoneId.of("UTC"))
    MessageSummary(messageId, headerMap("Subject"), date, Addresses(from), "")
  }
}

case class Marc(groupName: String) extends MailArchive {
  val providerName = "MARC"

  val url = s"http://marc.info/?l=$groupName"

  def linkFor(messageId: String) = s"http://marc.info/?i=$messageId"

  override def lookupMessage(query: String)(implicit ec: ExecutionContext) = {
    for {
      articleUrlOpt <- articleUrl(query)
      articleMessageSummaryOpt <- messageSummaryFor(articleUrlOpt)
    } yield articleMessageSummaryOpt.map(_.copy(id = query)).toSeq
  }

  def articleUrl(messageId: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    RedirectCapturer.redirectFor(linkFor(messageId))

  def messageSummaryFor(articleUrlOpt: Option[String])(implicit ec: ExecutionContext): Future[Option[MessageSummary]] = {
    articleUrlOpt match {
      case Some(articleUrl) =>
        val okClient = new OkHttpClient()
        for {
          resp <- okClient.execute(new okhttp.Request.Builder().url(articleUrl).build())
        } yield Some(Marc.messageSummaryFor(resp.body.string).copy(groupLink = articleUrl))
      case None => Future.successful(None)
    }
  }
}

object Gmane {
  val Git = Gmane("comp.version-control.git")
}

case class MailArchiveDotCom(emailAddress: String) extends MailArchive {
  val providerName = "mail-archive.com"

  val url = s"https://www.mail-archive.com/$emailAddress"

  // mail-archive.com actually doesn't not seem to work on message-id search for recent Git messages
  def linkFor(messageId: String) = s"http://mid.mail-archive.com/$messageId"
}

case class GoogleGroup(groupName: String) extends MailArchive {

  val providerName = "Google Groups"

  val url = s"https://groups.google.com/forum/#!forum/$groupName"

  // https://groups.google.com/d/msgid/submitgit-test/0000014d9a92ef17-abf8da02-8ed6-4f1e-b959-db3d4617e750-000000@eu-west-1.amazonses.com
  def linkFor(messageId: String) = s"https://groups.google.com/d/msgid/$groupName/$messageId"

  // submitgit-test@googlegroups.com
  val emailAddress = s"$groupName@googlegroups.com"

  val mailingList = MailingList(emailAddress, Seq(this))
}
