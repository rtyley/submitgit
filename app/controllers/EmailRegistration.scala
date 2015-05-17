/*
 * Copyright 2014 The Guardian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import controllers.Actions._
import lib.aws.SES._
import lib.aws.SesAsyncHelpers._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

object EmailRegistration extends Controller {

  val GitHubUserWithVerifiedEmail: ActionBuilder[GHRequest] =
    githubAction() andThen EnsureGitHubVerifiedEmail

  def isRegisteredEmail(email: String) = GitHubUserWithVerifiedEmail.async {
    for (status <- ses.getIdentityVerificationStatusFor(email)) yield Ok(status.map(_.string).getOrElse("Unknown"))
  }

  def registerEmail = GitHubUserWithVerifiedEmail.async { req =>
    for (res <- ses.sendVerificationEmailTo(req.userEmail.getEmail)) yield Ok("Registration email sent")
  }

}
