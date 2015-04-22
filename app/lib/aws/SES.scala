package lib.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider}
import com.amazonaws.regions.Regions._
import com.amazonaws.services.simpleemail.{AmazonSimpleEmailServiceAsync, AmazonSimpleEmailServiceAsyncClient}

object SES {

  val AwsCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("submitgit"),
    new EnvironmentVariableCredentialsProvider()
  )

  val ses: AmazonSimpleEmailServiceAsync = new AmazonSimpleEmailServiceAsyncClient(AwsCredentials).withRegion(EU_WEST_1)

}
