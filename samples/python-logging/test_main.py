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

import uuid

import io
import logging

from main import murmur3_uuid, LogSiteHashFilter, configure_logging, logger


class TestMurmur3Uuid:
    def test_same_input_produces_same_hash(self):
        h1 = murmur3_uuid("src/main.py:42:20")
        h2 = murmur3_uuid("src/main.py:42:20")
        assert h1 == h2

    def test_result_is_valid_uuid(self):
        h = murmur3_uuid("src/main.py:42:20")
        assert isinstance(h, uuid.UUID)
        # Round-trip through string
        assert h == uuid.UUID(str(h))

    def test_different_file_produces_different_hash(self):
        h1 = murmur3_uuid("src/main.py:42:20")
        h2 = murmur3_uuid("src/other.py:42:20")
        assert h1 != h2

    def test_different_line_produces_different_hash(self):
        h1 = murmur3_uuid("src/main.py:42:20")
        h2 = murmur3_uuid("src/main.py:99:20")
        assert h1 != h2

    def test_different_level_produces_different_hash(self):
        h1 = murmur3_uuid("src/main.py:42:20")
        h2 = murmur3_uuid("src/main.py:42:30")
        assert h1 != h2


class TestLogSiteHashFilter:
    def test_filter_sets_log_site_hash(self):
        import logging

        f = LogSiteHashFilter()
        record = logging.LogRecord(
            name="test", level=logging.INFO, pathname="src/main.py",
            lineno=42, msg="test", args=(), exc_info=None,
        )
        result = f.filter(record)
        assert result is True
        assert hasattr(record, "log_site_hash")
        # Should be a valid UUID string
        uuid.UUID(record.log_site_hash)

    def test_same_location_produces_same_hash(self):
        import logging

        f = LogSiteHashFilter()
        record1 = logging.LogRecord(
            name="test", level=logging.INFO, pathname="src/main.py",
            lineno=42, msg="message A", args=(), exc_info=None,
        )
        record2 = logging.LogRecord(
            name="test", level=logging.INFO, pathname="src/main.py",
            lineno=42, msg="message B", args=(), exc_info=None,
        )
        f.filter(record1)
        f.filter(record2)
        assert record1.log_site_hash == record2.log_site_hash

    def test_different_location_produces_different_hash(self):
        import logging

        f = LogSiteHashFilter()
        record1 = logging.LogRecord(
            name="test", level=logging.INFO, pathname="src/main.py",
            lineno=42, msg="test", args=(), exc_info=None,
        )
        record2 = logging.LogRecord(
            name="test", level=logging.INFO, pathname="src/main.py",
            lineno=99, msg="test", args=(), exc_info=None,
        )
        f.filter(record1)
        f.filter(record2)
        assert record1.log_site_hash != record2.log_site_hash


class TestConfigureLogging:
    def test_child_logger_records_carry_the_hash(self, monkeypatch):
        stream = io.StringIO()
        root = logging.getLogger()
        saved_handlers, saved_level = root.handlers[:], root.level
        root.handlers.clear()
        monkeypatch.setattr("sys.stderr", stream)
        try:
            configure_logging()
            logger.info("Hello %s", "world")
            logging.getLogger("sample.other").info("From another logger")
        finally:
            root.handlers[:] = saved_handlers
            root.setLevel(saved_level)
        lines = stream.getvalue().splitlines()
        assert len(lines) == 2
        for line in lines:
            site = line.split("[site:")[1].split("]")[0]
            uuid.UUID(site)
