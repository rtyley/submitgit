package lib

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Properties
import javax.mail.Message.RecipientType
import javax.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}
import javax.mail.{Address, Message}

import lib.Email.Addresses

object Email {
  case class Addresses(
    from: String,
    to: Seq[String] = Seq.empty,
    cc: Seq[String] = Seq.empty,
    bcc: Seq[String] = Seq.empty,
    replyTo: Option[String] = None
  )
}

case class Email(
  addresses: Addresses,
  subject: String,
  bodyText: String,
  charset: Option[String] = None,
  headers: Seq[(String, String)] = Seq.empty
) {

  lazy val toMimeMessage: ByteBuffer = {
    val s = javax.mail.Session.getInstance(new Properties(), null)
    val msg = new MimeMessage(s)

    // Sender and recipient
    msg.setFrom(new InternetAddress(addresses.from))
    def setRecipients(typ: Message.RecipientType, recipients: Seq[String]) {
      if (recipients.nonEmpty) msg.setRecipients(typ, recipients.map(new InternetAddress(_)).toArray[Address])
    }

    setRecipients(RecipientType.TO, addresses.to)
    setRecipients(RecipientType.CC, addresses.cc)
    setRecipients(RecipientType.BCC, addresses.bcc)

    msg.setSubject(subject)
    headers.foreach(t => msg.addHeader(t._1, t._2))
    addresses.replyTo.foreach(r => msg.setReplyTo(Array(new InternetAddress(r))))

    // Add a MIME part to the message
    val mp = new MimeMultipart()

    val part = new MimeBodyPart()
    part.setContent(bodyText, "text/plain")
    mp.addBodyPart(part)

    msg.setContent(mp)

    val b = new ByteArrayOutputStream()

    msg.writeTo(b)

    msg.writeTo(System.out);

    ByteBuffer.wrap(b.toByteArray)

  }
}
