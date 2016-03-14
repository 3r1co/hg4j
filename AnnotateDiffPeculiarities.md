> Since there's no standard way to annotate changes in version-controlled files, each implementation tries to find its own hard way to do it right. **Hg4J** is not an exception here, and there are few peculiarities worth know about. You may find this information useful if a question like "How come 'hg annotate' gives slightly different result than `HgAnnotateCommand`?" or "Why on earth did stupid authors of **Hg4J** implement their own annotate and didn't follow that of 'hg annotate'?" sound familiar. On a side (but quite important) note, there's no standard 'hg annotate' output either, chances are different versions of native client would produce different 'hg annotate' result for the same repository are high.

> There are few differences in the approach **Hg4J** utilizes compared to that of native Mercurial client (as of 2.x):
  1. Iteration order of file changes.
> > ...
> > Mercurial walks from parents to children, and traces equal lines.
  1. Handling of merge revisions
> > ....
  1. Detecting of 'common' lines (like '}' in Java)
> > ....
> > our diff implementation doesn't process these lines in any special way (while the one Mercurial uses does), hence such lines may be attributed to different change blocks