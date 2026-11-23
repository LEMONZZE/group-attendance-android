#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
mkdir -p .build
work=$(mktemp -d .build/test.XXXXXXXX)
trap 'rm -rf -- "$work"' EXIT
javac -encoding UTF-8 --release 8 -d "$work" \
    app/src/main/java/ru/groupjournal/attendance/AttendanceBook.java \
    tests/AttendanceBookTest.java
java -cp "$work" ru.groupjournal.attendance.AttendanceBookTest
