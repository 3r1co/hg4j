# 1.2 Milestone 3 #
> _Due in Sept_
  * Annotate/Blame: the way merged lines are reported has been changed
  * Introduced `HgMergeCommand` and `HgCommitCommand` updated to commit merged revisions


> Pending updates to ProjectPlan
  1. might need `HgResolveCommand` to deal with unresolved changed to include in 1.2 plan
  1. access to shared repositories is a requested feature, is it possible to include it into 1.2 as well?

# 1.2 Milestone 2 #
> August 8, 2013
  * Annotate reworked. No 'native hg' compatibility, as it's downgrade from present functionality, which is much smarter with merged revisions.
  * Initial support for SSH remote repositories, using Trilead SSH2 library
  * `HgDataFile#isCopy(int)`, API to detect copy/rename in file history other than in the very first revision. Status, log/history, annotate/diff and the rest of the library now respect rename information in any revision


# 1.2 Milestone 1 #
> July 10, 2013
  * `HgPushCommand`: send local changes to remote repository
  * `HgPullCommand`: get remote changes to local repository
  * Performance: faster rebuild of `HgBranches` for huge repository; use branch cache as of Mercurial 2.5 (cache/branchheads-base).
  * Patch merge functionality (not to reconstruct intermediate revision content in its entirety) is on by default

# 1.1.0 #
> June 11, 2013
  * HgCommitCommand wiki page: commit implementation and its limitations (failures-rollback), use of repository locks
  * InstanceRefreshPolicy wiki page: limitations of refresh mechanism based on timestamps
  * HgDiffCommand wiki page to introduce new annotate/blame functionality
  * CheckedExceptionsHowto wiki page: how to switch from runtime to checked exceptions
  * Functionality to create new repositories is now exposed in public API as `HgInitCommand`
  * Few JUnit tests

# 1.1 Release Candidate 2 #
> May 30, 2013
  * Diff/blame methods as hi-level command, `HgDiffCommand`
  * Explicit exception declarations even for runtime exceptions to facilitate easy switch to checked exceptions in a code fork
  * Collect more info about possible AIOOBE cause ([Issue 45](https://code.google.com/p/hg4j/issues/detail?id=45))

# 1.1 Release Candidate 1 #
> May 21, 2013
  * Commit failures: rollback modified files to their initial state
  * Comply with Java 1.5 target
  * Refactorings, tests.
  * Few @Experimental graduates (ExperimentalCode)

# 1.1 Milestone 4 #
> May 10, 2013
  * RC0 == API freeze (almost, minor refactoring with ExperimentalCode is possible)
  * `HgTags`, `HgBranches`, `HgBookmarks`, `HgIgnore` are refreshed when repository is changed (either externally or via Hg4J). Note, for the sake of performance, these instances are treated as snapshots, and are refreshed only from corresponding accessor (i.e. `HgRepository#getTags()`, `#getBranches()`, etc.
  * Changelog, manifest and file nodes are refreshed when underlying files are changed
  * "Lightweight" index revlog reading to speed-up operations when only few bytes are needed (i.e. don't use mem-mapped files to read 20 bytes at known offset)
  * `HgBlameFacility` refactored: ceased to exist (didn't provide any value except holding relevant methods/structures together; not really an object), relevant methods moved to `HgDataFile`
  * Performance: much faster `Revlog#getRevisionIndex()` with lite `Nodeid` `<->` index map.
  * New `HgCommitCommand`, with use example `org.tmatesoft.hg.console.Commit`
  * New revlog patch merging mechanism in place, much more friendly for huge revisions with a lot of small patches. The mechanism is turned off by default, set hg4j.repo.merge\_revlog\_patches property to true to enable it (either with -Dhg4j.repo.merge\_revlog\_patches=true java argument or the way your `SessionContext` implementation employs)
  * Cancel and progress in new commands (commit, annotate, checkout, revert)
  * Added follow copy/rename for annotate, `HgBlameFacility` API has changed, and more API changes are expected
  * Performance: refactored the way packed revisions are unpacked (`InflaterDataAccess`). Memory-friendly and faster `HgManifest#getFileRevision`
  * Unix flags are respected during checkout/revert. I.e. if manifest of the checked-out revision lists file as executable, attempt to set executable bit on Unix/Linux.
  * POSTPONED: Refactor `HgManifest#getFileRevision` to utilize the fact manifest entries are sorted

# 1.1 Milestone 3 #
> April 9, 2013
  * Restored behavior when `HgLogCommand#execute`(`WithCopyHandler/HgFileRenameHandlerMixin`) reports renames regardless of followRename value.
  * Dirstate operations: add, remove tracked files, `HgAddRemoveCommand`
  * Repository checkout: `HgCheckoutCommand`
  * Revert changes in working copy: `HgRevertCommand`
  * Commit facility
  * Annotate facility and `HgAnnotateCommand`
  * Facility to diff file changes

# 1.1 Milestone 2 #
> December 26, 2012, **v1.1m2**
  * `HgLogCommand`: followRename with `HgChangesetTreeHandler`
  * `HgLogCommand#execute(WithCopyHandler)` rename reporting logic changed: renames are not reported unless requested (with followRename = true).
  * `HgLogCommand`: followAncestry (independent from followRename)
  * `HgLogCommand`: reversed order of changesets (`HgIterateDirection`)
  * `HgCloneCommand`: cancel & progress support
  * Exposed control over repository locks (`HgRepositoryLock`).
  * When looking for remote repository, use default from config
  * Basic support for `Rebase` and 'mq' extensions
  * Access phases information (`HgPhase`)
  * Preserve user formatting and comments when altering configuration files
  * Access to last user-supplied commit message
  * Handle include and unset directives in configuration files
  * Initial bookmarks support: discover all known, tell active bookmark

# 1.0.0 #
> July 11, 2012, **v1.0.0**

  * Minor API cleanup: removed experimental tags and couple of deprecated methods

# 0.9.5 #
> June, 2012, _imaginary tag_

  * Update to match subtle log --follow logic change in Mercurial 2.2
  * Deploy with Maven
  * Use gradle to build and deploy project
  * API refactoring and cleanup
  * Javadoc
  * Bugfixes: [issue 30](https://code.google.com/p/hg4j/issues/detail?id=30)

# 0.9.0 #
> March 30, 2012, **v0.9.0**

> A lot of API-breaking changes one need to complete prior to release not to live with awkward API down the road. FIXME comments gone.

  * complete refactoring of exceptions (use of runtime in oth.repo and checked in oth.core)
  * few handlers got promoted to top-level classes
  * consistent naming for handler methods
  * few helper classes (`RevisionMap`, `ParentWalker`) became TLC, renamed to match rest of the library
  * deprecated code removed
  * Honest 'hello' implementation for `HgRemoteRepository`
  * Javadoc


# 0.8.5 #
> March 22, 2012, **v0.8.5**

> Intermediate tag to record start of API-breaking changes, see description of 0.9.0

  * Underflow/overflow in certain repositories (Issues 24, 25, 26)
  * Extended hgignore syntax support ([Issue 28](https://code.google.com/p/hg4j/issues/detail?id=28))
  * Correct support for national characters in file names ([Issue 29](https://code.google.com/p/hg4j/issues/detail?id=29)). A great surprise to find out conscious neglect of Unicode these days.
  * Javadoc

# 0.8.0 #
> Feb 2012, **v0.8.0**

> Milestone with stable functionality prior to final API cleanup/refactoring

# 0.5.0 #
> Aug 2011, **v0.5.0**