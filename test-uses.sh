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

if [ "${1}" = "tracing" ]
then
    # https://www.graalvm.org/22.0/reference-manual/native-image/Agent/
    java='java -agentlib:native-image-agent=config-merge-dir=META-INF/native-image/'
else
    java='java'
fi
$java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    init

$java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'c' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/c.zip"

$java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'b' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/b.zip" \
    --add-package-dependency 'c'

# Test that unmet dependencies stop installation.
if $java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'd'
then
    exit 1
fi

$java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'c'

# TODO: The output of these commands need to be checked manually at the moment

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependers \
    -k 'c'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependers \
    -k 'b'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependers \
    -k 'a'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'c'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'b'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'a'

$java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.2.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'b'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'a'

$java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.3.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'b' \
    -u 'c'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'a'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'nonexistent'

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    remove \
    --set-package-name 'a' \
    --enable-dry-run

if [ "$($java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'a' | jq -r '.result')" = "not-found" ]
then
    exit 1
fi

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    remove \
    --set-package-name 'a'

if [ "$($java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'a' | jq -r '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'b' | jq -r '.result')" != "package-found" ]
then
    exit 1
fi

if [ "$($java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'c' | jq -r '.result')" != "package-found" ]
then
    exit 1
fi

$java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.3.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'b' \
    -u 'c'

if $java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    remove \
    --set-package-name 'c'
then
    exit 1
fi

$java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    remove \
    --enable-cascade \
    --set-package-name 'c'
# TODO: THIS IS THE COMMAND THAT IS FAILING
if [ "$($java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'c' | jq -r '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'b' | jq -r '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    info \
    --set-package-name 'a' | jq -r '.result')" != "not-found" ]
then
    exit 1
fi

test "$($java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    remove \
    --enable-cascade \
    --set-package-name 'c' | jq -r '.result')" = "not-found"
