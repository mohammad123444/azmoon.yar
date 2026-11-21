#!/bin/bash
# usage: build.sh outdir src1 src2 ...
LIB=/opt/gradle-8.14.3/lib
CP="$LIB/kotlin-compiler-embeddable-2.0.21.jar:$LIB/kotlin-stdlib-2.0.21.jar:$LIB/kotlin-script-runtime-2.0.21.jar:$LIB/kotlin-reflect-2.0.21.jar:$LIB/kotlin-daemon-embeddable-2.0.21.jar:$LIB/trove4j-1.0.20200330.jar:$LIB/annotations-24.0.1.jar:$LIB/kotlinx-coroutines-core-jvm-1.6.4.jar"
OUT=$1; shift
rm -rf "$OUT"; mkdir -p "$OUT"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn -jvm-target 17 \
  -cp "$LIB/kotlin-stdlib-2.0.21.jar:$LIB/gson-2.10.jar" -d "$OUT" "$@" 2>&1 | grep -v "^Picked up"
