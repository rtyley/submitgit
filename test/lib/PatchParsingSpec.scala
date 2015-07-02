package lib

import com.madgag.git._
import fastparse._
import fastparse.core.Result
import lib.model.PatchParsing
import org.scalatestplus.play.PlaySpec

import scalax.io.Resource

class PatchParsingSpec extends PlaySpec {

  val pull187PatchesText = Resource.fromClasspath("samples/bfg/pull87/github.patch").string

  "FastParse" should {
    "parse the from header" in {
      // http://stackoverflow.com/q/15790120/438886
      val Result.Success(value, successIndex) =
        PatchParsing.patchFromHeader.parse("From 21c2ef53cd0809149cdc158a6e5335ab0af77e7f Mon Sep 17 00:00:00 2001\n")
      value mustEqual "21c2ef53cd0809149cdc158a6e5335ab0af77e7f".asObjectId
    }

    "parse a patch header" in {
      val githubPatch = Resource.fromClasspath("samples/git/pull145/github.patch").string

      val Result.Success(value, s) = PatchParsing.patchHeaderRegion.parse(githubPatch)
      value mustEqual "af7333c176401601d67ea67cb961332ee4ef3574".asObjectId
    }

    "parse a patch" in {
      val Result.Success(patch, successIndex) = PatchParsing.patch.parse(pull187PatchesText)
      patch.commitId mustEqual "72428763eee19a2d83cc05a0ae4d55ab76930762".asObjectId
      patch.body must endWith("         run(\"--delete-folders .git --no-blob-protection\")\n")
    }

    "parse a sequence of patches" in {
      val Result.Success(patches, successIndex) = PatchParsing.patches.parse(pull187PatchesText)
      patches must have size 2
    }
  }
}
