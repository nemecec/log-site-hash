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

//! Sample: log site hashing with OpenTelemetry.
//!
//! Demonstrates a custom `LogProcessor` that computes a log site hash
//! from the source location attributes that the bridge crates add with
//! `experimental_metadata_attributes`. `opentelemetry-appender-log` writes
//! `code.file.path` and `code.line.number`. `opentelemetry-appender-tracing`
//! writes `code.filepath` and `code.lineno`. The processor reads both name pairs.

use std::io::Cursor;
use std::time::Duration;

use opentelemetry::logs::{AnyValue, LogRecord, Severity};
use opentelemetry::{InstrumentationScope, Key};
use opentelemetry_sdk::error::OTelSdkResult;
use opentelemetry_sdk::logs::{LogProcessor, SdkLogRecord, SdkLoggerProvider, SimpleLogProcessor};
use uuid::Uuid;

/// A `LogProcessor` that computes a log site hash from code location attributes
/// and attaches it as `log_site_hash`.
#[derive(Debug)]
struct LogSiteHashProcessor;

impl LogProcessor for LogSiteHashProcessor {
    fn emit(&self, record: &mut SdkLogRecord, _scope: &InstrumentationScope) {
        let mut file: Option<String> = None;
        let mut line: Option<i64> = None;

        for (key, value) in record.attributes_iter() {
            match (key.as_str(), value) {
                ("code.file.path" | "code.filepath", AnyValue::String(s)) => file = Some(s.to_string()),
                ("code.line.number" | "code.lineno", AnyValue::Int(n)) => line = Some(*n),
                _ => {}
            }
        }

        let severity = record
            .severity_number()
            .unwrap_or(Severity::Info) as i32;

        let input = format!(
            "{}:{}:{}",
            file.as_deref().unwrap_or(""),
            line.unwrap_or(0),
            severity,
        );
        let hash = murmur3::murmur3_x64_128(&mut Cursor::new(input.as_bytes()), 0)
            .expect("murmur3 hash failed");
        let uuid = Uuid::from_u128(hash);

        record.add_attribute(
            Key::from_static_str("log_site_hash"),
            AnyValue::String(uuid.to_string().into()),
        );
    }

    fn force_flush(&self) -> OTelSdkResult {
        Ok(())
    }

    fn shutdown_with_timeout(&self, _timeout: Duration) -> OTelSdkResult {
        Ok(())
    }
}

fn main() {
    // Set up OTel pipeline: hash processor -> stdout exporter
    let stdout_exporter = opentelemetry_stdout::LogExporter::default();
    let provider = SdkLoggerProvider::builder()
        .with_log_processor(LogSiteHashProcessor)
        .with_log_processor(SimpleLogProcessor::new(stdout_exporter))
        .build();

    // Bridge the `log` crate to OTel (with experimental_metadata_attributes enabled)
    let bridge = opentelemetry_appender_log::OpenTelemetryLogBridge::new(&provider);
    log::set_boxed_logger(Box::new(bridge)).unwrap();
    log::set_max_level(log::LevelFilter::Info);

    // These three calls share the same file+line+level -> same hash
    for i in 0..3 {
        log::info!("Processing item {}", i);
    }

    // Different line -> different hash
    log::info!("Done processing");

    // Same line, different level -> different hash
    log::info!("Same line test"); log::warn!("Same line test");

    // Shut down the provider to flush all pending logs
    let _ = provider.shutdown();
}
