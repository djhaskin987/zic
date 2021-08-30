#!/bin/sh
set -ex

# https://docs.oracle.com/javadb/10.8.3.0/adminguide/cadminsslclient.html
# https://stackoverflow.com/questions/65376092/can-jvm-trust-a-self-signed-certificate-for-only-a-single-run
# This was run to create keystore
# keytool -import -file lighttpd-environment/server.crt -keystore test.keystore
# where server.crt was just the certificate half of the server PEM file
# password 'asdfasdf'

rm -rf .zic.db
rm -rf .staging
rm -rf a
rm -rf failure

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    init

if java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'verybad' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/verybad.zip"
then
    exit 1
fi

# make sure the test DOES NOT replace existing files in the staging directory
# by putting a bogus file where a good one should be downloaded.
# BTW, because verybad was downloaded previously, the .staging directory
# SHOULD ALREADY BE THERE
# This also tests that the directory has been created.
touch .staging/a-0.1.0.zip

if java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}'
then
    exit 1
fi

rm -rf .staging/a-0.1.0.zip

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    verify \
    --set-package-name 'a'

sed -i  's/w/W/g' a/willows.txt

# If I change a normal file then verification fails
if java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    verify \
    --set-package-name 'a'
then
    exit 1
fi

# and back to normal
sed -i  's/W/w/g' a/willows.txt

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    verify \
    --set-package-name 'a'

# I can change a config file without killing verification
sed -i  's/I/U/g' a/poem.txt

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    verify \
    --set-package-name 'a'


rm -rf a/poem.txt
rm -rf a/willows.txt

# But if I remove a config file muster no longer passes
if java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    verify \
    --set-package-name 'a'
then
    exit 1
fi

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --set-package-name 'b' \
    --set-package-version 0.1.0 \
    --set-package-location "invalid,yetworks" \
    --set-package-metadata '{"for": "you"}' \
    --disable-download-package

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'a'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'b'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'c' || :


# Test that config files put down before clean installation remain intact
mkdir c
echo 'I am NOT JUST an echo' > "c/echo.txt"

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'c' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/c.zip" \
    --set-package-metadata '{"zic": {"config-files": ["c/echo.txt"]}}'

test "$(cat c/echo.txt)" = "I am NOT JUST an echo"
test -f "c/echo.txt"
test -f "c/echo.txt.c.0.1.0.new"

if [ "$(java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    files \
    --set-package-name a)" != '{"result":"package-found","package-files":[{"path":"a/poem.txt","size":44,"file-class":"config-file","checksum":"f5804ac61aa4b37500ea52077f984d7224a35d3e2d05644d62ac4940384cfa6e"},{"path":"a/willows.txt","size":16,"file-class":"normal-file","checksum":"bf20da2e626d608e486160cad938868e588603cd91fa71b4b7f604a5a4e90dfd"},{"path":"a/log.txt","size":0,"file-class":"ghost-file","checksum":null}]}' ]
then
    exit 1
fi

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --set-package-version 0.2.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.2.0.zip"

# Cannot upgrade from one package to one of equivalent version
if java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --set-package-version 0.2.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.2.0.zip"
then
    exit 1
fi

#  "Option `allow-downgrades` is disabled and downgrade detected."
if java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.1.0.zip"
then
    exit 1
fi

#  "Option `allow-downgrades` is enabled and downgrade detected."
# used to be a normal file, is now a directory
java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --enable-allow-downgrades \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.1.0.zip"

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    remove \
    --set-package-name 'failure'

# No normal files should exist anymore
if [ -n "$(find failure -type f)" ]
then
    exit 1
fi

# Clean slate for tests
rm -rf failure

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.1.0.zip"

#  Cannot update: some directories in old package are not directories in new package.
#  (This isn't explicitly checked for; it should just blow up)
if java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.2.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --set-package-version 0.2.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.2.0.zip"
then
    exit 1
fi

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    remove \
    --set-package-name 'failure'

rm -rf failure

# Clean slate for tests.
rm -rf changes
# File cases:
# used to be a config file, is now a ghost file
java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'changes' \
    --set-package-version 0.1.0 \
    --set-package-metadata '{ "zic": { "config-files": [ "changes/config-to-ghost", "changes/config-to-gone", "changes/contig-config-diffsize", "changes/contig-config-diffsize-gone", "changes/contig-config-diffsum", "changes/contig-config-diffsum-edited", "changes/contig-config-same", "changes/contig-config-same-edited", "changes/contig-config-same-gone" ], "ghost-files": [ "changes/ghost-to-ghost" ] } }' \
    --set-package-location "https://djhaskin987.me:8443/changes-0.1.0.zip"

# gone to config
# gone to config, edited in between
echo 'gone to config edited' > changes/gone-to-config-edited
echo 'contig config same edited' > changes/contig-config-same-edited
echo 'contig config grey edited' > changes/contig-config-diffsum-edited
rm -f changes/contig-config-same-gone
rm -f changes/contig-config-diffsize-gone
# Test that .1,.2,.3 works (no file is overwritten)
touch changes/config-to-gone.changes.0.1.0.backup
touch changes/contig-config-diffsum-edited.changes.0.2.0.new
touch changes/contig-config-diffsum-edited.changes.0.2.0.new.1
java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'changes' \
    --set-package-version 0.2.0 \
    --set-package-metadata '{ "zic": { "config-files": [ "changes/gone-to-config", "changes/gone-to-config-edited", "changes/contig-config-diffsize", "changes/contig-config-diffsize-gone", "changes/contig-config-diffsum", "changes/contig-config-diffsum-edited", "changes/contig-config-same", "changes/contig-config-same-edited", "changes/contig-config-same-gone" ], "ghost-files": [ "changes/config-to-ghost", "changes/ghost-to-ghost", "changes/gone-to-ghost" ] } }' \
    --set-package-location "https://djhaskin987.me:8443/changes-0.2.0.zip"

test -f changes/config-to-ghost.changes.0.1.0.backup
test -f changes/config-to-gone.changes.0.1.0.backup
test -f changes/config-to-gone.changes.0.1.0.backup.1

test -f changes/contig-config-diffsize
test ! -f changes/contig-config-diffsize.changes.0.2.0.new
test ! -f changes/contig-config-diffsize.changes.0.1.0.backup
test ! -f changes/contig-config-diffsize.changes.0.2.0.backup

test -f changes/contig-config-diffsum
test ! -f changes/contig-config-diffsum.changes.0.1.0.backup
test ! -f changes/contig-config-diffsum.changes.0.2.0.backup
test ! -f changes/contig-config-diffsum.changes.0.2.0.new
test "$(cat changes/contig-config-diffsum)" = "contig config blue"
test "$(cat changes/contig-config-diffsum.changes.0.2.0.new)" = "contig config grey"

test -f changes/contig-config-diffsize-gone
test ! -f changes/contig-config-diffsize-gone.changes.0.1.0.backup
test ! -f changes/contig-config-diffsize-gone.changes.0.2.0.backup
test ! -f changes/contig-config-diffsize-gone.changes.0.2.0.new
test "$(cat changes/contig-config-diffsize)" = "contig config blue"
test "$(cat changes/contig-config-diffsize.changes.0.2.0.new)" = "contig config red"

test -f changes/contig-config-diffsum-edited.changes.0.2.0.new
test -f changes/contig-config-diffsum-edited.changes.0.2.0.new.1
test -f changes/contig-config-diffsum-edited.changes.0.2.0.new.2
test -f changes/contig-config-diffsum-edited

test "$(cat changes/contig-config-diffsum-edited)" = "contig config grey edited"
test "$(cat changes/contig-config-diffsum-edited.changes.0.2.0.new.2)" = "contig config blue"

test -f changes/contig-config-same
test ! -f changes/contig-config-same.changes.0.2.0.new
test ! -f changes/contig-config-same.changes.0.1.0.backup
test ! -f changes/contig-config-same.changes.0.2.0.backup

test -f changes/contig-config-same-edited
test ! -f changes/contig-config-same-edited.changes.0.2.0.new
test "$(cat changes/contig-config-same-edited)" = "contig config same edited"

test -f changes/contig-config-same-gone
test ! -f changes/contig-config-same-gone.changes.0.2.0.new
test ! -f changes/contig-config-same-gone.changes.0.2.0.backup

test -f changes/gone-to-config
test ! -f changes/gone-to-config.changes.0.2.0.new
test ! -f changes/gone-to-config.changes.0.1.0.backup
test ! -f changes/gone-to-config.changes.0.2.0.backup

test -f changes/gone-to-config-edited
test ! -f changes/gone-to-config-edited.changes.0.2.0.new
test ! -f changes/gone-to-config-edited.changes.0.2.0.backup
test ! -f changes/gone-to-config-edited.changes.0.1.0.backup
test "$(cat changes/gone-to-config-edited)" = "gone to config edited"
test "$(cat changes/gone-to-config-edited)" = "gone to config edited"

test -f changes/gone-to-normal
test -f changes/normal-to-normal-different
test ! -f changes/normal-to-normal-different.changes.0.2.0.new
test ! -f changes/normal-to-normal-different.changes.0.2.0.backup
test ! -f changes/normal-to-normal-different.changes.0.1.0.backup

test -f changes/normal-to-normal-same
test -d changes/samedir

# TWO PACKAGES THAT OWN THE SAME DIRECTORY (which is okay)
java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'somethingelse' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/somethingelse-0.1.0.zip"

# TWO PACKAGES THAT OWN THE SAME FILE - GHOST VERSION (which is NOT okay)
if java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'yetsomethingelse' \
    --set-package-version 0.1.0 \
    --set-package-metadata '{"zic": {"ghost-files": ["changes/samedir/somethingelse"]}}' \
    --set-package-location "https://djhaskin987.me:8443/empty-0.1.0.zip"
then
    exit 1
fi

# TWO PACKAGES THAT OWN THE SAME FILE (which is NOT okay)
if java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'alsosomethingelse' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/alsosomethingelse-0.1.0.zip"
then
    exit 1
fi

# Check that if I specify an extra config file, it gets ignored.
java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \ target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \ add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'extraconfigfile' \
    --set-package-version 0.1.0 \
    --set-package-metadata '{"zic": {"config-files": ["a/b"]}}' \
    --set-package-location "https://djhaskin987.me:8443/empty-0.1.0.zip"

if [ "$(java -jar target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar files --set-package-name 'extraconfigfile')" != '{"result":"package-found","package-files":[]}' ]
then
    exit 1
fi


# TODO: [ ] Check that if I specify a config file the same as a ghost file, it explodes.
# happens if it does.

# Check that files check out
# Check for backups of config,s and .new's

# used to be a ghost file, is now a ghost file
# used to be a ghost file, now doesn't exist in package
# used to be a normal file, now doesn't exist in package
# used to be a config file, now doesn't exist in package
# used to be a normal file, is now a normal file, same contents
# used to be a normal file, is now a normal file, different contents
# gone to normal
# gone to config
# gone to config, edited in between
# used to be a config file, is now a config file (contiguous config): same contents
# used to be a config file, is now a config file (contiguous config): different contents
# used to be a config file, is now a config file (contiguous config): same contents, but edited in between
# used to be a config file, is now a config file (contiguous config): different contents, but edited in between
# used to be a config file, is now a config file (contiguous config): same contents, but gone in between
# used to be a config file, is now a config file (contiguous config): different contents, but gone in between
# Used to be a directory, now doesn't exist
# used to be a non-empty directory, now doesn't exist

# THEN, DELETE IT
#java -jar \
#    -Djavax.net.ssl.trustStore="test.keystore" \
#    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
#    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
#    remove \
#    --set-package-name 'changes'
# AND CHECK THE FILES AGAIN

# [ ] TODO: CHECK package file conflicts on upgrade apply between ghost-file/normal file pairs
