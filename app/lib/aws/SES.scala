package lib.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider}
import com.amazonaws.regions.Regions._
import com.amazonaws.services.simpleemail.model.IdentityVerificationAttributes
import com.amazonaws.services.simpleemail.{AmazonSimpleEmailServiceAsync, AmazonSimpleEmailServiceAsyncClient}

object SES {

  sealed abstract class VerificationStatus(val string: String)

  object VerificationStatus {
    object Pending extends VerificationStatus("Pending")
    object Success extends VerificationStatus("Success")
    object Failed extends VerificationStatus("Failed")
    object TemporaryFailure extends VerificationStatus("TemporaryFailure")
    object NotStarted extends VerificationStatus("NotStarted")

    val all = Set(Pending, Success, Failed, TemporaryFailure, NotStarted)

    def from(string: String): Option[VerificationStatus] = all.find(_.string == string)
  }

  implicit class RichIdentityVerificationAttributes(attrs: IdentityVerificationAttributes) {
    lazy val status: Option[VerificationStatus] = VerificationStatus.from(attrs.getVerificationStatus)
  }

  val AwsCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("submitgit"),
    new EnvironmentVariableCredentialsProvider()
  )

  val ses: AmazonSimpleEmailServiceAsync = new AmazonSimpleEmailServiceAsyncClient(AwsCredentials).withRegion(EU_WEST_1)



}
