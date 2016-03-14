# Introduction #

> `HgCommitCommand` is pretty much the same as `hg commit`, commit outstanding changes into repository.

> This command was added in version 1.1.

# Details #

> The command use is straightforward:
```
 HgCommitCommand cmd = hgRepoFacade.createCommitCommand();
 cmd.message("Bug #123: fix foo functionality")
 Outcome o = cmd.execute();
 if (!o.isOk()) {
   // commit didn't succeed due to unsatisfied pre-condition
   // e.g. no outstanding changes
 }
```

> After successful commit, one may find out newly added revision:
```
  Nodeid newRev = cmd.getCommittedRevision();
```

> The command respects added/removed files as recorded in dirstate (e.g. with HgAddRemoveCommand).

# Repository locks #

> The command locks repository with the same mechanism as native Mercurial client. First, lock for working directory is acquired, then lock for the storage area. Locks are released in reverse order.

> Locks are always regular files, although native **hg** uses symbolic links on Linux. Links are quite uncomfortable to work with from Java (namely, Java5), and fallback solution, with regular files, works fine.

> It's possible, and often desirable, to lock repository explicitly, e.g. when running few commit commands at once. To lock working directory:
```
  try {
    // get lock accessor, this call does not lock a repository
    HgRepositoryLock wdLock = hgRepo.getWorkingDirLock();
    // perform actual locking
    wdLock.acquire();
  } catch (HgRepositoryLockException ex) {
    // failed to obtain the lock, react
  }
```

> To release the lock:
```
  try {
    HgRepositoryLock wdLock = hgRepo.getWorkingDirLock();
    wdLock.release();
  } catch (HgRepositoryLockException ex) {
    // lock was not owned, couldn't be released
  }
```

> It's important that `hgRepo` instance in both cases is the same, because `HgRepositoryLock` instance keeps count of acquired locks and makes a decision whether to remove actual lock file on `release()` based on this count.

> Locks are subject for [ui.timeout](http://www.selenic.com/mercurial/hgrc.5.html#ui) configuration property. `repoLock.acquire()` waits for specified amount of time prior to failing with `HgRepositoryLockException`.


# Failures and rollback #

> As a command that modifies repository, `HgCommitCommand` tries to minimize chances to ruin repository in case anything goes wrong. **Hg4J** creates a backup of each modified system file (`undo.dirstate`, `undo.branch` and `<filename>.hg4j.orig` for .i/.d files; this strategy might become configurable in next versions) and reverts to these backups in case of any error during commit.