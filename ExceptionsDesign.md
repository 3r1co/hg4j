## Summary ##

API in `org.tmatesoft.hg.repo` uses unchecked runtime exceptions, with `HgRuntimeException` as base class for all errors.

![http://code.google.com/p/hg4j/wiki/img/hg-rt-ex-classes.png](http://code.google.com/p/hg4j/wiki/img/hg-rt-ex-classes.png)


API in `org.tmatesoft.hg.core` relies on checked exceptions rooted at `HgException`

![http://code.google.com/p/hg4j/wiki/img/hg-ex-classes.png](http://code.google.com/p/hg4j/wiki/img/hg-ex-classes.png)


Handlers (callbacks to be implemented by clients) throw `HgCallbackTargetException` which is different from the rest of **Hg4J** exceptions, not to get lost among them. Clients shall use it to propagate their own errors. Use of `RuntimeException` subclasses for this purpose is discouraged. Handlers are expected to propagate any library error (i.e. `HgRuntimeException`) to get wrapped with checked `HgLibraryFailureException`. However, if necessary, implementers may catch `HgRuntimeException` and wrap/consume/re-throw as deemed appropriate.


## Details ##

> Generally, checked exceptions describe a situation where user reaction may help to overcome an obstacle. Catch clause in this case bears more intelligence than pure `printStackTrace()`/log.

> Since most of exceptional cases in the library are hard to predict (e.g. corrupt index file entry), or is easy to avoid (e.g. revision number can be checked if exists in a given revlog), use of checked exceptions (and the need to propagate them from the very depth of the library up to client), although typical for libraries out there, seems to be not the best possible solution.

> Checked exceptions, of course, force clients to deal with exceptional situations. However, it's often clients can't do anything reasonable with the exception right away (e.g. an action listener implementation with no chances to pop-up a dialog or otherwise report an error to end-user), nor can they re-throw (due to existing API that doesn't allow exceptions, e.g. action listener with empty throws clause). In these cases exception handling often ends up with dismal printStackTrace/log/throw new RuntimeException. Use of unchecked exception still allows for try/catch error handling, although for the price of no compiler help.

> So, with _unobtrusiveness_ as main theme, here are reasons and examples to justify the approach:
    * hardly any exception suitable for reaction from user (i.e. no unverifiable data input from a user.
    * if potentially erroneous data may come from a client, there shall be method to verify it. Nice example is revision value (either `Nodeid` or `int` index), which incorrect value may have been reported with some sort of checked `WrongRevisionException`. However, there are numerous places in the code were such revision comes from a trusted source (i.e. we just read it ourselves from a revlog entry), where dealing with this exception (either catch or re-throw) is dubious, at least.
    * clean client code if exception handling is not desired, nevertheless...
    * use of single common root `HgRuntimeException` for all unchecked errors from the library leaves a chance for those interested to handle and process errors with a regular try-catch approach
    * clean library code - unchecked exceptions allow straight propagation of library's errors from callbacks without any extra effort (neither re-throw, nor explicit declaration)

## Examples ##

```
public void doSomeRepoStuff {
  // here comes code that works with classes 
  // from org.tmatesoft.hg.repo
}

public void letClientReadConsoleErrors() {
  // don't care about errors
  doSomeRepoStuff();
}

public void beSoftAndTenderToClient() {
  try {
    doSomeRepoStuff();
    // handle few specific subclasses of HgRuntimeException separately,
    // and leave rest of them to the last catch clause
  } catch (HgInvalidRevisionException ex) {
    Dialog.popup("Ha-ha, no such revision: " + ex.getRevision());
  } catch(HgInvalidFileException ex) {
    String msg = "Uh-oh, file %s got troubles: %s. Shall I try again, yes/no";
    boolean tryAgain = Dialog.popup(String.fromat(msg, ex.getFileName(), ex.getMessage()));
    if (tryAgain) {
      // well, give it another chance
    }
  } catch(HgRuntimeException ex) {
    Dialog.popup("I did my best, but can't help with " + ex.getMessage());
  }
}
```

## I don't buy the idea, nevertheless ##

> Well, it's easy to get your own **Hg4J** copy to use checked exceptions instead of runtime, please read CheckedExceptionsHowto.