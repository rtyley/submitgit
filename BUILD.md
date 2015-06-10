For possible contributors looking to build & run _submitGit_ locally...

# Requirements

### System Requirements

_submitGit_ is written in Scala, a modern functional language that runs on the JVM - so it
can run anywhere Java can.

* Install Java 8 or above
* Install [sbt](http://www.scala-sbt.org/release/tutorial/Setup.html), the Scala build tool

### Service Credentials

##### Amazon Simple Email Service (SES)

[Amazon SES](http://docs.aws.amazon.com/ses/latest/DeveloperGuide/Welcome.html) is used to
send email for preview, and to the mailing list. Part of the reason for choosing it
is that it's an SMTP service in good standing (not associated with spam) which allows
you to send emails with arbitrary 'From:' addresses - so long as those emails are validated
with Amazon SES

You'll need an Amazon AWS account. Create an IAM User, and attach a policy to it allowing it to
perform these actions:

* `ses:SendRawEmail`
* `ses:VerifyEmailIdentity`
* `ses:GetIdentityVerificationAttributes`

The AWS credentials associated with this user should be stored as an AWS CLI profile
named `submitgit`.

##### GitHub

Register a new OAuth application with GitHub on https://github.com/settings/applications/new :

* Homepage URL : http://localhost:9000/
* Authorization callback URL : http://localhost:9000/oauth/callback

Also generate a GitHub 'personal access token' with the `public_repo` scope at
https://github.com/settings/tokens/new

Tell _submitGit_ about these credentials by setting the following environment variables:

* `GITHUB_APP_CLIENT_ID`
* `GITHUB_APP_CLIENT_SECRET`
* `SUBMITGIT_GITHUB_ACCESS_TOKEN`


# Finally, build and run...

* `git clone https://github.com/rtyley/submitgit.git`
* `cd submitgit`
* `sbt`<- start the sbt console
* `run` <- run the server
* Visit http://localhost:9000/

If you're going to make changes to the Scala code, you may want to use IntelliJ and it's Scala
plugin to help with the Scala syntax...!

I personally found Coursera's [online Scale course](https://www.coursera.org/course/progfun)
very helpful in learning Scala, YMMV.
