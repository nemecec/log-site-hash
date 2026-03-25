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

package dev.nemecec.logsitehash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicHashingTest {

    @Test
    @DisplayName("Same input produces the same UUID")
    void sameInput() {
        UUID hash1 = DeterministicHashing.hash("Hello world");
        UUID hash2 = DeterministicHashing.hash("Hello world");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Different input produces different UUIDs")
    void differentInput() {
        UUID hash1 = DeterministicHashing.hash("Hello world");
        UUID hash2 = DeterministicHashing.hash("Goodbye world");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Hash is stable across non-interned strings")
    void nonInternedStrings() {
        String a = new StringBuilder("test").append(" value").toString();
        String b = new StringBuilder("test").append(" value").toString();
        assertThat(a).isNotSameAs(b);
        assertThat(DeterministicHashing.hash(a)).isEqualTo(DeterministicHashing.hash(b));
    }
}
