#!/bin/sh
set -ex

usage() {
    echo "Usage: test.sh [-h] [--tracing|--native]" >&2
    echo "  --tracing: Record runs for future use with native-image" >&2
    echo "  --native: Run using the natively-compiled executable" >&2
    exit 1
}

# Dependency tree testing
rm -rf .zic.mv.db
rm -rf .zic.trace.db
rm -rf .staging
mkdir -p .staging
rm -rf a
rm -rf b
rm -rf c
rm -rf changes
rm -rf failure

name=$(lein print :name | sed 's|"||g')
version=$(lein print :version | sed 's|"||g')

execmd=
java='java'
while [ -n "${1}" ]
do
    case "${1}" in
        --tracing)
            shift
            # https://www.graalvm.org/22.0/reference-manual/native-image/Agent/
            java='java -agentlib:native-image-agent=config-merge-dir=META-INF/native-image/'
            ;;
        --native)
            shift
            execmd="./${name}-${version}-standalone -Djavax.net.ssl.trustStore=test.keystore -Djavax.net.ssl.trustStorePassword=asdfasdf"
            ;;
        -h|*)
            usage
            ;;
    esac
done

if [ -z "${execmd}" ]
then
    execmd="${java} -Djavax.net.ssl.trustStore=test.keystore -Djavax.net.ssl.trustStorePassword=asdfasdf -jar target/uberjar/${name}-${version}-standalone.jar"
fi

$execmd \
    init

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'c' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/c.zip"

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'b' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/b.zip" \
    --add-package-dependency 'c'

# Test that unmet dependencies stop installation.
if $execmd \
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

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'c'

# TODO: The output of these commands need to be checked manually at the moment
$execmd \
    dependers \
    -k 'c'

$execmd \
    dependers \
    -k 'b'

$execmd \
    dependers \
    -k 'a'

$execmd \
    dependees \
    -k 'c'

$execmd \
    dependees \
    -k 'b'

$execmd \
    dependees \
    -k 'a'

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.2.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'b'

$execmd \
    dependees \
    -k 'a'

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.3.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'b' \
    -u 'c'

$execmd \
    dependees \
    -k 'a'

$execmd \
    dependees \
    -k 'nonexistent'

$execmd \
    remove \
    --set-package-name 'a' \
    --enable-dry-run

if [ "$($execmd \
    info \
    --set-package-name 'a' | jq -r '.result')" = "not-found" ]
then
    exit 1
fi

$execmd \
    remove \
    --set-package-name 'a'

if [ "$($execmd \
    info \
    --set-package-name 'a' | jq -r '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($execmd \
    info \
    --set-package-name 'b' | jq -r '.result')" != "package-found" ]
then
    exit 1
fi

if [ "$($execmd \
    info \
    --set-package-name 'c' | jq -r '.result')" != "package-found" ]
then
    exit 1
fi

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'a' \
    --set-package-version 0.3.0 \
    --set-package-location "https://djhaskin987.me:8443/a.zip" \
    --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' \
    -u 'b' \
    -u 'c'

if $execmd \
    remove \
    --set-package-name 'c'
then
    exit 1
fi

$execmd \
    remove \
    --enable-cascade \
    --set-package-name 'c'
# TODO: THIS IS THE COMMAND THAT IS FAILING
if [ "$($execmd \
    info \
    --set-package-name 'c' | jq -r '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($execmd \
    info \
    --set-package-name 'b' | jq -r '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($execmd \
    info \
    --set-package-name 'a' | jq -r '.result')" != "not-found" ]
then
    exit 1
fi

test "$($execmd \
    remove \
    --enable-cascade \
    --set-package-name 'c' | jq -r '.result')" = "not-found"
