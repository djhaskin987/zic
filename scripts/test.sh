#!/bin/sh
set -ex

# https://docs.oracle.com/javadb/10.8.3.0/adminguide/cadminsslclient.html
# https://stackoverflow.com/questions/65376092/can-jvm-trust-a-self-signed-certificate-for-only-a-single-run
# This was run to create keystore
# keytool -import -file lighttpd-environment/server.crt -keystore test.keystore
# where server.crt was just the certificate half of the server PEM file
# password 'asdfasdf'

usage() {
    echo "Usage: test.sh [-h] [--tracing|--native]" >&2
    echo "  --tracing: Record runs for future use with native-image" >&2
    echo "  --native: Run using the natively-compiled executable" >&2
    echo "" >&2
    echo "This script pre-supposes graalvm installed and \`native-image\`," >&2
    echo "  \`java\`, and \`javac\` as commands originating from the" >&2
    echo "  GraalVM installation being targeted." >&2
    exit 128
}

if [ ! -f "project.clj" ]
then
    echo "This script must be run from the root of the project." >&2
    usage
fi

root_path=${PWD}
test_home="${root_path}"

name=$(${root_path}/scripts/name)
version=$(${root_path}/scripts/version)

cleanup_files() {

    rm -rf .${name}.mv.db
    rm -rf .staging
    mkdir -p .staging
    rm -rf a
    rm -rf c
    rm -rf changes
    rm -rf failure
}

cleanup() {
    set +x
    echo "Cleaning up..."
    set -x
    #cleanup_server
    #cleanup_repl
    cleanup_files
}

# https://unix.stackexchange.com/q/235582/9696
trap "exit 129" HUP
trap "exit 130" INT
trap "exit 143" TERM
trap cleanup EXIT

replexec() {
    set +x
    echo "+ zic ${@}" >&2
    arg='(zic.cli/run ['
    stdin_in_play=0
    cljenv="{$(env |
        sort |
        sed -e 's|^\([^=]\{1,\}\)=\(.*\)$|"\1" "\2"|g' \
            -e 's|\\|\\\\|g')}"

    for i in "${@}"
    do
        cleaned="$(echo "${i}" | sed -e 's|\\|\\\\|g' -e 's|"|\\"|g')"
        if [ "${cleaned}" = "-" ]
        then
            if [ "${stdin_in_play}" -eq 0 ]
            then
                input="$(cat | sed -e 's|\\|\\\\|g' -e 's|"|\\"|g')"
                echo "Input:" >&2
                echo "${input}" >&2
            fi
            stdin_in_play=1
        fi
        arg="${arg} \"${cleaned}\""
    done
    arg="${arg}] ${cljenv} {\"user.home\" \"${HOME}\" \"user.dir\" \"${PWD}\"})"
    if [ "${stdin_in_play}" -ne 0 ]
    then
        arg="(with-in-str \"${input}\" ${arg})"
    fi

    response=$(rep "${arg}")
    text=$(echo "${response}" | head -n -1)
    return=$(echo "${response}" | tail -n 1)
    printf '%s\n' "${text}"
    set -x
    test "${return}" -eq 0
}

#cleanup_server
cleanup_files

execmd=replexec
java='java'
first_java=
tracing=0
native=0
keystore="${root_path}/test/resources/test.keystore"

while [ -n "${1}" ]
do
    case "${1}" in
        --tracing)
            shift
            tracing=1
            native_image_config="${root_path}/META-INF/native-image"
            # https://www.graalvm.org/22.0/reference-manual/native-image/Agent/
            java="java -agentlib:native-image-agent=config-merge-dir=${native_image_config}/"

            # https://www.graalvm.org/22.0/reference-manual/native-image/Agent/
            # Make sure to run `gu install native-image` first or you will get an "so file
            # not found" error
            first_java="java -agentlib:native-image-agent=config-output-dir=${native_image_config}/"
            rm -rf "${native_image_config}"
            mkdir -p "${native_image_config}"
            args="-Djavax.net.ssl.trustStore=${keystore} -Djavax.net.ssl.trustStorePassword=asdfasdf -jar ${root_path}/target/${name}-${version}-standalone.jar"
            execmd="${java} ${args}"
            ;;
        --native)
            shift
            native=1
            execmd="${root_path}/target/native-image/${name}-${version}-standalone -Djavax.net.ssl.trustStore=${keystore} -Djavax.net.ssl.trustStorePassword=asdfasdf --enable-insecure"
            ;;
        -h|*)
            usage
            ;;
    esac
done

if [ -n "${first_java}" ]
then
    first_exe="${first_java} ${args}"
else
    first_exe="${execmd}"
    if [ ! -f "${root_path}/.nrepl-port" -a "${tracing}" -eq 0 -a "${native}" -eq 0 ]
    then
        echo "Please start a REPL." >&2
        exit 1
    fi
fi

# Run this first just in case there's a real problem before starting
# the lighttpd server, as a sort of smoke test
$first_exe init

#start_server="${root_path}/lighttpd-environment/lighttpd.exp"
#set +x
#echo "Starting server..."
#set -x
#${start_server} &
#echo "${!}" > "${root_path}/build/server-pid"
cd "${root_path}"
set +e
mkdir -p "${root_path}/build"
set -e
cd "${test_home}"



# OneCLI, for tracing
$execmd \
    --file-tar-valon "${root_path}/test/resources/tar-valon.yaml" \
    --yaml-places '["Two Rivers", "Gealdan", "Tar Valon"]' \
    --yaml-eyes '16' \
    --yaml-pocahontas '1607' \
    --yaml-fire 'false' \
    --add-places "The Dragon" \
    options

$execmd \
    options show

cat > "${test_home}/zic.json" <<ZIC
{
"one": {
"two": 238,
"three": 543
},
"zed": {
"a": true,
"b": false
}
}
ZIC
rm ${test_home}/zic.json

$execmd \
    options show

cat > "${test_home}/zic.yaml" <<ZIC
one:
  two: 238
  three: 543
zed:
  a: true
  b: false
ZIC

cat > "${test_home}/a.json" <<A
{
"afound": true
}
A
cat > "${test_home}/b.json" <<B
{
"bfound": true
}
B
ls ./zic.yaml

answer=$(ZIC_ITEM_ANONYMOUS_COWARD="I was never here" ZIC_LIST_CONFIG_FILES="${test_home}/a.json,${test_home}/b.json" ${execmd} options show --set-output-format json --add-config-files '-' --json-fart '123' <<ALSO
{
    "ifihadtodoitagain": "i would"
}
ALSO
)

expected='{"one":{"two":238,"three":543},"start-directory":"/home/djhaskin987/Development/src/zic","cascade":false,"anonymous-coward":"I was never here","db-connection-string":"jdbc:h2:file:/home/djhaskin987/Development/src/zic/.zic;AUTOCOMMIT=OFF","bfound":true,"output-format":"json","commands":["options","show"],"staging-path":"/home/djhaskin987/Development/src/zic/.staging","fart":123,"download-package":true,"ifihadtodoitagain":"i would","root-path":"/home/djhaskin987/Development/src/zic","zed":{"a":true,"b":false},"dry-run":false,"afound":true,"package-metadata":null,"lock-path":"/home/djhaskin987/Development/src/zic/.zic.lock"}'

if [ ! "${answer}" = "${expected}" ]
then
    echo "AAAAH"
    exit 1
fi

answer=$(ZIC_ITEM_ANONYMOUS_COWARD="I was never here" ZIC_LIST_CONFIG_FILES="./a.json,./b.json" ${execmd} options show --add-config-files '-' --yaml-fart '123' << ALSO
ifihadtodoitagain: i would
ALSO
)

expected='one:
  two: 238
  three: 543
start-directory: /home/djhaskin987/Development/src/zic
cascade: false
anonymous-coward: I was never here
db-connection-string: jdbc:h2:file:/home/djhaskin987/Development/src/zic/.zic;AUTOCOMMIT=OFF
bfound: true
output-format: yaml
commands:
 - options
 - show
staging-path: /home/djhaskin987/Development/src/zic/.staging
fart: 123
download-package: true
ifihadtodoitagain: i would
root-path: /home/djhaskin987/Development/src/zic
zed:
  a: true
  b: false
dry-run: false
afound: true
package-metadata: null
lock-path: /home/djhaskin987/Development/src/zic/.zic.lock'

if [ ! "${answer}" = "${expected}" ]
then
    echo "AAAAH"
    exit 1
fi


# And now back to your regularly scheduled program.
if $execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'verybad' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/verybad.zip"
then
    exit 1
fi

# Make sure the test DOES NOT replace existing files in the staging directory
# by putting a bogus file where a good one should be downloaded.
# BTW, because verybad was downloaded previously, the .staging directory
# SHOULD ALREADY BE THERE
# This also tests that the directory has been created.
touch .staging/a-0.1.0.zip

if $execmd \
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

if [ "${tracing}" -ne 0 ]
then
    java \
        -verbose:class \
        "-agentlib:native-image-agent=config-merge-dir=${native_image_config}/" \
        "-Djavax.net.ssl.trustStore=${keystore}" \
        "-Djavax.net.ssl.trustStorePassword=asdfasdf" \
        -jar "${root_path}/target/${name}-${version}-standalone.jar" \
        add \
        --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
        --set-package-name 'a' \
        --set-package-version 0.1.0 \
        --set-package-location "https://djhaskin987.me:8443/a.zip" \
        --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}' |
        awk '/^\[[^ ]* /{print $2}' |
        sed -e 's|\.[^.]*$||g' |
        sort -u > "${root_path}/build/loaded-packages"
else
    $execmd \
        add \
        --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
        --set-package-name 'a' \
        --set-package-version 0.1.0 \
        --set-package-location "https://djhaskin987.me:8443/a.zip" \
        --set-package-metadata '{"zic": {"config-files": ["a/poem.txt"], "ghost-files": ["a/log.txt"]}}'
fi

$execmd \
    verify \
    --set-package-name 'a'

sed -i  's/w/W/g' a/willows.txt

# If I change a normal file then verification fails
if $execmd \
    verify \
    --set-package-name 'a'
then
    exit 1
fi

# and back to normal
sed -i 's/W/w/g' a/willows.txt

$execmd \
    verify \
    --set-package-name 'a'

# I can change a config file without killing verification
sed -i  's/I/U/g' a/poem.txt

$execmd \
    verify \
    --set-package-name 'a'


rm -rf a/poem.txt
rm -rf a/willows.txt

# But if I remove a config file muster no longer passes
if $execmd \
    verify \
    --set-package-name 'a'
then
    exit 1
fi

$execmd \
    add \
    --set-package-name 'b' \
    --set-package-version 0.1.0 \
    --set-package-location "invalid,yetworks" \
    --set-package-metadata '{"for": "you"}' \
    --disable-download-package

$execmd \
    info \
    --set-package-name 'a'

$execmd \
    info \
    --set-package-name 'b'

$execmd \
    info \
    --set-package-name 'c' || :


# Test that config files put down before clean installation remain intact
mkdir c
echo 'I am NOT JUST an echo.' > "c/echo.txt"
touch c/echo.txt.c.0.1.0.new

# TODO: Test the case where config files listed are actually directories,
# whether on the file system or the package

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'c' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/c.zip" \
    --set-package-metadata '{"zic": {"config-files": ["c/echo.txt"]}}'

test "$(cat c/echo.txt)" = "I am NOT JUST an echo."
test -f "c/echo.txt"
test -f "c/echo.txt.c.0.1.0.new"
test -f "c/echo.txt.c.0.1.0.new.1"

if [ "$($execmd \
    files \
    --set-package-name a)" != 'result: package-found
package-files:
 -
  path: a/poem.txt
  size: 44
  file-class: config-file
  checksum: f5804ac61aa4b37500ea52077f984d7224a35d3e2d05644d62ac4940384cfa6e
 -
  path: a/willows.txt
  size: 16
  file-class: normal-file
  checksum: bf20da2e626d608e486160cad938868e588603cd91fa71b4b7f604a5a4e90dfd
 -
  path: a/log.txt
  size: 0
  file-class: ghost-file
  checksum: null' ]
then
    exit 1
fi

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --set-package-version 0.2.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.2.0.zip"

# Cannot upgrade from one package to one of equivalent version
if $execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --set-package-version 0.2.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.2.0.zip"
then
    exit 1
fi

#  "Option `allow-downgrades` is disabled and downgrade detected."
if $execmd \
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
$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --enable-allow-downgrades \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.1.0.zip"

$execmd \
    remove \
    --set-package-name 'failure'

# No normal files should exist anymore
if [ -n "$(find failure -type f)" ]
then
    exit 1
fi

# Clean slate for tests
rm -rf failure

$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'failure' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/failure-0.1.0.zip"

#  Cannot update: some directories in old package are not directories in new package.
#  (This isn't explicitly checked for; it should just blow up)
#if $execmd \
#    add \
#    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
#    --set-package-name 'failure' \
#    --set-package-version 0.2.0 \
#    --set-package-location "https://djhaskin987.me:8443/failure-0.2.0.zip"
#then
#    exit 1
#fi

$execmd \
    remove \
    --set-package-name 'failure'

# Clean slate for tests.
rm -rf changes
# File cases:
# used to be a config file, is now a ghost file
$execmd \
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
$execmd \
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

test -f changes/contig-config-diffsize-gone
test ! -f changes/contig-config-diffsize-gone.changes.0.1.0.backup
test ! -f changes/contig-config-diffsize-gone.changes.0.2.0.backup
test ! -f changes/contig-config-diffsize-gone.changes.0.2.0.new
test "$(cat changes/contig-config-diffsize)" = "contig config blue"

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
test -f changes/gone-to-config-edited.changes.0.2.0.new
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
$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'somethingelse' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/somethingelse-0.1.0.zip"

# TWO PACKAGES THAT OWN THE SAME FILE - GHOST VERSION (which is NOT okay)
if $execmd \
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
if $execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'alsosomethingelse' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/alsosomethingelse-0.1.0.zip"
then
    exit 1
fi

# Check that if I specify an extra config file, it gets ignored.
$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'extraconfigfile' \
    --set-package-version 0.1.0 \
    --set-package-metadata '{"zic": {"config-files": ["a/b"]}}' \
    --set-package-location "https://djhaskin987.me:8443/empty-0.1.0.zip"

if [ "$($execmd files --set-package-name 'extraconfigfile')" != 'result: package-found
package-files: []' ]
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
#$java -jar \
#    -Djavax.net.ssl.trustStore="${keystore}" \
#    -Djavax.net.ssl.trustStorePassword="asdfasdf" \
#    target/zic-0.1.0-SNAPSHOT-standalone.jar \
#    remove \
#    --set-package-name 'changes'
# AND CHECK THE FILES AGAIN

# [ ] TODO: CHECK package file conflicts on upgrade apply between ghost-file/normal file pairs


# Dependency tree testing

cleanup_files

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
    --set-package-name 'a' | yq '.result')" = "not-found" ]
then
    exit 1
fi

$execmd \
    remove \
    --set-package-name 'a'

if [ "$($execmd \
    info \
    --set-package-name 'a' | yq '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($execmd \
    info \
    --set-package-name 'b' | yq '.result')" != "package-found" ]
then
    exit 1
fi

if [ "$($execmd \
    info \
    --set-package-name 'c' | yq '.result')" != "package-found" ]
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
    --set-package-name 'c' | yq '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($execmd \
    info \
    --set-package-name 'b' | yq '.result')" != "not-found" ]
then
    exit 1
fi

if [ "$($execmd \
    info \
    --set-package-name 'a' | yq '.result')" != "not-found" ]
then
    exit 1
fi

test "$($execmd \
    remove \
    --enable-cascade \
    --set-package-name 'c' | yq '.result')" = "not-found"

# Cleanup that should only run if the script ran successfully
if [ "${tracing}" -ne 0 ]
then
    ed "${root_path}/META-INF/native-image/reflect-config.json" <<ED
/^\[
a
    {"name": "java.lang.reflect.AccessibleObject", "methods" : [{"name":"canAccess"}]},
.
w
q
ED
fi
