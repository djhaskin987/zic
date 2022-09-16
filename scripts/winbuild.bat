@echo off
set GRAAL_VERSION=21.3.0
set JAVA_MAJVER=11
if not exist build\bin\ (
  mkdir build
  mkdir build\bin
)
if not exist build\bin\bb.exe (
  del /s /f "%CD%\babashka-0.9.161-windows-amd64.zip"
  bitsadmin ^
      /transfer djhaskin987_bb ^
      /download ^
      /priority FOREGROUND ^
      "https://github.com/babashka/babashka/releases/download/v0.9.161/babashka-0.9.161-windows-amd64.zip" ^
      "%CD%\babashka-0.9.161-windows-amd64.zip"
  7z x babashka-0.9.161-windows-amd64.zip bb.exe
  mv bb.exe build\bin
)
if not exist build\bin\clj.exe (
  del /s /f "%CD%\deps.clj-0.1.1155-2-windows-amd64.zip"
  bitsadmin ^
      /transfer djhaskin987_deps ^
      /download ^
      /priority FOREGROUND ^
      "https://github.com/borkdude/deps.clj/releases/download/v0.1.1155-2/deps.clj-0.1.1155-2-windows-amd64.zip" ^
      "%CD%\deps.clj-0.1.1155-2-windows-amd64.zip"
  7z x deps.clj-0.1.1155-2-windows-amd64.zip deps.exe
  move deps.exe build\bin\clj.exe
)
if not exist build\graalvm-ce-java11-%GRAAL_VERSION% (
  
  del /s /f "%CD%\graalvm-ce-java%JAVA_MAJVER%-windows-amd64-%GRAAL_VERSION%.zip"
  bitsadmin ^
      /transfer djhaskin987_graalvm ^
      /download ^
      /priority FOREGROUND ^
      "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-%GRAAL_VERSION%/graalvm-ce-java11-windows-amd64-%GRAAL_VERSION%.zip" ^
      "%CD%\graalvm-ce-java%JAVA_MAJVER%-windows-amd64-%GRAAL_VERSION%.zip"
  7z x graalvm-ce-java%JAVA_MAJVER%-windows-amd64-%GRAAL_VERSION%.zip
  move graalvm-ce-java%JAVA_MAJVER%-%GRAAL_VERSION% build\graalvm-ce-java11-%GRAAL_VERSION%
  build\graalvm-ce-java%JAVA_MAJVER%-%GRAAL_VERSION%\bin\gu.cmd install native-image
)
set "PATH=%CD%\build\bin;%CD%\build\graalvm-ce-java%JAVA_MAJVER%-%GRAAL_VERSION%\bin;%PATH%"
set "JAVA_HOME=%cd%\build\graalvm-ce-java-11-21.3.0"