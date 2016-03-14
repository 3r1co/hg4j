> As described in ExceptionsDesign, low-level part of **Hg4J** library uses runtime exceptions to tell about unsatisfied expectations or unexpected obstacles while dealing with Mercurial repositories. However, not everyone out there share the same perspective, and consider use of runtime exceptions inappropriate or uncomfortable, at least.

> The good news is it takes few easy step to get a clone of **Hg4J** with checked exceptions in place of runtime.

> To switch from runtime to checked exceptions, you'll need to make next changes to the source code:
    1. `HgRuntimeException`: extend regular `Exception` instead of `RuntimeException`
    1. `HgInvalidStateException`: extend `RuntimeException` directly, instead of `HgRuntimeException`, as the nature of this exception is unpredictable or impossible state of the library.

> With these changes, there'd be no compilation errors and the only unpleasant leftover is _"Runtime exception"_ tag in JavaDoc @throws descriptions. Worries fade away, however, if you'd treat it as "happens at run time" instead of "extends `java.lang.RuntumeException`.