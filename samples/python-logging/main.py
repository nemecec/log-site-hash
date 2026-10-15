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

"""Sample: log site hashing with Python's standard logging module.

Demonstrates computing a deterministic hash from the call site
(pathname + lineno + levelno) and attaching it to every log record
via a logging.Filter on the handler.

A filter on a logger runs only for records that this logger creates, not for records
that child loggers pass up to it. A filter on the handler runs for every record that
the handler receives, so it also covers loggers created with logging.getLogger(__name__).
"""

import logging
import uuid

import mmh3


def murmur3_uuid(input_str: str) -> uuid.UUID:
    """Compute a deterministic UUID from a string using Murmur3 x64_128 with seed 0."""
    lo, hi = mmh3.mmh3_x64_128_utupledigest(input_str.encode("utf-8"), 0)
    return uuid.UUID(int=(hi << 64) | lo)


class LogSiteHashFilter(logging.Filter):
    """A logging filter that computes a log site hash and attaches it to the record.

    The hash is computed from:
    - pathname: path to the source file. The path is absolute, so the hash changes
      when the code runs from another directory
    - lineno: line number in the source file
    - levelno: numeric log level (DEBUG=10, INFO=20, WARNING=30, etc.)

    Uses Murmur3 x64_128 and outputs a UUID.
    """

    def filter(self, record):
        site_key = f"{record.pathname}:{record.lineno}:{record.levelno}"
        record.log_site_hash = str(murmur3_uuid(site_key))
        return True


logger = logging.getLogger("sample.app")


def process_items():
    """Simulate processing items -- all log calls from the same line share a hash."""
    for i in range(3):
        logger.info("Processing item %d", i)


def configure_logging():
    """Send all records to stderr with the hash in the format."""
    handler = logging.StreamHandler()
    # Attach the filter to the handler, not to a logger
    handler.addFilter(LogSiteHashFilter())
    handler.setFormatter(logging.Formatter(
        "%(levelname)s [site:%(log_site_hash)s] %(filename)s:%(lineno)d - %(message)s"
    ))
    logging.basicConfig(level=logging.INFO, handlers=[handler])


def main():
    configure_logging()

    # These three calls share the same file+line+level -> same hash
    process_items()

    # Different line -> different hash
    logger.info("Done processing")

    # Same line, different level -> different hash
    logger.info("Same line test"); logger.warning("Same line test")


if __name__ == "__main__":
    main()
