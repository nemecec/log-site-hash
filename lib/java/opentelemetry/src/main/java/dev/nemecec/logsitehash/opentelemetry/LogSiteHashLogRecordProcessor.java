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

package dev.nemecec.logsitehash.opentelemetry;

import dev.nemecec.logsitehash.hashing.LogSiteHash;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

/**
 * An OpenTelemetry {@link LogRecordProcessor} that computes a log site hash
 * and attaches it as the {@code log_site_hash} attribute to each log record.
 *
 * <p>The hash is computed from the log site identity, the severity number and the
 * logger name (the instrumentation scope name). The log site identity is the first of:
 * <ol>
 *   <li>the {@code log.body.template} attribute. The OpenTelemetry Logback appender sets it
 *       with {@code captureTemplate=true}, but only for messages that have arguments.</li>
 *   <li>the source location from the {@code code.file.path} and {@code code.line.number}
 *       attributes. The appender sets them with {@code captureCodeAttributes=true}.
 *       Use this for kotlin-logging lambdas, because they carry no arguments and so no template.</li>
 *   <li>the log record body. For a message without arguments, the body is the template.</li>
 * </ol>
 *
 * <p>The hash of a log record differs from the hash that {@code LogSiteHashFieldMapper}
 * computes for the same statement, because the severity numbers differ and this processor has no marker.
 */
public class LogSiteHashLogRecordProcessor implements LogRecordProcessor {

    static final AttributeKey<String> LOG_BODY_TEMPLATE =
            AttributeKey.stringKey("log.body.template");

    static final AttributeKey<String> CODE_FILE_PATH =
            AttributeKey.stringKey("code.file.path");

    static final AttributeKey<Long> CODE_LINE_NUMBER =
            AttributeKey.longKey("code.line.number");

    static final AttributeKey<String> LOG_SITE_HASH =
            AttributeKey.stringKey("log_site_hash");

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
        var severity = logRecord.getSeverity();
        var loggerName = logRecord.getInstrumentationScopeInfo().getName();
        var siteHash = LogSiteHash.of(
                logSiteIdentity(logRecord),
                loggerName,
                severity == null ? null : severity.getSeverityNumber(),
                null);

        logRecord.setAttribute(LOG_SITE_HASH, siteHash.toString());
    }

    private static String logSiteIdentity(ReadWriteLogRecord logRecord) {
        var template = logRecord.getAttribute(LOG_BODY_TEMPLATE);
        if (template != null) {
            return template;
        }
        var filePath = logRecord.getAttribute(CODE_FILE_PATH);
        var lineNumber = logRecord.getAttribute(CODE_LINE_NUMBER);
        if (filePath != null && lineNumber != null) {
            return filePath + ":" + lineNumber;
        }
        var body = logRecord.getBodyValue();
        return body == null ? null : body.asString();
    }
}
