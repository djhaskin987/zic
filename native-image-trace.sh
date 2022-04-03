#!/bin/sh
set -ex


# Dependency tree testing
rm -rf .zic.db
rm -rf .staging
rm -rf a
rm -rf b
rm -rf c
rm -rf changes
rm -rf failure


java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    init

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'c' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/c.zip"

# https://www.graalvm.org/22.0/reference-manual/native-image/Agent/
# Make sure to run `gu install native-image` first or you will get an "so file
# not found" error
java \
    -agentlib:native-image-agent=config-output-dir=META-INF/native-image/ \
    -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'b' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/b.zip" \
    --add-package-dependency 'c'
