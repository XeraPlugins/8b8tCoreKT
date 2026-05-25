#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NUVOTIFIER_DIR="$ROOT_DIR/libs/nuvotifier"
NUVOTIFIER_JAR="$ROOT_DIR/libs/nuvotifier.jar"

if [[ ! -x "$NUVOTIFIER_DIR/gradlew" ]]; then
  echo "Missing NuVotifier Gradle wrapper: $NUVOTIFIER_DIR/gradlew" >&2
  exit 1
fi

echo "Building NuVotifier..."
(
  cd "$NUVOTIFIER_DIR"
  ./gradlew :nuvotifier-universal:shadowJar
)

built_jar="$(find "$NUVOTIFIER_DIR/universal/build/libs" -maxdepth 1 -type f -name '*-dist.jar' | sort | tail -n 1)"
if [[ -z "$built_jar" ]]; then
  echo "NuVotifier build completed, but no universal dist jar was found." >&2
  exit 1
fi

cp "$built_jar" "$NUVOTIFIER_JAR"
echo "Copied $(basename "$built_jar") to libs/nuvotifier.jar"

if [[ "$#" -eq 0 ]]; then
  set -- -DskipTests package
fi

echo "Building 8b8tCore..."
cd "$ROOT_DIR"
mvn "$@"
