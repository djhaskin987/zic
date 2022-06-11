# The sqlite/native-image problem

A quick google search yielded this:

  - https://github.com/oracle/graal/issues/966

  - Some thought and googling brought me here: https://www.graalvm.org/22.1/reference-manual/native-image/StaticImages/
    - This didn't work but I implemented it anyway because I want static.
  - This was after reading this: https://github.com/xerial/sqlite-jdbc/issues/584
  - Alas! That's not it. Doing more digging. https://github.com/xerial/sqlite-jdbc/issues/413


OKayokayokay. After COMPLETELEY REWORKING the database ssystem (switched from sqlite3 to h2), here are my thoughts:

- I still had a major issue, which hopefully I resolved. It's this whole H2
  problem. I added a --trace-* something and it took FOREVER to run, but it ran
  and the results are in the file `most-recent-native-image-trace`. I
  "listened" to it and now,
- COMPILE WORKS!!! XOL LOVES
- H2's referential integrity actually **works**, and caught bugs.
- We still have a problem.

```
+ ./zic-0.1.0-SNAPSHOT-standalone -Djavax.net.ssl.trustStore=test.keystore -Djavax.net.ssl.trustStorePassword=asdfasdf add --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' --set-package-name a --set-package-version 0.1.0 --set-package-location https://djhaskin987.me:8443/a.zip --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}'
Exception in thread "main" java.lang.NoSuchMethodError: java.lang.reflect.AccessibleObject.canAccess(java.lang.Object)
        at java.lang.invoke.MethodHandleNatives.resolve(MethodHandleNatives.java:230)
        at java.lang.invoke.MethodHandle.invokeBasic(MethodHandle.java:75)
        at java.lang.invoke.MethodHandle.invokeBasic(MethodHandle.java:0)
        at java.lang.invoke.Invokers$Holder.invoke_MT(Invokers$Holder)
        at clojure.lang.Reflector.canAccess(Reflector.java:49)
        at clojure.lang.Reflector.isAccessibleMatch(Reflector.java:253)
        at clojure.lang.Reflector.getAsMethodOfAccessibleBase(Reflector.java:231)
        at clojure.lang.Reflector.invokeMatchingMethod(Reflector.java:160)
        at clojure.lang.Reflector.invokeNoArgInstanceMember(Reflector.java:438)
        at zic.fs$all_parents.invokeStatic(fs.clj:317)
        at zic.fs$find_marking_file.invokeStatic(fs.clj:323)
        at zic.cli$run$fn__2948.invoke(cli.clj:284)
        at onecli.core$go_BANG_.invokeStatic(core.clj:627)
        at zic.cli$run.invokeStatic(cli.clj:275)
        at zic.cli$_main.invokeStatic(cli.clj:314)
        at zic.cli$_main.doInvoke(cli.clj:314)
        at clojure.lang.RestFn.applyTo(RestFn.java:137)
        at zic.cli.main(Unknown Source)
```

But after I googled the problem, I got borkdude's java 11 fix! so this might be surmountable.

An excerpt from the README of this: https://github.com/borkdude/clj-reflector-graal-java11-fix

> tada Great news! Starting with GraalVM v21, this fix should no longer be needed.

> Instead, you will probably need to add this to your reflection config:

> ```[{"name": "java.lang.reflect.AccessibleObject",
  "methods" : [{"name":"canAccess"}]},
  ...
]```

So like, that worked. Now I'm just sorting through SSL exceptions trying to get my janky web server recognized.

Something like this would be good, a run-time solution that would allow the user to specify a cert: https://stackoverflow.com/questions/1201048/allowing-java-to-use-an-untrusted-certificate-for-ssl-https-connection/1201102#1201102

Maybe something clojure-specific?

https://gist.github.com/mikeananev/76346532933bd9ff108ccbb04a89b849

I don't know, man. Maybe just make sure the -D stuff works like it used to? That would be nice. https://www.graalvm.org/22.1/reference-manual/native-image/Properties/

The offending command: `./zic-0.1.0-SNAPSHOT-standalone -Djavax.net.ssl.trustStore=test.keystore -Djavax.net.ssl.trustStorePassword=asdfasdf add --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' --set-package-name a --set-package-version 0.1.0 --set-package-location https://djhaskin987.me:8443/a.zip --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}'`
o the system property is `javax.net.ssl.trustStore` and its associated `trustStorePassword`. Let's research those.0lA

https://stackoverflow.com/a/59056537 <-- what is a TRUST STORE TYPE!??!!?

Maybe this? https://github.com/aphyr/less-awful-ssl

WELL THEN. https://www.graalvm.org/22.1/reference-manual/native-image/CertificateManagement/

But that's for 22.1 . Guess I have to upgrade my graalvm. Maybe if I do so, this will work?!?

Where have you been all my life. https://github.com/clj-easy/graal-docs#jdk11-and-clojurelangreflector

# The failproof project

The idea is to use the fallback image, then bake the JVM in via AppImage.
- https://clojure.org/reference/compilation
- AppImage

DO NOT USE AppImage. AppImage unpacks a file system, runs the program, then
cleans up the FS after. Great for UIs, but not CLIs.

Class initialization if +PrintClassInitialization is specified to native-image gets saved to src/zic/reports/class_initialization_configuration_20220610_165104.csv

A very helpful guide, which put build time vs. runtime into perspective.
https://www.graalvm.org/22.1/reference-manual/native-image/Limitations/ .
Basically, if it loads classes dynamically or via reflection or whatever, load
it at build time. Also, fallback ain't so bad. It just requires a VM, but it
still way speeds up start up time.

And now with the `IllegalArgumentException`:

```
  Caused by: java.lang.IllegalArgumentException: Path: nio:/home/djhaskin987/Development/src/zic/test/resources/data/all/.zic.mv.db
      at org.h2.store.fs.FilePathWrapper.create(FilePathWrapper.java:49)
      at org.h2.store.fs.FilePathWrapper.getPath(FilePathWrapper.java:24)
      at org.h2.store.fs.FilePathWrapper.getPath(FilePathWrapper.java:18)
      at org.h2.store.fs.FilePath.get(FilePath.java:62)
      at org.h2.mvstore.FileStore.open(FileStore.java:142)
      at org.h2.mvstore.MVStore.<init>(MVStore.java:390)
      at org.h2.mvstore.MVStore$Builder.open(MVStore.java:3343)
      at org.h2.mvstore.db.MVTableEngine$Store.open(MVTableEngine.java:162)
      at org.h2.mvstore.db.MVTableEngine.init(MVTableEngine.java:95)
      at org.h2.engine.Database.getPageStore(Database.java:2739)
      at org.h2.engine.Database.open(Database.java:769)
      at org.h2.engine.Database.openDatabase(Database.java:319)
      ... 29 more
  Caused by: java.lang.NoSuchMethodException: org.h2.store.fs.FilePathNio.<init>()
      at java.lang.Class.getConstructor0(DynamicHub.java:3585)
      at java.lang.Class.getDeclaredConstructor(DynamicHub.java:2754)
      at org.h2.store.fs.FilePathWrapper.create(FilePathWrapper.java:44)
      ... 40 more
```
D'OH! I didn't see the `NoSuchMethodException` below.

https://github.com/oracle/graal/issues/865




Using `-Dhibernate.bytecode.provider=none` may help, or installing this one thingamarig:
https://docs.jboss.org/hibernate/orm/5.0/topical/html/bytecode/BytecodeEnhancement.html


Apparently this in known: https://github.com/oracle/graal/issues/3248#issuecomment-816665688

