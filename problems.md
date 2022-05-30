# The sqlite/native-image problem

A quick google search yielded this:

  - https://github.com/oracle/graal/issues/966

  - Some thought and googling brought me here: https://www.graalvm.org/22.1/reference-manual/native-image/StaticImages/
    - This didn't work but I implemented it anyway because I want static.
  - This was after reading this: https://github.com/xerial/sqlite-jdbc/issues/584
  - Alas! That's not it. Doing more digging. https://github.com/xerial/sqlite-jdbc/issues/413
