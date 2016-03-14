# Introduction #

From command line, `hg log` and `hg log <filename>` commands are used to access changelog and to show changesets. With **hg4j**, one shall use `HgLogCommand` class to accomplish the same.

# Details #

## Initialize repository ##
First, we need an access to Mercurial repository, in this case (`HgRepoFacade#init()`) from the current working directory
```
HgRepoFacade hgRepo = new HgRepoFacade();
if (!hgRepo.init()) {
	System.err.printf("Can't find repository in: %s\n", hgRepo.getRepository().getLocation());
	return;
}
```

## History of the whole repository ##
Get command instance through the facade
```
HgLogCommand cmd = hgRepo.createLogCommand();
```

alternatively, one can instantiate new instance directly
```
HgLogCommand cmd = new HgLogCommand(hgRepo.getRepository());
```
Former approach might be beneficial when you gonna run few commands over same repository as HgRepoFacade may cache and share information between commands (although it's not yet implemented)

Command may get executed right away, yielding complete history of the repo. However, in most cases command shall get parametrized first to reflect what we'd like to get. Here, we'd like to see at most 5 commits made by a given user
```
cmd = cmd.user("John Doe").limit(5);
```

The command can be executed right away, for more sophisticated handling, however, it's advised to use dedicated callback.
```
List<HgChangeset> result = cmd.execute();
```

Alternatively, one may pass a smart consumer of history output (see below for handler examples)
```
HgLogCommand.Handler handler = new ChangesetDumpHandler();
cmd.execute(handler)
```

## History of an individual file ##
Pretty much the same as history of the whole repository.
Need to specify file path, relative to repository root.
Second argument (boolean) indicates whether we'd like to follow copy/renames of the file (similar to `--follow` in Hg command line interface)

```
cmd = cmd.user("John Doe").file("src/samples/Sample.java", true);
List<HgChangeset> result = cmd.execute();
```
Rresulting changesets are partially ordered (per filename), however, it's safe to assume they come in arbitrary order.


## Handler ##
`HgLogCommand.Handler` or `HgLogCommand.FileHistoryHandler` if you're interested to be notified when copy/rename origin is followedclass ChangesetDumpHandler implements HgLogCommand.FileHistoryHandler {

  // from HgLogCommand.Handler
  public void next(HgChangeset cset) {
    System.out.printf("changeset: %d:%s\n", cset.getRevision(), cset.getNodeid().shortNotation());
    System.out.print("affected files:");
    for (FileRevision s : cset.getModifiedFiles()) {
      System.out.print(' ');
      System.out.print(s.getPath());
    }
    System.out.print('\n');
  }


  // from HgLogCommand.FileHistoryHandler
  public void copy(FileRevision from, FileRevision to) {
    System.out.printf("Got notified that %s(%s) was originally known as %s(%s)\n", to.getPath(), to.getRevision(), from.getPath(), from.getRevision());
  }
}```