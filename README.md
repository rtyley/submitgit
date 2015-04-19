submitGit
=========

The Git project has a [patch submission process](https://github.com/git/git/blob/1eb0545c/Documentation/SubmittingPatches#L120-L464)
that's _unfamilar to the majority of Git users_. Any potential
contributor - wanting to fix a small bug, or tweak documentation
to make it more friendly - will learn that mailing a patch is
[not as simple as you might expect](http://git-scm.com/docs/git-format-patch#_mua_specific_hints),
especially if they're using a webmail service, like Gmail. The
recommende approach is to forgo regular email clients, and
use [`git-format-patch`](http://git-scm.com/docs/git-format-patch)
and [`git-send-email`](http://git-scm.com/docs/git-send-email`)
on the command-line to ensure nothing gets lost or auto-bounced
(for containing HTML, for instance, which Gmail does by default).

This might change in the future, but for the moment, the core Git
contributors have decided to keep patch review where it is, on
the mailing list, and won't be reviewing, for instance, GitHub or
Bitbucket pull requests.

_submitGit_ is a small step in trying to make patch submission
more easy. If you create a pull request on https://github.com/git/git/,
_submitGit_ can close it for you by correctly formatting it as
a series of patches, and sending it to the mailing list. The
discussion stays where it is- on the list -but at least that
initial step is a little easier.
