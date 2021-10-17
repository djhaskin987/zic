#!/usr/bin/env bb

# https://www.cyberciti.biz/tips/howto-lighttpd-create-self-signed-ssl-certificates.html
# https://redmine.lighttpd.net/projects/lighttpd/wiki/Docs_SSL
# https://redmine.lighttpd.net/projects/1/wiki/HowToBasicAuth

(require '[clojure.java.shell :as shell])
(require '[clojure.string :as string])
(require '[clojure.java.io :as io])

(defn walk [file func]
    (when (.isDirectory file)
        (doseq [subfile (.listFiles file)]
          (walk subfile func)))
    (func file))

(def root-path (System/getProperty "user.dir"))
(def sep (System/getProperty "path.separator"))

(when (not (.exists (io/as-file (string/join "/" [root-path "project.clj"))))
 (binding [*out* *err*]
  (println "This script must be run from the root of the project."))
 (System/exit 1))

(def testing-path (string/join sep [root-path "lighttpd-environment"]))
(let [ltesting-path (io/as-file (string/join "/" [testing-path "lighttpd"]))]
 (walk ltesting-path io/delete-file)
 (.mkdirs ltesting-path))
(io/copy (io/as-file (string/join sep [testing-path "server.pem"]))
         (io/as-file (string/join sep 

/iog
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

zip -r "${testing_path}/wwwroot/a.zip" a
zip -r "${testing_path}/wwwroot/b.zip" b
zip -r "${testing_path}/wwwroot/c.zip" c
cd "${testing_path}/"
./new-verybad.bb

cd "${testing_path}"

lighttpd -f "${config_file}" -D
