Sending emails with the correct From header is hard

1. Don't use correct From - just send as submitgit@gmail.com - update git-mailinfo in order to parse a new Authored-By header. Ensure that the author
is cc'd on the message, so they at least get the reply when people reply-all
2. Get the user to forward the message? What if their mail agent corrupts it...?
3. Use a service like Amazon SES, which has an API for adding verified
email addresses : http://docs.aws.amazon.com/ses/latest/APIReference/API_VerifyEmailIdentity.html

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
