## Summary ##

> Starting points:
    * `org.tmatesoft.hg.util.CancelSupport`
    * `org.tmatesoft.hg.util.ProgressSupport`

> Unlike some other frameworks, cancellation and progress reporting are represented as distinct aspects in **Hg4J**. Their instances are not passed around explicitly in most operation that may utilize them (not to pollute API and code unless clients really use it), but rather obtained from the context/operation arguments using `org.tmatesoft.hg.util.Adaptable` mechanism or supplied using dedicated method (`Hg\*Command.set()).


### Cancellation ###
> Couple of main players here are `org.tmatesoft.hg.util.CancelSupport` and checked `org.tmatesoft.hg.util.CancelledException`.

```
public interface CancelSupport {
    void checkCancelled() throws CancelledException;
}
```

> Client-supplied implementations shall throw `CancelledException` or return gracefully from the `checkCancelled()` method according to their internal state.

> The library consults client-supplied cancel support instance once in a while and propagates `CancelledException`, if any, up to the client without any further modifications.

> Note, handlers/callbacks are not allowed to throw CancelledException from their methods. If cancellation shall be part of the handler (i.e. not an external object), they may implement either `CancelSupport` or `Adaptable` to expose their cancellation logic.

> To get non-cancellable helper, use:
```
  CancelSupport noCancel = CancelSupport.Factory.get(null);
```


### Progress reporting ###

> Main player here is `org.tmatesoft.hg.util.ProgressSupport`
```
public interface ProgressSupport {
    void start(int totalUnits);
    void worked(int units);
    void done();
}
```

Lifecycle:
  * `start()`, called once, with total number of steps to report, or -1 if unknown
  * `worked()`, multiple calls, or not at all (e.g. if cancelled prior to actual work), to report gradual improvement.
  * `done()`, called once, to indicate operation is over (if `done()` is not reported e.g. in case of exception, it's a bug)