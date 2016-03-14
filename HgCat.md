# Introduction #

Way to get content of any file revision, same what `hg cat` does in command line. Simply pass file name, revision number and decide how output is to be consumed.


# Details #

## Initialize repository ##
```
HgRepoFacade hgRepo = new HgRepoFacade();
if (!hgRepo.init()) {
	System.err.printf("Can't find repository in: %s\n", hgRepo.getRepository().getLocation());
	return;
}
```

## Get file revision content ##
```
Nodeid ofInterest = Nodeid.fromAscii("<40 hex digits>");
Path f = "path/to/file";
HgCatCommand cmd = hgRepo.createCatCommand().revision(ofInterest).file(f);
```
Command pipes its output to a byte channel. ByteChannel subclasses may direct output to a file, keep it in memory or do anything else.
Note, any content filters (like eol or keyword processing) would get applied according to repository configuration.
```
ByteChannel sink = new ByteArrayChannel();
cmd.execute(sink);
```

Further processing depends on supplied ByteChannel implementation, for example, ByteChannel that keeps output in memory may be transformed to a string and presented to user.
```
String result = new String(sink.toArray());
System.out.println(result);
```

# API #
  * `org.tmatesoft.hg.core.Nodeid`
  * `org.tmatesoft.hg.util.Path`
  * `org.tmatesoft.hg.util.ByteChannel`