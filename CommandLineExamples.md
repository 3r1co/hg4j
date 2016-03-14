# Introduction #

There are few quick-and-dirty examples with functionality resembling that of the native Mercurial `hg` command-line client. These examples are not intended to substitute the native client, just to demonstrate how one can use **Hg4J** library to access Mercurial repositories.

# Details #

> In addition to `hg4j_<version>.jar`, you'd need `hg4j-console_<version>.jar` in the classpath as well. You can found jars at the [downloads page](http://code.google.com/p/hg4j/downloads/list).

All the samples below assume your working directory is Mercurial repository.
```
cd /path/to/working/dir
```

Alternatively, one may specify repository location using `-R` or `--repository` argument, exactly like with `hg`.

## hg log ##
Class: **org.tmatesoft.hg.console.Log**

```
java -cp hg4j_0.1.0.jar;hg4j-console_0.1.0.jar org.tmatesoft.hg.console.Log --limit 1 --debug
```

## hg manifest ##
Class: **org.tmatesoft.hg.console.Manifest**

```
java -cp hg4j_0.1.0.jar;hg4j-console_0.1.0.jar org.tmatesoft.hg.console.Manifest --debug
```

## hg status ##
Class: **org.tmatesoft.hg.console.Status**

Working directory:
```
java -cp hg4j_0.1.0.jar;hg4j-console_0.1.0.jar org.tmatesoft.hg.console.Status
```

Specific revisions:
```
java -cp hg4j_0.1.0.jar;hg4j-console_0.1.0.jar org.tmatesoft.hg.console.Status --rev 1 --rev 3
```

## hg cat ##
Class: **org.tmatesoft.hg.console.Cat**

You'll need to put a real filename instead of `build.xml` below
```
java -cp hg4j_0.1.0.jar;hg4j-console_0.1.0.jar org.tmatesoft.hg.console.Cat --rev 1 build.xml
```