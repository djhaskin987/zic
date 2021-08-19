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

if [ "$(java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    files \
    --set-package-name a)" != '{"result":"package-found","package-files":[{"path":"a/","size":0,"file-class":"directory","checksum":null},{"path":"a/poem.txt","size":44,"file-class":"config-file","checksum":"f5804ac61aa4b37500ea52077f984d7224a35d3e2d05644d62ac4940384cfa6e"},{"path":"a/willows.txt","size":16,"file-class":"normal-file","checksum":"bf20da2e626d608e486160cad938868e588603cd91fa71b4b7f604a5a4e90dfd"},{"path":"a/log.txt","size":0,"file-class":"ghost-file","checksum":null}]}' ]
then
    exit 1
fi


# New testcases
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

# It better actually be gone
if [ -d failure ]
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
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.1.0.zip"

#  "Cannot update: some directories in old package are not directories in new package."
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
