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

import com.sangupta.murmur.Murmur3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Produces deterministic UUIDs from string input using Murmur3 hash.
 * The same input always produces the same UUID, making it suitable
 * for generating stable log site identifiers.
 *
 * <p>Depends on: {@code com.sangupta:murmur}
 */
public class DeterministicHashing {

    private DeterministicHashing() {
    }

    public static UUID hash(String from) {
        // NB! Murmur3.hash_x64_128 modifies the byte array during execution,
        // so we rely on getBytes() returning a fresh array each time.
        byte[] byteArray = from.getBytes(StandardCharsets.UTF_8);
        long[] hash = Murmur3.hash_x64_128(byteArray, byteArray.length, 0);
        return new UUID(hash[0], hash[1]);
    }
}
