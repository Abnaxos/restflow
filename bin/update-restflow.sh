#!/bin/bash
set -e

BASE="$(realpath "$(dirname "$0")/..")"
TARGET="$(realpath "${1:-$(pwd)}")"

cd "$BASE"
./gradlew core:clean frontend:clean core:build frontend:build frontend:allLib

cd "$TARGET"
[[ -d lib ]] && rm -r lib
cp -r "$BASE/frontend/target/lib-all" lib
