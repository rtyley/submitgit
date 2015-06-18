In http://git-scm.com/docs/git-format-patch :

--subject-prefix=<Subject-Prefix>
Instead of the standard [PATCH] prefix in the subject line, instead use [<Subject-Prefix>]. This allows for useful naming of a patch series, and can be combined with the --numbered option.

[PATCH v2 ...]
[RFC/PATCH ...]
[WIP/PATCH v4 6/8]
[PATCH]
[RFC/WIP PATCH 06/11]
[PATCH/WIP 4/8]

Matthieu Moy said: I found no way to specify a version like [PATCH v2 ...]. Similarly, there could be a way to say [RFC/PATCH ...].



Potential features TODO:

* When closing a PR 'cos it's been posted, link to http://mid.gmane.org/1234567890.1234567890@example.com so we can see the discussion.
* When RE-posting a PR, pull out the OLD message id so we can say it's in-reply to the
previous message.

Reasons why you can't send to the mailing-list, only preview:

* You did not create the PR
* The PR is not Open?
* You have not registered your email with Amazon SES
* You have not sent a preview email?
* We're out of SES quota
* Signed off missing - need a full name?
* The body of the commit messages is too short??? There are some commits in Git with just a subject line and "Signed-off-by"s
* The subject line is too long (can't do for whole body, some things can go long!)

Commit warnings:

* Message lines too long
* Commit content lines too long

Sending emails with the correct From header is hard

1. Don't use correct From - just send as submitgit@gmail.com - update git-mailinfo in order to parse a new Authored-By header. Ensure that the author
is cc'd on the message, so they at least get the reply when people reply-all
2. Get the user to forward the message? What if their mail agent corrupts it...?
3. Use a service like Amazon SES, which has an API for adding verified
email addresses : http://docs.aws.amazon.com/ses/latest/APIReference/API_VerifyEmailIdentity.html

Need to go *raw* with SES in order to send 'In-Reply-To' & 'References'
headers:
http://docs.aws.amazon.com/ses/latest/DeveloperGuide/send-email-raw.html

The From address should be the authenticated GitHub user address, not
the email address on the commit - the GitHub user is the person
submitting this patch.

By default, the subject of a single patch is "[PATCH] " followed by the concatenation of lines from the commit message up to the first blank line (see the DISCUSSION section of git-commit(1)).

When multiple patches are output, the subject prefix will instead be "[PATCH n/m] ". To force 1/1 to be added for a single patch, use -n. To omit patch numbers from the subject, use -N.

If given --thread, git-format-patch will generate In-Reply-To and References headers to make the second and subsequent patch mails appear as replies to the first mail; this also generates a Message-Id header to reference.

http://git-scm.com/docs/git-format-patch
https://github.com/torvalds/linux/pull/17#issuecomment-5654674
https://github.com/torvalds/linux/pull/17#issuecomment-5663780


From: 51ac58c9e7bce595f6652513dd6ef2e9f92dd2b1

Would make sense to link off to the mailing arcive:

http://dir.gmane.org/gmane.comp.version-control.git
http://marc.info/?l=git
