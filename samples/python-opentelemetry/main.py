# Copyright 2026 Neeme Praks
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Sample: log site hashing with Python logging + OpenTelemetry.

Demonstrates two approaches:
1. A logging.Filter on the OTel LoggingHandler that computes the hash and sets it on the record.
   The handler forwards all non-reserved record attributes,
   so the hash appears automatically as an OTel log attribute.
   A filter on a logger would run only for records that this logger creates,
   so the filter belongs on the handler.
2. (Alternative) A custom LogRecordProcessor that reads code.file.path
   and code.line.number from the OTel log record and computes the hash.

This sample uses approach 1 (the simpler one) and exports to stdout.
"""

import logging
import uuid

import mmh3
from opentelemetry._logs import set_logger_provider
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import SimpleLogRecordProcessor, ConsoleLogRecordExporter


def murmur3_uuid(input_str: str) -> uuid.UUID:
    """Compute a deterministic UUID from a string using Murmur3 x64_128 with seed 0."""
    lo, hi = mmh3.mmh3_x64_128_utupledigest(input_str.encode("utf-8"), 0)
    return uuid.UUID(int=(hi << 64) | lo)


class LogSiteHashFilter(logging.Filter):
    """A logging filter that computes a log site hash and attaches it to the record."""

    def filter(self, record):
        site_key = f"{record.pathname}:{record.lineno}:{record.levelno}"
        record.log_site_hash = str(murmur3_uuid(site_key))
        return True


logger = logging.getLogger("sample.app")


def process_items():
    """Simulate processing items -- all log calls from the same line share a hash."""
    for i in range(3):
        logger.info("Processing item %d", i)


def configure_logging(logger_provider):
    """Send all records to the OTel pipeline, with the hash set on each record."""
    otel_handler = LoggingHandler(logger_provider=logger_provider)
    # Attach the filter to the handler -- sets log_site_hash on every record,
    # which the handler forwards as an attribute
    otel_handler.addFilter(LogSiteHashFilter())
    root_logger = logging.getLogger()
    root_logger.setLevel(logging.INFO)
    root_logger.addHandler(otel_handler)
    return otel_handler


def main():
    # Set up OTel logging pipeline: console exporter for demo
    logger_provider = LoggerProvider()
    logger_provider.add_log_record_processor(
        SimpleLogRecordProcessor(ConsoleLogRecordExporter())
    )
    set_logger_provider(logger_provider)
    configure_logging(logger_provider)

    # These three calls share the same file+line+level -> same hash
    process_items()

    # Different line -> different hash
    logger.info("Done processing")

    # Same line, different level -> different hash
    logger.info("Same line test"); logger.warning("Same line test")

    # Shut down the provider to flush pending logs
    logger_provider.shutdown()


if __name__ == "__main__":
    main()
