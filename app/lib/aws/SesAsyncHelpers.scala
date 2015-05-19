package lib.aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsync
import com.amazonaws.services.simpleemail.model._
import lib.Email
import lib.aws.SES.VerificationStatus

import scala.collection.convert.wrapAll._
import scala.concurrent.{ExecutionContext, Future, Promise}

object SesAsyncHelpers {

  class AsyncHandlerToFuture[REQUEST <: AmazonWebServiceRequest, RESULT] extends AsyncHandler[REQUEST, RESULT] {
    val promise = Promise[RESULT]()

    def future = promise.future

    override def onError(exception: Exception): Unit = promise.failure(exception)

    override def onSuccess(request: REQUEST, result: RESULT): Unit = promise.success(result)
  }

  implicit class SesAsync(ses: AmazonSimpleEmailServiceAsync) {
    import SES._

    private def invoke[REQUEST <: AmazonWebServiceRequest, RESULT]
    (
      method: (REQUEST, AsyncHandler[REQUEST, RESULT]) => java.util.concurrent.Future[RESULT],
      req: REQUEST
    ): Future[RESULT] = {
      val handler = new AsyncHandlerToFuture[REQUEST, RESULT]
      method(req, handler)
      handler.future
    }

    // ignore the red in IntelliJ here, the scala compiler understands this :)
    def verifyEmailIdentityFuture(req: VerifyEmailIdentityRequest): Future[VerifyEmailIdentityResult] =
      invoke(ses.verifyEmailIdentityAsync, req)

    def sendVerificationEmailTo(email: String)(implicit ec: ExecutionContext) =
      verifyEmailIdentityFuture(new VerifyEmailIdentityRequest().withEmailAddress(email))

    def getIdentityVerificationAttributesFuture(req: GetIdentityVerificationAttributesRequest): Future[GetIdentityVerificationAttributesResult] =
      invoke(ses.getIdentityVerificationAttributesAsync, req)

    def getIdentityVerificationStatusFor(email: String)(implicit ec: ExecutionContext): Future[Option[VerificationStatus]] = {
      val idReq = new GetIdentityVerificationAttributesRequest().withIdentities(email)
      for (res <- ses.getIdentityVerificationAttributesFuture(idReq)) yield
        res.getVerificationAttributes.toMap.get(email).flatMap(_.status)
    }

    def sendRawEmailFuture(req: SendRawEmailRequest): Future[SendRawEmailResult] =
      invoke(ses.sendRawEmailAsync, req)

    def send(email: Email)(implicit ec: ExecutionContext): Future[String] = {
      val rawEmailRequest = new SendRawEmailRequest(new RawMessage(email.toMimeMessage))
      rawEmailRequest.setDestinations(email.addresses.to)
      rawEmailRequest.setSource(email.addresses.from)
      sendRawEmailFuture(rawEmailRequest).map(resp => s"${resp.getMessageId}@eu-west-1.amazonses.com")
    }
  }

}