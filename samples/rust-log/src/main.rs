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

//! Sample: log site hashing with the `log` crate and `env_logger`.
//!
//! Demonstrates computing a deterministic hash from the call site
//! (file + line + level) and including it in the log output.

use std::io::{Cursor, Write};

use log::Record;
use uuid::Uuid;

/// Compute a deterministic Murmur3-based UUID from the log call site.
fn log_site_hash(record: &Record) -> Uuid {
    compute_hash(
        record.file().unwrap_or(""),
        record.line().unwrap_or(0),
        record.level() as usize,
    )
}

/// Compute a deterministic Murmur3-based UUID from raw inputs.
fn compute_hash(file: &str, line: u32, level: usize) -> Uuid {
    let input = format!("{file}:{line}:{level}");
    let hash = murmur3::murmur3_x64_128(&mut Cursor::new(input.as_bytes()), 0)
        .expect("murmur3 hash failed");
    Uuid::from_u128(hash)
}

fn main() {
    env_logger::Builder::from_default_env()
        .filter_level(log::LevelFilter::Info)
        .format(|buf, record| {
            let hash = log_site_hash(record);
            writeln!(
                buf,
                "[{level}] [site:{hash}] {file}:{line} - {msg}",
                level = record.level(),
                hash = hash,
                file = record.file().unwrap_or("<unknown>"),
                line = record.line().unwrap_or(0),
                msg = record.args(),
            )
        })
        .init();

    // These three calls produce different formatted messages but the SAME hash
    // (same file, same line, same level):
    for i in 0..3 {
        log::info!("Processing item {}", i);
    }

    // Different line -> different hash
    log::info!("Done processing");

    // Same line, different level -> different hash
    log::info!("Same line test"); log::warn!("Same line test");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn same_inputs_produce_same_hash() {
        let h1 = compute_hash("src/main.rs", 42, log::Level::Info as usize);
        let h2 = compute_hash("src/main.rs", 42, log::Level::Info as usize);
        assert_eq!(h1, h2);
    }

    #[test]
    fn hash_is_valid_uuid() {
        let h = compute_hash("src/main.rs", 42, log::Level::Info as usize);
        // Should parse back as a UUID
        assert_eq!(h, h.to_string().parse::<Uuid>().unwrap());
    }

    #[test]
    fn different_file_produces_different_hash() {
        let h1 = compute_hash("src/main.rs", 42, log::Level::Info as usize);
        let h2 = compute_hash("src/other.rs", 42, log::Level::Info as usize);
        assert_ne!(h1, h2);
    }

    #[test]
    fn different_line_produces_different_hash() {
        let h1 = compute_hash("src/main.rs", 42, log::Level::Info as usize);
        let h2 = compute_hash("src/main.rs", 99, log::Level::Info as usize);
        assert_ne!(h1, h2);
    }

    #[test]
    fn different_level_produces_different_hash() {
        let h1 = compute_hash("src/main.rs", 42, log::Level::Info as usize);
        let h2 = compute_hash("src/main.rs", 42, log::Level::Warn as usize);
        assert_ne!(h1, h2);
    }
}
