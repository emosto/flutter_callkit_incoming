#!/bin/sh
set -eu
# Test the production state machine with XCTest on macOS, without Flutter or
# signing. CallKit/provider integration still requires an iOS build/device.
test_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
task_dir=$(mktemp -d "${TMPDIR:-/tmp}/callkit-answer-tests.XXXXXX")
trap 'rm -rf "$task_dir"' EXIT
mkdir -p "$task_dir/Sources/AnswerAudio" "$task_dir/Tests/AnswerAudioTests"
cp "$test_dir/../Classes/IncomingAnswerAudioGate.swift" "$task_dir/Sources/AnswerAudio/"
cp "$test_dir/IncomingAnswerAudioGateTests.swift" "$task_dir/Tests/AnswerAudioTests/"
cat > "$task_dir/Package.swift" <<'PACKAGE'
// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "AnswerAudio", targets: [
    .target(name: "AnswerAudio"),
    .testTarget(name: "AnswerAudioTests", dependencies: ["AnswerAudio"])
])
PACKAGE
swift test --package-path "$task_dir"
