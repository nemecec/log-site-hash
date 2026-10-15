// Copyright 2026 Neeme Praks
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use std::process::Command;

#[test]
fn log_site_hash_groups_same_line_messages() {
    let output = Command::new(env!("CARGO_BIN_EXE_log-site-hash-rust-otel-sample"))
        .output()
        .expect("failed to run sample binary");

    let stdout = String::from_utf8_lossy(&output.stdout);

    // Extract all log_site_hash values from the output
    let hashes: Vec<&str> = stdout
        .lines()
        .filter_map(|line| {
            let trimmed = line.trim();
            if trimmed.starts_with("->  log_site_hash:") {
                // Format: ->  log_site_hash: String(Owned("uuid-here"))
                let start = trimmed.find('"')? + 1;
                let end = trimmed.rfind('"')?;
                Some(&trimmed[start..end])
            } else {
                None
            }
        })
        .collect();

    assert_eq!(hashes.len(), 6, "expected 6 log messages, got {}: {:?}", hashes.len(), hashes);

    // First three messages (same line in a loop) should have the same hash
    assert_eq!(hashes[0], hashes[1], "messages 0 and 1 should share a hash");
    assert_eq!(hashes[1], hashes[2], "messages 1 and 2 should share a hash");

    // Fourth message (different line) should have a different hash
    assert_ne!(hashes[2], hashes[3], "message 3 should have a different hash (different line)");

    // Fifth and sixth messages are on the same line but different levels -> different hashes
    assert_ne!(hashes[4], hashes[5], "same line, different level should produce different hash");

    // All hashes should look like UUIDs (8-4-4-4-12 format)
    for hash in &hashes {
        assert_eq!(hash.len(), 36, "hash should be UUID format: {}", hash);
        assert_eq!(hash.chars().filter(|c| *c == '-').count(), 4, "UUID should have 4 dashes: {}", hash);
    }
}
