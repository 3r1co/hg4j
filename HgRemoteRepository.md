# Introduction #

**hg4j** currently supports remote repository exposed via HTTP/HTTPS. For HTTPS, no certificate handling yet, neither client-side certificates, nor server-side. Servers with invalid certificate (outdated, wrong name) are silently accepted.

Access via SSH is scheduled for later releases.

# Details #

There are two methods to get `HgRemoteRepository` instance:

```
URL url = new URL("http://user:passwd@localhost:8000/hello");
HgRemoteRepository hgRemote = new HgLookup().detect(url);
```

and

```
HgRemoteRepository hgRemote = new HgLookup().detectRemote("hg4j-googlecode", null);
```

which uses information from `hgrc` configuration file, e.g.

```
[paths]
hg4j-googlecode = https://hg4j.googlecode.com/hg/

[auth]
googlecode.prefix   = hg4j.googlecode.com/hg/
googlecode.schemes  = https 
googlecode.username = user
googlecode.password = passwd
```


It's possible to use `HgLookup.detectRemote()` for plain URLs, not only configuration keys. Second argument (not in use now) gives context where `HgLookup` may find additional configuration files.