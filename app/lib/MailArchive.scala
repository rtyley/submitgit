package lib

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField._
import java.time.{LocalDateTime, ZoneId}
import javax.mail.internet.InternetAddress

import cats.data.OptionT
import cats.implicits._
import com.madgag.okhttpscala._
import com.netaporter.uri.Uri
import com.netaporter.uri.config.UriConfig
import com.netaporter.uri.decoding.NoopDecoder
import com.netaporter.uri.dsl._
import com.squareup.okhttp
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request.Builder
import controllers.Application._
import lib.Email.Addresses
import lib.MailArchive.okClient
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

  def redirectFor(url: Uri)(implicit ec: ExecutionContext): OptionT[Future, Uri] =
    OptionT(okClient.execute(new okhttp.Request.Builder().url(url).build()) { resp =>
      resp.code match {
        case FOUND =>
          implicit val c = UriConfig(decoder = NoopDecoder)
          val redirectUri = Uri.parse(resp.header(LOCATION))
          Some(redirectUri.copy(
            host = redirectUri.host.orElse(url.host),
            scheme = redirectUri.scheme.orElse(url.scheme)
          ))
        case _ => None
      }
    })
}


trait MailArchive {
  val providerName: String

  val url: String

  /**
    * @return a url that's derived from the message-id - it may not be the 'canonical' url for the message, so hitting
    *         this url may yield a redirect
    */
  def linkFor(messageId: String): Uri

  /**
    * @return the canonical url for a message, if found- to find this out may require a network request to the mail archive.
    *         The url may contain a mail-archive-specific id, eg:
    *         https://marc.info/?l=git&m=143387261528519
    *         https://groups.google.com/forum/#!msg/submitgit-test/-cq4q1w7jyY/fLlC47tosH4J
    */
  def canonicalUrlFor(messageId: String)(implicit ec: ExecutionContext): OptionT[Future, Uri]

  def messageSummaryBasedOn(canonicalMessageUrl: Uri)(implicit ec: ExecutionContext): Future[MessageSummary]

  def lookupMessage(query: String)(implicit ec: ExecutionContext): Future[Seq[MessageSummary]] = {
    for {
      canonicalMessageUrl <- canonicalUrlFor(query)
      articleMessageSummary <- OptionT.liftF(messageSummaryBasedOn(canonicalMessageUrl))
    } yield articleMessageSummary.copy(id = query)
  }.value.map(_.toSeq)

}

object MailArchive {
  val okClient = new OkHttpClient()
}


trait OffersRawMessage extends MailArchive {
  def rawUrlFor(canonicalMessageUrl: Uri): Uri

  override def messageSummaryBasedOn(canonicalMessageUrl: Uri)(implicit ec: ExecutionContext): Future[MessageSummary] = for {
    raw <- okClient.execute(new Builder().url(rawUrlFor(canonicalMessageUrl)).build())(_.body.string)
  } yield MessageSummary.fromRawMessage(raw, canonicalMessageUrl)
}


case class PublicInbox(groupName: String) extends MailArchive with OffersRawMessage {
  val providerName = "public-inbox"

  val url = s"https://public-inbox.org/$groupName/"

  def linkFor(messageId: String) = s"https://public-inbox.org/$groupName/$messageId/"

  override def canonicalUrlFor(messageId: String)(implicit ec: ExecutionContext) = OptionT.some(linkFor(messageId))

  override def rawUrlFor(canonicalMessageUrl: Uri) = canonicalMessageUrl + "raw"

}

object PublicInbox {
  val Git = PublicInbox("git")
}

object Marc {
  val Git = Marc("git")

  def deobfuscate(emailOrMessageId: String) = emailOrMessageId
    .replace(" () ","@")
    .replace(" ! ", ".")
    .replace("&lt;","<")
    .replace("&gt;",">")

  val DateTimeFormat = new DateTimeFormatterBuilder().parseCaseInsensitive
    .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral(' ')
    .appendValue(HOUR_OF_DAY).appendLiteral(':').appendValue(MINUTE_OF_HOUR).optionalStart.appendLiteral(':').appendValue(SECOND_OF_MINUTE)
    .toFormatter

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

    val date = LocalDateTime.parse(headerMap("Date"), DateTimeFormat).atZone(ZoneId.of("UTC"))
    MessageSummary(messageId, headerMap("Subject"), date, Addresses(from), "")
  }
}

case class Marc(groupName: String) extends MailArchive {
  val providerName = "MARC"

  val url = s"http://marc.info/?l=$groupName"

  def linkFor(messageId: String) = s"http://marc.info/?i=$messageId"

  override def canonicalUrlFor(messageId: String)(implicit ec: ExecutionContext) =
    RedirectCapturer.redirectFor(linkFor(messageId))

  override def messageSummaryBasedOn(canonicalMessageUrl: Uri)(implicit ec: ExecutionContext): Future[MessageSummary] = for {
    raw <- okClient.execute(new okhttp.Request.Builder().url(canonicalMessageUrl).build())(_.body.string)
  } yield Marc.messageSummaryFor(raw).copy(groupLink = canonicalMessageUrl)
}

case class GoogleGroup(groupName: String) extends MailArchive with OffersRawMessage {

  val providerName = "Google Groups"

  val url = s"https://groups.google.com/forum/#!forum/$groupName"

  // submitgit-test@googlegroups.com
  val emailAddress = new InternetAddress(s"$groupName@googlegroups.com")

  // https://groups.google.com/d/msgid/submitgit-test/0000014d9a92ef17-abf8da02-8ed6-4f1e-b959-db3d4617e750-000000@eu-west-1.amazonses.com
  def linkFor(messageId: String) = s"https://groups.google.com/d/msgid/$groupName/$messageId"

  override def canonicalUrlFor(messageId: String)(implicit ec: ExecutionContext) = RedirectCapturer.redirectFor(linkFor(messageId))

  /* redirect: /forum/#!msg/submitgit-test/-cq4q1w7jyY/fLlC47tosH4J
   * raw resource: https://groups.google.com/forum/message/raw?msg=submitgit-test/-cq4q1w7jyY/fLlC47tosH4J
   */
  override def rawUrlFor(canonicalMessageUrl: Uri) =
    "https://groups.google.com/forum/message/raw?msg="+canonicalMessageUrl.fragment.get.stripPrefix("!msg/")

  val mailingList = MailingList(emailAddress, Seq(this))

}
