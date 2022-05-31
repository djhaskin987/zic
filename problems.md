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
