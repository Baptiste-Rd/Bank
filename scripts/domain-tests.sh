#!/bin/bash
# Tests du module :domain sans Gradle (le téléchargement de Gradle est bloqué dans certains
# environnements) : compile le domaine et ses tests avec le compilateur Kotlin livré avec Gradle,
# puis lance chaque méthode @Test avec un petit lanceur (scripts/domain-tests/).
# Usage : ./scripts/domain-tests.sh   → « N passed, M failed » et les échecs.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
L=${GRADLE_LIB:-/opt/gradle/lib}
OUT=$(mktemp -d)
CP=$(ls $L/kotlin-compiler-embeddable-*.jar $L/kotlin-stdlib-*.jar $L/kotlin-reflect-*.jar $L/kotlin-script-runtime-*.jar $L/kotlin-daemon-embeddable-*.jar $L/trove4j*.jar $L/annotations-*.jar $L/kotlinx-coroutines-core-jvm-*.jar 2>/dev/null | tr '\n' ':')
STD=$(ls $L/kotlin-stdlib-2*.jar | head -1)
mkdir -p "$OUT/stub" "$OUT/domain"
javac -d "$OUT/stub" $(find "$ROOT/scripts/domain-tests" -name "*.java")
cd "$ROOT"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$STD:$OUT/stub" -d "$OUT/domain" \
  $(find domain/src/main domain/src/test -name "*.kt") 2>&1 | grep -v "^warning\|JAVA_TOOL" | head -30
java -cp "$STD:$OUT/stub:$OUT/domain" runner.Run "$OUT/domain" 2>&1 | grep -v JAVA_TOOL | tail -15
