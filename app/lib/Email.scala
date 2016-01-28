package lib

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Properties
import javax.mail.Message.RecipientType
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Address, Message}

import lib.Email.Addresses

object Email {
  case class Addresses(
    from: InternetAddress,
    to: Seq[InternetAddress] = Seq.empty,
    cc: Seq[InternetAddress] = Seq.empty,
    bcc: Seq[InternetAddress] = Seq.empty,
    replyTo: Option[InternetAddress] = None
  )
}

case class Email(
  addresses: Addresses,
  subject: String,
  bodyText: String,
  charset: Option[String] = None,
  headers: Seq[(String, String)] = Seq.empty
) {

  def inReplyTo(unenclosedMessageId: String): Email = {
    val encloseMessageId = s"<$unenclosedMessageId>"
    copy(headers = headers ++ Seq("References" -> encloseMessageId, "In-Reply-To" -> encloseMessageId))
  }

  lazy val toMimeMessage: ByteBuffer = {
    val s = javax.mail.Session.getInstance(new Properties(), null)
    val msg = new MimeMessage(s)

    // Sender and recipient
    msg.setFrom(addresses.from)
    def setRecipients(typ: Message.RecipientType, recipients: Seq[InternetAddress]) {
      if (recipients.nonEmpty) msg.setRecipients(typ, recipients.toArray[Address])
    }

    setRecipients(RecipientType.TO, addresses.to)
    setRecipients(RecipientType.CC, addresses.cc)
    setRecipients(RecipientType.BCC, addresses.bcc)

    msg.setSubject(subject)
    headers.foreach(t => msg.addHeader(t._1, t._2))
    addresses.replyTo.foreach(r => msg.setReplyTo(Array(r)))

    msg.setText(bodyText)

    val b = new ByteArrayOutputStream()

    msg.writeTo(b)

    ByteBuffer.wrap(b.toByteArray)

  }
}
