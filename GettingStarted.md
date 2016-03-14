Either get source code from [Mercurial repository](http://code.google.com/p/hg4j/source/checkout) to build yourself or get binary build from [downloads section](http://code.google.com/p/hg4j/downloads/list).

If you choose to checkout source code, from within top directory use supplied Ant script (`build.xml`) with `samples` target to verify quickly everything is ok. This target builds `hg4j.jar` (the library itself) and `hg4j-console.jar` (sample command-line tools) and runs few commands right against hg4j own repository from the working directory. You shall get quite a lot of output, quite similar to that of Hg command line tools, indeed.

Java 5 is sufficient to compile and run Hg4J code.

With binary download, just make sure it's in the classpath of your application. I recommend to start with `HgRepoFacade`(a session-friendly wrapper of `HgRepository`, which, as its name suggests, represents Mercurial repository).
```
HgRepoFacade hgRepo = new HgRepoFacade();
```
To initialize the root object, you either need an existing repository:
```
if (!hgRepo.initFrom(new File("path/to/repo"))) {
  System.err.printf("Can't find repository in: %s\n", hgRepo.getRepository().getLocation());
  return;
}
```

alternatively, you may create a blank new repository:
```
File repoLoc = new File("path/to/new/repo");
HgInitCommand cmd = HgInitCommand().location(repoLoc).revlogV1();
hgRepo.init(cmd.execute());
```

With that, `hgRepo` is ready to issue repository-accessing commands.

**Next step:**
  * Curious what was justification for the change, and whom to blame - HgLog
  * What did I changed locally or back then at a given revision - HgStatus
  * To get content of a file - HgCat
  * Need to know what files were there at a given revision - HgManifest
  * To commit changes - HgCommitCommand
  * To see file changes between specific revisions - HgDiffCommand and HgAnnotateCommand
  * Find out changes available at remote server and missing locally - HgIncomingCommand
  * To check out specific revision - HgCheckoutCommand
  * Find out commits to push - HgOutgoingCommand
  * Manage working directory - HgAddRemoveCommand and HgRevertCommand

**Complete example**
> There are few console applications that resemble original `hg` command line tools. Check CommandLineExamples page for more details