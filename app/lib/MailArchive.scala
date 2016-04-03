package lib

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField._
import java.time.{LocalDateTime, ZoneId}
import javax.mail.internet.InternetAddress

import com.madgag.okhttpscala._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.squareup.okhttp
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request.Builder
import controllers.Application._
import lib.Email.Addresses
import lib.model.MessageSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import scala.collection.convert.wrapAsScala._
import scala.concurrent.{ExecutionContext, Future}

object RedirectCapturer {
  val okClient = {
    val c = new OkHttpClient()
    c.setFollowRedirects(false)
    c
  }

  def redirectFor(url: String)(implicit ec: ExecutionContext): Future[Option[Uri]] =
    okClient.execute(new okhttp.Request.Builder().url(url).build()) { resp =>
      resp.code match {
        case FOUND => Some(Uri.parse(resp.header(LOCATION)))
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

trait MessageSummaryByRawResourceFromRedirect extends MailArchive {
  def rawUrlFor(articleUrl: Uri): Uri

  override def lookupMessage(query: String)(implicit ec: ExecutionContext): Future[Seq[MessageSummary]] = {
    for {
      articleUriOpt <- RedirectCapturer.redirectFor(linkFor(query))
      messageSummaryOpt <- messageSummaryBasedOn(articleUriOpt)
    } yield messageSummaryOpt.toSeq
  }

  def messageSummaryBasedOn(articleUriOpt: Option[Uri])(implicit ec: ExecutionContext): Future[Option[MessageSummary]] = {
    articleUriOpt match {
      case Some(articleUrl) =>
        val okClient = new OkHttpClient()
        val rawUrl = rawUrlFor(articleUrl)
        for {
          raw <- okClient.execute(new Builder().url(rawUrl).build())(_.body.string)
        } yield Some(MessageSummary.fromRawMessage(raw, articleUrl))
      case None => Future.successful(None)
    }
  }
}

case class Gmane(groupName: String) extends MailArchive with MessageSummaryByRawResourceFromRedirect {
  val providerName = "Gmane"

  val url = s"http://dir.gmane.org/gmane.$groupName"

  def linkFor(messageId: String) = s"http://mid.gmane.org/$messageId"

  override def rawUrlFor(articleUrl: Uri) = articleUrl / "raw"
}

object Marc {
  val Git = Marc("git")

  def deobfuscate(emailOrMessageId: String) = emailOrMessageId
    .replace(" () ","@")
    .replace(" ! ", ".")
    .replace("&lt;","<")
    .replace("&gt;",">")


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
    val from = new InternetAddress(deobfuscate(headerMap("From")))

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

  def articleUrl(messageId: String)(implicit ec: ExecutionContext): Future[Option[Uri]] =
    RedirectCapturer.redirectFor(linkFor(messageId))

  def messageSummaryFor(articleUrlOpt: Option[Uri])(implicit ec: ExecutionContext): Future[Option[MessageSummary]] = {
    articleUrlOpt match {
      case Some(articleUrl) =>
        val okClient = new OkHttpClient()
        for {
          raw <- okClient.execute(new okhttp.Request.Builder().url(articleUrl).build())(_.body.string)
        } yield Some(Marc.messageSummaryFor(raw).copy(groupLink = articleUrl))
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

case class GoogleGroup(groupName: String) extends MailArchive with MessageSummaryByRawResourceFromRedirect {

  val providerName = "Google Groups"

  val url = s"https://groups.google.com/forum/#!forum/$groupName"

  // https://groups.google.com/d/msgid/submitgit-test/0000014d9a92ef17-abf8da02-8ed6-4f1e-b959-db3d4617e750-000000@eu-west-1.amazonses.com
  def linkFor(messageId: String) = s"https://groups.google.com/d/msgid/$groupName/$messageId"

  // submitgit-test@googlegroups.com
  val emailAddress = new InternetAddress(s"$groupName@googlegroups.com")

  val mailingList = MailingList(emailAddress, Seq(this))

  /* redirect: /forum/#!msg/submitgit-test/-cq4q1w7jyY/fLlC47tosH4J
   * raw resource: https://groups.google.com/forum/message/raw?msg=submitgit-test/-cq4q1w7jyY/A-EH61BAZaUJ
   */
  override def rawUrlFor(articleUrl: Uri): Uri =
    "https://groups.google.com/forum/message/raw?msg="+articleUrl.fragment.get.stripPrefix("!msg/")
}
