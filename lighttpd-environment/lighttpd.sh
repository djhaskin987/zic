#!/bin/sh
# https://www.cyberciti.biz/tips/howto-lighttpd-create-self-signed-ssl-certificates.html
# https://redmine.lighttpd.net/projects/lighttpd/wiki/Docs_SSL
# https://redmine.lighttpd.net/projects/1/wiki/HowToBasicAuth

set -ex
root_path="${PWD}"


if [ ! -e "${root_path}/project.clj" ]
then
    echo "This script must be run from the root of the project." >&2
    exit 1
fi

testing_path="${root_path}/lighttpd-environment"

cd "${testing_path}"

rm -rf lighttpd
mkdir -p lighttpd

# Make self-signed certificate
#openssl req -x509 -newkey rsa:4096 -keyout server.pem -out server.pem -days 365
cp server.pem lighttpd
config_file="${testing_path}/lighttpd/lighttpd.conf"
auth_file="${testing_path}/lighttpd/auth.conf"
cat > "${auth_file}" << AUTH
mode:code
AUTH

cat > "${config_file}" << CONF
var.confdir = "${testing_path}/lighttpd"
server.modules += ("mod_auth", "mod_authn_file", "mod_openssl")
server.username = "$(id -un)"
server.groupname = "$(id -gn)"
server.port = 8443
server.dir-listing = "enable"
ssl.engine = "enable"
ssl.pemfile = var.confdir + "/server.pem"
server.name = "djhaskin987.me"
server.document-root = "${testing_path}/wwwroot"
auth.backend = "plain"
auth.backend.plain.userfile = "${auth_file}"
# insecure location; temporary; FIX to something better
auth.require = ( "" => ("method" => "basic", "realm" => "djhaskin987.me", "require" => "valid-user") )
CONF

rm -rf wwwroot
mkdir wwwroot

rm -rf test-data
mkdir test-data
cd test-data

mkdir c
mkdir b
mkdir a
mkdir b/d
echo 'Hopscotch.' > b/d/hoarcrux.txt
echo 'Fie on goodness! Fie! Fie! Fie! Fie!' > b/fie.txt
echo 'I can'\''t stop this feeling deep inside of me' > a/poem.txt
echo 'The wind in the' > a/willows.txt

# Cannot upgrade from one package to one of equivalent version
#  "Option `allow-downgrades` is disabled and downgrade detected."
#  "Option `allow-downgrades` is enabled and downgrade detected."
#  "Cannot update: some directories in old package are not directories in new package."
rm -rf failure
mkdir failure
mkdir failure/directory-to-normal
echo 'used to be normal, now a directory' > failure/directory-to-normal/normal
zip -r "${testing_path}/wwwroot/failure-0.1.0.zip" failure
rm -rf failure
mkdir failure
echo 'used to be normal, now a directory' > failure/directory-to-normal
zip -r "${testing_path}/wwwroot/failure-0.2.0.zip" failure

#echo 'used to be config, now a ghost' > d/config-to-ghost.txt
#
#
#mkdir upgrade-main
#echo 'used to be config, now a ghost' > upgrade-main/config-to-ghost
#echo 'used to be normal, now a directory' > upgrade-main/normal-to-directory
#
#mkdir upgrade-main
#echo 'used to be ghost, now a ghost'
# New testcases
# File cases:
# used to be a config file, is now a ghost file
# used to be a normal file, is now a directory
# used to be a ghost file, is now a ghost file
# used to be a ghost file, now doesn't exist in package
# used to be a normal file, now doesn't exist in package
# used to be a config file, now doesn't exist in package
# used to be a normal file, is now a normal file, same contents
# used to be a normal file, is now a normal file, different contents
# used to be a config file, is now a config file (contiguous config): same contents
# used to be a config file, is now a config file (contiguous config): different contents
# used to be a config file, is now a config file (contiguous config): same contents, but edited in between
# used to be a config file, is now a config file (contiguous config): different contents, but edited in between
# used to be a config file, is now a config file (contiguous config): same contents, but gone in between
# used to be a config file, is now a config file (contiguous config): different contents, but gone in between
# Used to be a directory, now doesn't exist
# used to be a non-empty directory, now doesn't exist
# Check that files check out
# Check for backups of config,s and .new's

zip -r "${testing_path}/wwwroot/a.zip" a
zip -r "${testing_path}/wwwroot/b.zip" b
zip -r "${testing_path}/wwwroot/c.zip" c
cd "${testing_path}/"
./new-verybad.bb

cd "${testing_path}"

lighttpd -f "${config_file}" -D
