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

package dev.nemecec.logsitehash.hashing;

import java.util.UUID;

/**
 * Computes the log site hash from the parts that identify a log statement.
 * Every integration uses this composition, so the same parts always give the same UUID.
 * A {@code null} part is written as the text {@code null}.
 */
public class LogSiteHash {

    private LogSiteHash() {
    }

    public static UUID of(String messageTemplate, String loggerName, Integer level, String markerName) {
        return DeterministicHashing.hash(messageTemplate + loggerName + level + markerName);
    }
}
