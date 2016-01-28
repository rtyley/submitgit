package lib

import org.scalatestplus.play.PlaySpec

class MailTypeSpec extends PlaySpec {
  "Email address construction" should {
    MailType.internetAddressFor("c@m.com", "Cédric Malard").toString mustEqual
      "=?UTF-8?Q?C=C3=A9dric_Malard?= <c@m.com>"
  }
}