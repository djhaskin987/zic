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
    --set-package-location "https://djhaskin987.me:8443/a.zip"

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

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    files \
    --set-package-name a

