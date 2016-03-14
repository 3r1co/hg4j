# Introduction #

`HgCloneCommand` is a `hg clone` counterpart. Use it to create a new empty repository and fill it with changes from remote server.

# Details #

All you need to do is to decide where to take changes from (`HgRemoteRepository`) and where to put 'em (inexistent or empty directory):
```
HgRemoteRepository hgRemote = ...;
HgCloneCommand cmd = new HgCloneCommand();
cmd.source(hgRemote);
cmd.destination(new File("path/to/new/repo/root"))
cmd.execute();
```

Note, now HgCloneCommand populates repository only, and does not populate working directory. This might change soon, however.