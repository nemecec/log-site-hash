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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LogSiteHashTest {

    @Test
    @DisplayName("Hash of the parts equals the known value, so existing hashes stay stable")
    void goldenValue() {
        UUID hash = LogSiteHash.of("this is a test message {}", "some.Class", 20000, "SOME_MARKER");
        assertThat(hash).isEqualTo(UUID.fromString("49cd2439-b38c-543a-5832-8233abc4a875"));
    }

    @Test
    @DisplayName("Null parts are written as the text null")
    void nullParts() {
        assertThat(LogSiteHash.of(null, null, null, null))
            .isEqualTo(DeterministicHashing.hash("nullnullnullnull"));
    }
}
