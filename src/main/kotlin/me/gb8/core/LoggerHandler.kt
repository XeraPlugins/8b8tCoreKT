/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core

import java.util.logging.Handler
import java.util.logging.LogRecord

class LoggerHandler : Handler() {
    override fun publish(logRecord: LogRecord) {
        val ogMessage = logRecord.message
        if (ogMessage.indexOf(Character.MIN_VALUE) != -1) {
            val raw = ogMessage.split(Character.MIN_VALUE.toString())
            val message = raw[0]
            val rawCallerName = raw[1].split("\\.")
            val callerName = rawCallerName[rawCallerName.size - 1]
            logRecord.message = message
            logRecord.loggerName = "%s/%s".format(logRecord.loggerName, callerName)
        }
    }

    override fun flush() {
    }

    override fun close() {
    }
}
