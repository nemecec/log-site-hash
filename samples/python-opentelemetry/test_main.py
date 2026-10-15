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

import logging
import uuid

from opentelemetry.sdk._logs import LoggerProvider
from opentelemetry.sdk._logs.export import InMemoryLogRecordExporter, SimpleLogRecordProcessor

from main import murmur3_uuid, LogSiteHashFilter, configure_logging, logger


class TestMurmur3Uuid:
    def test_same_input_produces_same_hash(self):
        h1 = murmur3_uuid("src/main.py:42:20")
        h2 = murmur3_uuid("src/main.py:42:20")
        assert h1 == h2

    def test_result_is_valid_uuid(self):
        h = murmur3_uuid("src/main.py:42:20")
        assert isinstance(h, uuid.UUID)

    def test_different_inputs_produce_different_hashes(self):
        h1 = murmur3_uuid("src/main.py:42:20")
        h2 = murmur3_uuid("src/main.py:99:20")
        assert h1 != h2


class TestLogSiteHashFilter:
    def test_filter_sets_uuid_attribute(self):
        import logging

        f = LogSiteHashFilter()
        record = logging.LogRecord(
            name="test", level=logging.INFO, pathname="src/main.py",
            lineno=42, msg="test", args=(), exc_info=None,
        )
        f.filter(record)
        uuid.UUID(record.log_site_hash)

    def test_same_location_same_hash(self):
        import logging

        f = LogSiteHashFilter()
        r1 = logging.LogRecord(
            name="test", level=logging.INFO, pathname="src/main.py",
            lineno=42, msg="A", args=(), exc_info=None,
        )
        r2 = logging.LogRecord(
            name="test", level=logging.INFO, pathname="src/main.py",
            lineno=42, msg="B", args=(), exc_info=None,
        )
        f.filter(r1)
        f.filter(r2)
        assert r1.log_site_hash == r2.log_site_hash


class TestPipeline:
    def test_otel_log_records_carry_the_hash(self):
        exporter = InMemoryLogRecordExporter()
        provider = LoggerProvider()
        provider.add_log_record_processor(SimpleLogRecordProcessor(exporter))
        root = logging.getLogger()
        saved_handlers, saved_level = root.handlers[:], root.level
        handler = configure_logging(provider)
        try:
            logger.info("Hello %s", "world")
            logging.getLogger("sample.other").info("From another logger")
        finally:
            root.removeHandler(handler)
            root.handlers[:] = saved_handlers
            root.setLevel(saved_level)
        records = exporter.get_finished_logs()
        assert len(records) == 2
        for record in records:
            uuid.UUID(record.log_record.attributes["log_site_hash"])
