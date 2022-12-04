package com.exactpro.th2.pico.shell

import com.exactpro.th2.pico.IWorker
import com.exactpro.th2.pico.LOGGER
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executor

class ShellWorker(
    private val path: File,
    private val componentFolder: File,
    private val args: Array<String>,
    private val componentName: String
): IWorker {
    private lateinit var process: Process
    companion object {
        private val logger = KotlinLogging.logger {  }
        private const val BEFORE_RESTART_INTERVAL = 5000L
    }

    override fun run() {
        while (true) {
            startProcess()
            Thread.sleep(BEFORE_RESTART_INTERVAL)
        }
    }

    private fun startProcess() {
        val processBuilder = ProcessBuilder(listOf(path.absolutePath) + args)
        processBuilder.directory(componentFolder)
        process = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(process.errorStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            logger.error { "$componentName: $line" }
        }

        val exitCode = process.waitFor()
        if(exitCode != 0) {
            logger.error { "$componentName script exited with non zero exit code. Restarting process." }
            process.destroy()
        }
    }

    override fun close() {
        logger.info { "Closing process for $componentName script." }
        if(this::process.isInitialized) process.destroy()
    }
}