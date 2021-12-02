cmake -G "MSYS Makefiles" -S zipline/src/jvmMain/ -B build/jni/ -DQUICKJS_VERSION="$(cat zipline/native/quickjs/VERSION)"
cmake --build build/jni/ --verbose
mkdir -p zipline/src/jvmMain/resources/
cp -v build/jni/libquickjs.* zipline/src/jvmMain/resources/
./gradlew zipline:assemble
