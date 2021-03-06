#!/bin/sh
set -ex

# https://docs.oracle.com/javadb/10.8.3.0/adminguide/cadminsslclient.html
# https://stackoverflow.com/questions/65376092/can-jvm-trust-a-self-signed-certificate-for-only-a-single-run
# This was run to create keystore
# keytool -import -file lighttpd-environment/server.crt -keystore test.keystore
# where server.crt was just the certificate half of the server PEM file
# password 'asdfasdf'

rm -rf .zic.db

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

sed -i  's/I/U/g' a/poem.txt

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
    --set-package-name a)" != '{"result":"package-found","package-files":[{"path":"a/","size":0,"file-class":"directory","checksum":null},{"path":"a/poem.txt","size":44,"file-class":"config-file","checksum":"f5804ac61aa4b37500ea52077f984d7224a35d3e2d05644d62ac4940384cfa6e"},{"path":"a/log.txt","size":0,"file-class":"ghost-file","checksum":null}]}' ]
then
    exit 1
fi

