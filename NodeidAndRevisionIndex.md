> In the API, following terms are in use
    * _revision_ - unique identifier of a change, 20-bytes hash value
    * _revision index_ - integer, index of the revision in the local copy of a repository

> In API where revision is expected, there are usually pair of methods, one that take `Nodeid` and another with `int` arguments, e.g. for a `HgDataFile`:
```
   public int getLength(Nodeid nodeid);
   public int getLength(int fileRevisionIndex);
```

**Note**, it's important not to confuse revisions of a file with revisions of a changelog or a manifest. Each revlog entry is identified with `Nodeid` and can be accessed with an index, hence care shall be taken when using repository revisions to access e.g. file contents, see section below for details.


### Revision ###

> Simply put, SHA-1 digest of the actual change/version, 20 bytes. Is unique within the repository, presumably. Identifies a change in a revlog, whether it's changeset, manifest or a file version.

> Besides identification, serves the purpose of repository integrity validation, detecting an error if calculated digest values doesn't match that stored.

> See original Mercurial description of [nodeid](http://mercurial.selenic.com/wiki/Nodeid).

> In API:
```
   Nodeid HgFileRevision.getRevision();
   Nodeid HgChangeset.getNodeid();
   // from Nodeid to revision index
   int Revlog.getRevisionIndex(Nodeid nid)
```


### Revision index ###

> Convenient zero-based index of a version in the the local repository copy. These indexes are likely to differ in repository copies for different users/branches, and shall not be used beyond the scope of local repository. They are handy mostly because of fast indexed access to specific revisions (use of `Nodeid` involves lookup step, which may be quite time-consuming in certain scenarios)

> Sample in the API:
```
   int HgChangeset.getRevisionIndex();
   // from revision index to Nodeid
   Nodeid Revlog.getRevision(int revisionIndex);
```


### Important difference between file, manifest and changelog revisions ###

> User files, stored in the repository, as well as Mercurial housekeeping changelog and manifest files are stored in so called [revlog](http://mercurial.selenic.com/wiki/Revlog) format. Each change, whether it's in changelog, manifest or in the user's data, is recorded as entry in this revlog, hence has sha-1 digest and local index. These of changelog are usually what users see as repository revision, and what they use to identify specific state of the repository. However, clients that deal with internals of a Mercurial repository, shall distinguish revision as a user-level concept (aka changeset) from use of 'revision' as identifier in a given revlog.

> Most API methods in HgManifest and HgChangelog deal with changeset identifier as it's straightforward and what most clients need, e.g. `HgManifest.walk()` and `HgChangelog.range()`. API of the `HgDataFile`, however, mostly relies on identifiers of file versions, and an extra step is required to find out file revision for a given changeset revision:
```
   // API
   Nodeid HgManifest.getFileRevision(int changelogRevisionIndex, Path file);
   void HgManifest.walkFileRevisions(Path file, Inspector inspector, int... changelogRevisionIndexes);
```

> To walk in backwards direction, to find out changeset when the file revision was created:
```
   HgDataFile.getChangesetRevisionIndex(int fileRevisionIndex)
   HgDataFile.getChangesetRevision(Nodeid nid)
```

> Sample use:
```
   HgDataFile df = hgRepo.getFileNode(...);
   Nodeid changesetRevision = ...;
   int changesetRevisionIndex = hgRepo.getChangeset().getRevisionIndex(changesetRevision);
   Nodeid fileRevision = hgRepo.getManifest().getFileRevision(changesetRevisionIndex, df.getPath());
   int fileRevisionIndex = df.getRevisionIndex(fileRevision); // in case Nodeid is not enough.
   //
   assert changesetRevisionIndex == df.getChangesetRevisionIndex(fileRevisionIndex);
   assert changesetRevision.equals(df.getChangesetRevision(fileRevision))
```