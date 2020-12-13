#!/bin/sh

java -jar target/uberjar/zic-0.1.0-SNAPSHOT-standalone.jar add --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' --set-package-name 'a' --set-package-version 0.1.0 --set-package-location "https://djhaskin987.me:8443/a.zip"
