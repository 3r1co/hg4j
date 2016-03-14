# Introduction #

Similar to `hg manifest` command line command, `HgManifestCommand` provides access to list of files in a given revision.

# Details #
First, initialize repository access facade (assuming current directory is a mercurial repository of interest):
```
HgRepoFacade hgRepo = new HgRepoFacade();
if (!hgRepo.init()) {
        System.err.printf("Can't find repository in: %s\n", hgRepo.getRepository().getLocation());
        return;
}
```

Instantiate a command
```
HgManifestCommand cmd = hgRepo.createManifestCommand();
```

Decide how to process data (for more information about Handler, see below)
```
HgManifestCommand.Handler handler = new MyTreeVisitor();
```

You may walk specific revision (or few at once) of a repository tree. Next sample sequentially yields trees of 3 revisions with local numbers 17, 18 and 19.
```
cmd = cmd.range(17, 19);
cmd.execute(handler);
```

If you're interested in specific files only, use `Path.Matcher`.
```
cmd = cmd.match(new PathGlobMatcher("src/**/*.java", "scrips"));
```

If you'd like to see not only file names, but grouped and headed by directories they reside in, command may sort it out for you.
```
cmd = cmd.dirs(true);
```

## Handlers ##
`HgManifestCommand.Handler`

`#begin(Nodeid)` indicates start of the tree for specified revision.

`#end(Nodeid)` indicates all files for the revision has been reported.

`#file(FileRevision)` next file entry in the manifest, with revision information

`dir(Path)` notifies (if requested) entering to directory (subsequent files are from specified directory).

# API #
  * `org.tmatesoft.hg.core.HgManifestCommand`
  * `org.tmatesoft.hg.core.HgManifestCommand.Handler`
  * `org.tmatesoft.hg.util.Path.Matcher`
  * `org.tmatesoft.hg.HgLogCommand.FileRevision`