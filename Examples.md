

## Complete file history ##

> For a given file, we'd like to collect changesets it was modified in.

> Each example, unless explicitly mentioned, references few instances, obtained somewhat like:

```
// local repository
HgRepository hgRepo = new HgLookup().detect(new File("/temp/hg/cpython"));
// This is to keep our result
Map<Nodeid, Nodeid> changesetToFileRevision = new HashMap<Nodeid, Nodeid>();

// The file, history thereof is our goal
HgDataFile fileNode = repository.getFileNode("configure.in");
// index of its last known revision
int latestFileRevision = fileNode.getLastRevision();
```


<br />
### Straightforward loop ###

> Slowest, but most straightforward approach:

```
// iterate all file revisions
for (int fileRevisionIndex = 0; fileRevisionIndex <= latestRevision; fileRevisionIndex++) {
   // get file revision's nodeid
   Nodeid fileRevision = fileNode.getRevision(fileRevisionIndex);
   // get changeset's nodeid, note _Changeset_ in the method name
   Nodeid changesetRevision = fileNode.getChangesetRevision(fileRevision);
   // record the outcome
   changesetToNodeid.put(changesetRevision, fileRevision);
}
```


<br />
### Slightly more sophisticated, with callback ###

> This example uses experimental code (see page ExperimentalCode for details), and generally not yet encouraged. It's here just to give you another perspective:

```
// indexWalk means we'll read only revlog indexing information, no actual data is needed
// TIP means we navigate up to latest index
fileNode.indexWalk(0, HgRepository.TIP, new HgDataFile.RevisionInspector() {

   public void next(int fileRevisionIndex, Nodeid fileRevision, int linkedRevisionIndex) {
      // linkedRevisionIndex is a field in revlog index, pointing to a changeset
      Nodeid changesetRevision = hgRepo.getChangelog().getRevision(linkedRevisionIndex);
      changesetToFileRevision.put(changesetRevision, fileRevision);
   }
});
```

> Use of information available in revlog index saves us couple of calls (file read operations, in fact). `fileRevision` ad `linkedRevisionIndex`, which is index of the changelog entry, come right away.

> This approach is fastest for huge repositories as only relevant elements are read/constucted


<br />
### With `HgRevisionMap` for file revisions ###

> Utility class `HgRevisionMap` provides a handy way to go back and forth between revision indexes and `Nodeid` values. Note, however, that the instance is initialized with complete revlog (more revisions revlog has, the longer initialization would take), hence use of this class is doubtful when only few revisions are of interest. Measurement shows it pays off with number of revisions around few thousand or when `HgRevisionMap` instance is used more than once.

```
HgRevisionMap<HgDataFile> fileMap;
fileMap = new HgRevisionMap<HgDataFile>(fileNode).init();

for (int fileRevisionIndex = 0; fileRevisionIndex <= latestFileRevision; fileRevisionIndex++) {
   // file: int -> Nodeid
   Nodeid fileRevision = fileMap.revision(fileRevisionIndex);
   Nodeid changesetRevision;
   // 
   // get index of changeset revision that corresponds to file change
   int csetIndex = fileNode.getChangesetRevisionIndex(fileRevisionIndex);
   // get cset revision from its local index
   changesetRevision = hgRepo.getChangelog().getRevision(csetIndex);
   //
   // In fact, two lines above just illustrate what dedicated API method does:
   // changesetRevision = fileNode.getChangesetRevision(fileRevision);
   // 
   // record the outcome
   changesetToFileRevision.put(changesetRevision, fileRevision);
}
```


<br />
### With `HgRevisionMap` both for changelog and file revisions ###

> Similar to the above, with `HgRevisionMap` for changelog as well. This one is given apart from the previous example to stress the need to consider use of `HgRevisionMap` carefully. For huge repositories, initializing `HgRevisionMap` for changelog with tens of thousand of revisions may take notable time (compared to direct access to desired revisions as needed. Generally building of a map even for huge repositories takes hundreds of milliseconds, e.g. it take less than 100ms to build the map for _cpython_ repository with 76k+ revisions on modern computer). More important aspect, however, memory consumption (for each revision there's Nodeid instance kept).

> The code is pretty much like in the previous sample, so repeating comments are omitted:

```
HgRevisionMap<HgDataFile> fileMap;
fileMap = new HgRevisionMap<HgDataFile>(fileNode).init();
// map of all changelog revisions
HgRevisionMap<HgChangelog> clogMap;
clogMap = = new HgRevisionMap<HgChangelog>(hgRepo.getChangelog()).init();

for (int fileRevisionIndex = 0; fileRevisionIndex <= latestFileRevision; fileRevisionIndex++) {
   Nodeid fileRevision = fileMap.revision(fileRevisionIndex);
   Nodeid changesetRevision;
   int csetIndex = fileNode.getChangesetRevisionIndex(fileRevisionIndex);
   //
   // here we use HgRevisionMap instance for the changelog we've build prior to the loop
   changesetRevision = clogMap.revision(csetIndex);

   changesetToFileRevision.put(changesetRevision, fileRevision);
}

```


<br />
## Sparse file history ##

> Previous example builds complete history of the file, for each file change corresponding changeset was found. An opposite task, when file changes only from specific changesets are of interest, is covered here.

> Common code for the next samples:

```
// local repository
HgRepository hgRepo = new HgLookup().detect(new File("/temp/hg/cpython"));
// The file, history thereof is our goal
HgDataFile fileNode = repository.getFileNode("configure.in");
```

> To find out file revision in a given changeset:

```
int changesetRevIndex = ...;
// manifest keeps record of file revisions in changesets
hgRepo.getManifest().getFileRevision(changesetRevIndex, fileNode.getPath());
```

> To find file revisions in multiple changesets:

```
// index of changesets we'd visit
int[] csetRevs = new int [ ..., ..., ..., ];
//
// outcome. records changeset index and file revision at the time of the change
// note, same file revision may be referenced from few changesets
final Map<Integer, Nodeid> csetToFileRevision = new HashMap<Integer, Nodeid>();

HgManifest.Inspector collector = new HgManifest.Inspector() {

   public boolean begin(int mainfestRevIndex, Nodeid manifestRevision, int changelogRevisionIndex) {
      // for each manifest revision visited, we get some information first.
      // We don't use manifest revision, but pointer to changeset is handy, keep it
      csetRevIndex = changelogRevisionIndex;
      return true;
   }

   public boolean next(Nodeid fileRevision, Path fname, Flags flags) {
      // this would get invoked for each file recorded in the manifest revision,
      // however, as long as we use this collector with specific file, below,
      // we don't check for file name
      csetToFileRevision.put(csetRevIndex, fileRevision);
      return true;
   }

   public boolean end(int manifestRevision) {
      // if there's no #next() call, we may want to record
      // explicit value into the csetToFileRevision map to indicate
      // file was not present at that revision.
      // Alternatively, just rely on map misses to find out the same.
      return true;
   }

   // cache value for later use from another method
   private int csetRevIndex;

};

// collector would get invoked only for the specified files
hgRepo.getManifest().walkFileRevisions(fileNode.getPath(), collector, csetRevs);

```

<br />
## Collect tags for a given file ##

Step 1. Map tags to changeset revisions

---

> Map tags to changeset revisions they were introduced in.

> Get ready:

```
// local repository
HgRepository hgRepo = new HgLookup().detect(new File("/temp/hg/cpython"));
// The file, tagged history thereof is our goal
HgDataFile fileNode = repository.getFileNode("configure.in");

// we'll need map of all changelog revisions
HgRevisionMap<HgChangelog> clogMap;
clogMap = = new HgRevisionMap<HgChangelog>(hgRepo.getChangelog()).init();
// all the tags from the repository
TagInfo[] allTags = new TagInfo[repository.getTags().getAllTags().size()];
repository.getTags().getAllTags().values().toArray(allTags);
// and the outcome, map of changeset revisions to tags
HashMap<Integer, List<TagInfo>> tagRevIndex2TagInfo = new HashMap<Integer, List<TagInfo>>(allTags.length);
```

> Now, iterate all the tags, and perform a 'reverse map'. Tags point to changeset, and there might be few tags that point to the same changeset. For each changeset tagged, collect all the tags that point to  that changeset.

```
for (int i = 0; i < allTags.length; i++) {
   final Nodeid tagRevision = allTags[i].revision();
   // get index of the tagged revision, with our handy revision map
   final int tagRevisionIndex = clogMap.revisionIndex(tagRevision);
   // check if we already now some tags for this changeset index
   List<TagInfo> tagsAssociatedWithRevision = tagRevIndex2TagInfo.get(tagRevisionIndex);
   if (tagsAssociatedWithRevision == null) {
      tagRevIndex2TagInfo.put(tagRevisionIndex, tagsAssociatedWithRevision = new LinkedList<TagInfo>());
   }
   // add; it's possible to have few tags for the same revision
   tagsAssociatedWithRevision.add(allTags[i]);
}
```

> Keyset of `tagRevIndex2TagInfo` represents collection of changeset revisions we need to visit to find out what were revisions of the file at that time.

```
Collection<Integer> triCollection = tagRevIndex2TagInfo.keySet();
// whatever method you prefer to transform collection of Integer to int[]
int[] tagRevIndexes = toIntArray(triCollection);
```

<br />
Step 2. Skim through manifest

---


> Look to find out what were file revisions at the moment tags were introduced.
> This step is quite similar to [Examples#Sparse\_file\_history](Examples#Sparse_file_history.md). Here, we iterate manifests using `tagRevIndexes` and `tagRevIndex2TagInfo` from the previous step, and collect tag names for file revisions found.

```
// keep file revision and tags associated with it, if any
final Map<Nodeid, List<String>> fileRev2TagNames = new HashMap<Nodeid, List<String>>();

HgManifest.Inspector collector = new HgManifest.Inspector() {

   public boolean begin(int mainfestRevIndex, Nodeid manifestRevision, int changelogRevisionIndex) {
      // for each manifest revision visited, we get some information first.
      // We don't use manifest revision, but pointer to changeset is handy, keep it
      csetRevIndex = changelogRevisionIndex;
      return true;
   }

   public boolean next(Nodeid fileRevision, Path fname, Flags flags) {
      // we iterate only changesets with tags (see #walkFileRevisions() call below)
      assert tagRevIndex2TagInfo.containsKey(csetRevIndex);
      List<String> tags = fileRev2TagNames.get(fileRevision);
      if (tags == null) {
         // note, same fileRevision may come more than once
         // shall collect (add up) tags for each fileRevision occurance
         fileRev2TagNames.put(fileRevision, tags = new ArrayList<String>(3));
      }
      // Just name of a tag is sufficient
      // iterate over all tags associated with changeset we are looking at
      for (TagInfo ti : tagRevIndex2TagInfo.get(csetRevIndex)) {
         tags.add(ti.name());
      }
      return true;
   }

   public boolean end(int manifestRevision) {
      return true;
   }

   // cache value for later use from another method
   private int csetRevIndex;

};

// collector would get invoked only for the specified files
hgRepo.getManifest().walkFileRevisions(fileNode.getPath(), collector, tagRevIndexes);

```

> With slight `collector` modifications (take `fname` into account), we may use it to collect information for multiple files (even complete repository). `HgManifest.walk` shall be used instead of `walkFileRevisions`, of course.

<br />
Step 3. Deal with the rest of file revisions

---


```
for (int i = 0, lastRev = fileNode.getLastRevision(); i <= lastRev; i++) {
   // i is fileRevisionIndex
   // Need Nodeid as it's the key in the map we've built
   Nodeid fileRevision = fileNode.getRevision(i);
   // be more descriptive with index of the changeset revision in the printout
   int changesetRevIndex = fileNode.getChangesetRevisionIndex(i);
   // if there were tags for this file revision, here they come
   // for the given file revision there may be no tags, beware
   List<String> associatedTags = fileRev2TagNames.get(fileRevision);
   if (associatedTags == null) {
      // there were no tags for this file revision
      associatedTags = Collections.emptyList();
   }
   // use the information, log to console 
   System.out.printf("%3d%7d %s\n", i, changesetRevIndex, associatedTags);
}

```

> Result would look like:
```
...
411  70304 []
412  70461 []
413  70508 [v2.7.2rc1, v2.7.2]
414  70635 []
415  74215 []
416  75202 [v2.7.3rc1, v2.7.3rc2, v2.7.3]
417  75207 []
...
```