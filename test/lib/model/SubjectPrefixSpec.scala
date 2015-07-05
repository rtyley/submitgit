package lib.model

import lib.model.SubjectPrefixParsing.parse
import org.scalatestplus.play.PlaySpec

class SubjectPrefixSpec extends PlaySpec {
  "SubjectPrefixParsing" should {
    "be happy if there is no patch version" in {
      parse("[PATCH] send-email") mustBe Some(SubjectPrefix("PATCH"))
    }
    "extract version" in {
      parse("[PATCH v3] send-email") mustBe Some(SubjectPrefix("PATCH", Some(3)))
    }
    "extract version when patch index is present" in {
      parse("[PATCH v3 5/7] send-email") mustBe Some(SubjectPrefix("PATCH", Some(3)))
    }
    "work for prefix text that isn't just 'PATCH'" in {
      parse("[RFC/PATCH] send-email") mustBe Some(SubjectPrefix("RFC/PATCH"))
    }
  }
}
