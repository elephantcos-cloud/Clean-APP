package com.shohan.cleanspace.shizuku

import java.util.concurrent.TimeUnit

/**
 * Runs INSIDE a privileged Shizuku process with shell-level (ADB) permission.
 * Must have a public no-argument constructor.
 */
class CacheServiceImpl : ICacheService.Stub() {

    // Fix: the timeout now actually works. Previously, `inputStream.readText()` was
    // called BEFORE `waitFor(30, SECONDS)` — readText() blocks until the process
    // closes its stdout (i.e. until it exits), so a hung command that never closes
    // its output would block forever and the line below it would never even run.
    // Now the stream is drained on a background thread while waitFor() enforces the
    // real 30s timeout on the JVM/OS level, independent of whether output is flowing.
    override fun runCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        outputBuilder.append(line).append('\n')
                    }
                } catch (_: Exception) {
                    // stream closed/destroyed — nothing more to read
                }
            }
            readerThread.isDaemon = true
            readerThread.start()

            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerThread.join(2_000)
                "ERROR: command timed out after 30s"
            } else {
                readerThread.join(2_000)
                outputBuilder.toString()
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}

