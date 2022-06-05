.POSIX:
.PHONY: all clean test test-native tracing jar

name=$(shell scripts/name)
version=$(shell scripts/version)
tracing_config_files=META-INF/native-image/jni-config.json META-INF/native-image/proxy-config.json META-INF/native-image/reflect-config.json META-INF/native-image/resource-config.json META-INF/native-image/serialization-config.json
target_dir=target
native_dir=$(target_dir)/native-image
jar_dir=$(target_dir)/uberjar
root_target_name=$(name)-$(version)-standalone
jar_fname=$(root_target_name).jar
native_fname=$(root_target_name)
jar_file=$(jar_dir)/$(jar_fname)
native_file=$(native_dir)/$(native_fname)
build_native_script=scripts/build-native-image
sources=src/zic/*.clj project.clj
test_script=scripts/test.sh
lein=lein

jar: $(jar_file)

clean:
	- rm -rf build
	- rm -rf META-INF
	- rm -rf test/resources/data/all
	- rm -rf target/

all: $(jar_file) $(native_file)

test: $(jar_file) $(test_script)
	$(test_script)

test-native: $(native_file) $(test_script)
	$(lein) test
	$(test_script) --native

tracing: $(tracing_config_files)

$(jar_file): $(sources)
	$(lein) test
	$(lein) uberjar

$(tracing_config_files): $(jar_file) $(test_script)
	$(test_script) --tracing

$(native_file): $(build_native_script) $(jar_file) $(tracing_config_files)
	$(build_native_script)
	mkdir -p $(native_dir)
	mv $(native_fname)* $(native_dir)