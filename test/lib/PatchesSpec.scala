package lib

import lib.Patches.PatchStartRegex
import org.eclipse.jgit.lib.ObjectId
import org.specs2.mutable.Specification

import scalax.io.Resource

class PatchesSpec extends Specification {
  "Patches" should {

    "use a sensible regex" in {
      PatchStartRegex.findFirstMatchIn("From 21c2ef53cd0809149cdc158a6e5335ab0af77e7f Mon Sep 17 00:00:00 2001\n").map(_.group(1)) should beSome("21c2ef53cd0809149cdc158a6e5335ab0af77e7f")
    }

    "be parsed" in {
      val commits = Seq(
        ObjectId.fromString("72428763eee19a2d83cc05a0ae4d55ab76930762"),
        ObjectId.fromString("21c2ef53cd0809149cdc158a6e5335ab0af77e7f")
      )
      val githubPatch = Resource.fromClasspath("samples/bfg/pull87/github.patch").string
      val commitsAndPatches = Patches.commitsAndPatches(commits, githubPatch)
      commitsAndPatches should have size 2
    }
  }
}
