# Introduction #

Simple example below demonstrates how to obtain status of the files in the working copy with the help of Hg4J API.


# Details #

```
String repositoryPath = "";
if (args.length > 0) {
   repositoryPath = args[0];
}

HgRepoFacade hgRepo = new HgRepoFacade();
try {
  if (!hgRepo.initFrom(new File(repositoryPath))) {
   System.out.printf("No Mercurial repository found at '%s'.\n", hgRepo.getRepository().getLocation());
   System.exit(1);
  }
		
  HgStatusCommand statusCommand = hgRepo.createStatusCommand();
  System.out.printf("About to display working tree status (%s)\n\n", hgRepo.getRepository().getLocation());

  // .all() - indicates we're interested in any status,
  // not only default Modified, Added, Removed, Missing, Unknown
  // Processing is primitive here, just dump filename and its status
  statusCommand.all().execute(new HgStatusCommand.Handler() {
     public void handleStatus(HgStatus status) {
       System.out.printf("%s %s\n", status.getKind(), status.getPath());
     }
  });

  System.out.printf("\nStatus of the working tree is displayed above.");
} catch (HgException e) {
   System.out.printf("Error accessing Mercurial repository at '%s'.\n", hgRepo.getRepository().getLocation());
   e.printStackTrace();
   System.exit(1);
}

```

# Sample output #
```
About to display working tree status (C:\Users\alex\workspace\hg4j)
...
Clean lib/junit-4.8.2-src.jar
Clean lib/junit-4.8.2.jar
Ignored bin/org/tmatesoft/hg/console/Bundle.class
Ignored bin/org/tmatesoft/hg/console/Cat$OutputStreamChannel.class
...
Clean src/org/tmatesoft/hg/util/Path.java
Clean src/org/tmatesoft/hg/util/PathPool.java
Clean src/org/tmatesoft/hg/util/PathRewrite.java
Clean src/org/tmatesoft/hg/util/ProgressSupport.java
Clean test/org/tmatesoft/hg/test/Configuration.java
Unknown test/org/tmatesoft/hg/test/DisplayWorkingTreeStatus.java
Clean test/org/tmatesoft/hg/test/ErrorCollectorExt.java
Clean test/org/tmatesoft/hg/test/ExecHelper.java
Clean test/org/tmatesoft/hg/test/LogOutputParser.java
Clean test/org/tmatesoft/hg/test/ManifestOutputParser.java
Clean test/org/tmatesoft/hg/test/OutputParser.java
Clean test/org/tmatesoft/hg/test/StatusOutputParser.java
Clean test/org/tmatesoft/hg/test/TestByteChannel.java
Clean test/org/tmatesoft/hg/test/TestHistory.java
Clean test/org/tmatesoft/hg/test/TestManifest.java
Clean test/org/tmatesoft/hg/test/TestStatus.java
Clean test/org/tmatesoft/hg/test/TestStorePath.java
...
Status of the working tree is displayed above.
```