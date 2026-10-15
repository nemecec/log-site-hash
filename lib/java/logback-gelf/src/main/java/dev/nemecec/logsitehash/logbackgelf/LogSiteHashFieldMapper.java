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

package dev.nemecec.logsitehash.logbackgelf;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.nemecec.logsitehash.hashing.LogSiteHash;
import de.siegmar.logbackgelf.GelfFieldMapper;
import org.slf4j.Marker;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * A logback-gelf field mapper that adds two fields to every GELF message:
 * <ul>
 *   <li>{@code log_site_hash} - a deterministic UUID computed from the message template,
 *       logger name, log level, and marker</li>
 *   <li>{@code msg_template} - the raw message template before parameter substitution</li>
 * </ul>
 *
 * <p>Configure in {@code logback.xml}:
 * <pre>{@code
 * <fieldMapper class="dev.nemecec.logsitehash.logbackgelf.LogSiteHashFieldMapper">
 *   <logSiteHashFieldName>log_site_hash</logSiteHashFieldName>
 *   <messageTemplateFieldName>msg_template</messageTemplateFieldName>
 * </fieldMapper>
 * }</pre>
 */
public class LogSiteHashFieldMapper implements GelfFieldMapper<String> {

    private String logSiteHashFieldName = "log_site_hash";
    private String messageTemplateFieldName = "msg_template";

    @Override
    public void mapField(ILoggingEvent event, BiConsumer<String, String> valueHandler) {
        valueHandler.accept(logSiteHashFieldName, hash(event).toString());
        valueHandler.accept(messageTemplateFieldName, String.valueOf(event.getMessage()));
    }

    public UUID hash(ILoggingEvent event) {
        var optionalLevel = Optional.ofNullable(event.getLevel());
        var optionalMarker = Optional.ofNullable(event.getMarker());

        return LogSiteHash.of(
            event.getMessage(),
            event.getLoggerName(),
            optionalLevel.map(Level::toInt).orElse(null),
            optionalMarker.map(Marker::getName).orElse(null)
        );
    }

    public void setLogSiteHashFieldName(String logSiteHashFieldName) {
        this.logSiteHashFieldName = logSiteHashFieldName;
    }

    public void setMessageTemplateFieldName(String messageTemplateFieldName) {
        this.messageTemplateFieldName = messageTemplateFieldName;
    }
}
