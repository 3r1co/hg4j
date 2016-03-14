# Introduction #

`HgIncomingCommand` is `hg incoming` counterpart, discovers changes in remote repository that were not found locally.

# Details #

First, instantiate and fill the command with arguments:
```
HgRepoFacade hgRepo = ...;
HgIncomingCommand cmd = hgRepo.createIncomingCommand();

HgRemoteRepository hgRemote = ...;
cmd.against(hgRemote);
```

For details on HgRepoFacade, try [GettingStarted](GettingStarted.md). For information about remote repositories check [HgRemoteRepository](HgRemoteRepository.md).


Then, depending on your needs, you can either ask only for revisions of incoming changes (the only thing exchanged with remote server would be Nodeids, no actual data is pulled):
```
List<Nodeid> incomingRevisions = cmd.executeLite(null);
```

or can process each changeset much like as with [HgLogCommand](HgLog.md). Note, this would download complete set of changes (i.e. not only changelog, manifest, but also each file change. Depending on modifications found in remote server, it may take a while).

```
HgLogCommand.Handler handler = ...;
cmd.executeFull(handler);
```
Note, extended functionality of `HgLogCommand.FileHistoryHandler` is not taken into account.