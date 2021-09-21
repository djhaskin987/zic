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

java -jar \
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
if java -jar \
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

java -jar \
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

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependers \
    -k 'c'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependers \
    -k 'b'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependers \
    -k 'a'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'c'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'b'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'a'

java -jar \
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

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'a'

java -jar \
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

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'a'

java -jar \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    dependees \
    -k 'nonexistent'

java -jar \
    -Djavax.net.ssl.trustStore="test.keystore" \
    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
    remove \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --enable-dry-run
# TODO: Check that package a is still there, but that it looked like it got
# removed.

# Remove a.
#java -jar \
#    -Djavax.net.ssl.trustStore="test.keystore" \
#    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
#    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
#    remove \
#    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
#    --set-package-name 'a'

# TODO: Check that package a is NOT still there, but that b and c are still there
# removed.

# Add `a` back
#java -jar \
#    -Djavax.net.ssl.trustStore="test.keystore" \
#    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
#    target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar \
#    add \
#    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
#    --set-package-name 'a' \
#    --set-package-version 0.3.0 \
#    --set-package-location "https://djhaskin987.me:8443/a.zip" \
#    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
#    -u 'b' \
#    -u 'c'

# TODO: Try to remove c, but you can't.
# Then remove c, forced. Add it back.
# Then remove c, cascade. Make sure EVERYTHING is gone.
# Remove nonexistent, make sure what is returned makes sense.
