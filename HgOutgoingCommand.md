# Introduction #

`HgOutgoingCommand` is `hg outgoing` counterpart, discovers changes made in local repository that were not found in the remote repository

# Details #

First, instantiate and fill the command with arguments:
```
HgRepoFacade hgRepo = ...;
HgOutgoingCommand cmd = hgRepo.createOutgoingCommand();

HgRemoteRepository hgRemote = ...;
cmd.against(hgRemote);
```

For details on `HgRepoFacade`, try [GettingStarted](GettingStarted.md). For information about remote repositories check [HgRemoteRepository](HgRemoteRepository.md).


Then, depending on your needs, you can either ask only for revisions of outgoing changes:
```
List<Nodeid> outgoingRevisions = cmd.executeLite(null);
```

or can process each changeset much like with [HgLogCommand](HgLog.md)
```
HgLogCommand.Handler handler = ...;
cmd.executeFull(handler);
```
The notable difference is that extended functionality of `HgLogCommand.FileHistoryHandler` is not recognized.