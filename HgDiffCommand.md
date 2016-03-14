# Introduction #

> `HgDiffCommand` helps to figure out file changes in specific revision or in a range of revisions.

> This command was added in version 1.1.

# Details #

> We start with an instance of the command:
```
 HgDiffCommand cmd = hgRepoFacade.createDiffCommand();
```

> The command needs file. As of version 1.1, **Hg4J** doesn't support diff between revisions, functionality to report diff for multiple files is left for future versions.
```
 HgDataFile df = ...; 
 cmd.file(df);
```

> Depending on desired diff kind (see below), either specify a single revision, two revisions or a range thereof to blame:
```
 cmd.changeset(HgRepository.TIP);
```
```
 cmd.range(5, 17);
```

> Along with the `range`, iteration order may come handy:
```
 cmd.order(HgIterateDirection.NewToOld);
```

> And finally, to execute the command, pick one:
    * `cmd.executeDiff()` to mimic `hg diff -r rev1 -r rev2 file`, differences the file between two revisions. The command takes two specified revisions and emits differences via `HgBlameInspector`.
    * `cmd.executeAnnotate()` walks the range of revisions and reports differences between each successive revision. Changes in merged revisions are reported from both parents as a combined diff (i.e. "this change comes from p1, and that one - from p2), not as two distinct diffs (for such, `executeDiff` might come handy). Start of the range has to be among ancestors of the range end revision. Respects iteration order.
    * `cmd.executeParentsAnnotate()` annotates changes in the given revision against its parent(s). With a single-parent revision, it's identical to `hg diff -c rev`. For merge revisions, the command respects both parents and report changes from either.

# Handler #
> `org.tmatesoft.hg.core.HgBlameInspector` get notified about each diff block.

> Handlers may additionally implement `RevisionDescriptor.Recipient` if they need extra before/after notifications when visiting a revision or would like to find out more about annotated revisions.

# Limitations #

  1. Since there's not _standard_ or _canonical_ way to do diff, various implementations may yield slightly different, although legal, results. E.g. lines like open/close braces, newlines and other _common_ lines may get attributed to different revisions by different diff implementations. Present diff implementation in **Hg4J** doesn't handle these _common_ lines in any special way, while Python's difflib does.
  1. The command now works with specific file only. To get diff between revisions, one would need to run `HgStatusCommand` to get list of modified files and then issue `HgDiffCommand` for each of them.