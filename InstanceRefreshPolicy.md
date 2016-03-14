# Introduction #

> To provide reasonable work experience, **Hg4J** caches some repository aspects.  Certain efforts are required to ensure repository information available through **Hg4j** API remains actual.

> Note, locking repository for modification in not covered here, for more information please check [HgCommitCommand page](HgCommitCommand#Repository_locks.md).

# Details #

> The library tries to be careful and sensible about caching not to become a memory hog. In few cases clients may sanely decide to cache more (e.g with `HgRevisionMap`), and drop caches as soon as they no longer need them. It's unwise, however, not to cache at all. The caches do not impose a problem if the application that uses **Hg4j** is the only one to access a repository. Once there are external processes that are interested in the same repository, there are chances our caches may become stale.

> There's no reasonable mechanism in **Java 5** to detect filesystem changes. Once **Hg4J** switches to **Java 7**, [WatchService](http://docs.oracle.com/javase/tutorial/essential/io/notification.html) would likely come in use. For the time being, **Hg4J** relies on few _ad hoc_ solutions.

> Provided commits are made by humans (not scripts) and most of mercurial files grow as they get changed, **hg4J** tracks file size and modification date to see if the file was changed. The strategy is fixed, although likely to  become subject to override in the next version. There are certain issues with this approach (I'd be grateful if anyone suggests a better solution, with Java 5 in mind):
  * granularity of modification timestamp: few hundreds milliseconds or even a second. Automated tools may end up creating tens of commits within the same filesystem timestamp.
  * certain operations (e.g. rollback of failed commit) may end up with an _unchanged_ file while there's still a need to update the internal state of the repository API.

> It's unlikely, however, that both timestamp and size would keep the same in regular use scenarios.

## New repository instance ##

> With **Hg4j** version 1.0 it was often advised to get a fresh `HgRepository` instance to make sure API reflects. This approach is discouraged as it drops every available cache, not those in fact affected by external change. Besides, it may lead to unexpected issues with repository locks (locks are tracked per repository, acquiring lock with one `HgRepository` instance and releasing it with another definitely leads to a trouble).