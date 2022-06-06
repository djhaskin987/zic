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
mkdir empty
echo 'I am an echo.' > "c/echo.txt"
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

zip -r "${testing_path}/wwwroot/empty-0.1.0.zip" empty
rm -rf changes1
rm -rf changes2
rm -rf changes3
rm -rf changes4
mkdir changes1
mkdir changes2
mkdir changes3
mkdir changes4
mkdir changes1/samedir
mkdir changes2/samedir
mkdir changes3/samedir
mkdir changes4/samedir


# Two packages that share the same directory
echo 'samedir' > changes1/samedir/changes
echo 'samedir' > changes2/samedir/changes
echo 'samedir' > changes3/samedir/somethingelse
echo 'samefile' > changes4/samedir/somethingelse

# [ ] used to be a config file, is now a ghost file
echo 'config to ghost' > changes1/config-to-ghost
# [ ] used to be a ghost file, is now a ghost file
# [ ] used to be a ghost file, now doesn't exist in package

# used to be a normal file, now doesn't exist in package
echo 'normal to gone' > changes1/normal-to-gone

# [ ] used to be a config file, now doesn't exist in package
echo 'config to gone' > changes1/config-to-gone


# used to be a normal file, is now a normal file, same contents
echo 'normal to normal same' > changes1/normal-to-normal-same
echo 'normal to normal same' > changes2/normal-to-normal-same

# gone to normal
echo 'gone to normal' > changes2/gone-to-normal

# gone to config
echo 'gone to config' > changes2/gone-to-config

# gone to config, edited in between
echo 'gone to config' > changes2/gone-to-config-edited

# used to be a normal file, is now a normal file, different contents
echo 'normal to normal red' > changes1/normal-to-normal-different
echo 'normal to normal blue' > changes2/normal-to-normal-different

# used to be a config file, is now a config file (contiguous config): same contents
echo 'contig config same' > changes1/contig-config-same
echo 'contig config same' > changes2/contig-config-same

# used to be a config file, is now a config file (contiguous config): different size
echo 'contig config red' > changes1/contig-config-diffsize
echo 'contig config blue' > changes2/contig-config-diffsize

# used to be a config file, is now a config file (contiguous config): different sum
echo 'contig config grey' > changes1/contig-config-diffsum
echo 'contig config blue' > changes2/contig-config-diffsum

# used to be a config file, is now a config file (contiguous config): same contents, but edited in between
echo 'contig config same' > changes1/contig-config-same-edited
echo 'contig config same' > changes2/contig-config-same-edited

# used to be a config file, is now a config file (contiguous config): different contents, but edited in between
echo 'contig config grey' > changes1/contig-config-diffsum-edited
echo 'contig config blue' > changes2/contig-config-diffsum-edited

# used to be a config file, is now a config file (contiguous config): same contents, but gone in between
echo 'contig config same' > changes1/contig-config-same-gone
echo 'contig config same' > changes2/contig-config-same-gone

# used to be a config file, is now a config file (contiguous config): different contents, but gone in between
echo 'contig config red' > changes1/contig-config-diffsize-gone
echo 'contig config blue' > changes2/contig-config-diffsize-gone

cp -af changes1 changes
zip -r "${testing_path}/wwwroot/changes-0.1.0.zip" changes
rm -rf changes
cp -af changes2 changes

zip -r "${testing_path}/wwwroot/changes-0.2.0.zip" changes
rm -rf changes
cp -af changes3 changes
zip -r "${testing_path}/wwwroot/somethingelse-0.1.0.zip" changes
rm -rf changes
cp -af changes4 changes
zip -r "${testing_path}/wwwroot/alsosomethingelse-0.1.0.zip" changes

zip -r "${testing_path}/wwwroot/a.zip" a
zip -r "${testing_path}/wwwroot/b.zip" b
zip -r "${testing_path}/wwwroot/c.zip" c

cd "${testing_path}/"
./new-verybad.bb

cd "${testing_path}"

mkdir -p ${root_path}/build/
lighttpd -f "${config_file}" -D
