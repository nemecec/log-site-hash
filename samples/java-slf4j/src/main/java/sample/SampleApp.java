/*
 * Copyright 2026 Neeme Praks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample application demonstrating log site hashing with SLF4J + Logback.
 *
 * <p>The {@link dev.nemecec.logsitehash.logbackgelf.LogSiteHashFieldMapper} (configured in
 * {@code logback.xml}) computes a hash from the message template, logger name, level, and marker.
 * Messages with different parameter values but the same template get the same hash.
 *
 * <p>This sample outputs GELF-formatted JSON to the console so you can see the
 * {@code log_site_hash} and {@code msg_template} fields in action.
 */
public class SampleApp {

    private static final Logger log = LoggerFactory.getLogger(SampleApp.class);

    public static void main(String[] args) {
        // These three calls share the same template -> same hash
        for (int i = 0; i < 3; i++) {
            log.info("Processing item {}", i);
        }

        // Different template -> different hash
        log.info("Done processing");

        // Different level, same template -> different hash
        log.warn("Done processing");
    }
}
