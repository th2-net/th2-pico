/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.pico.shell

import com.exactpro.th2.pico.IWorker
import com.exactpro.th2.pico.LOGGER
import com.exactpro.th2.pico.configuration.BoxConfiguration
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executor

class ShellWorker(
    private val path: File,
    private val componentFolder: File,
    private val args: Array<String>,
    private val boxConfig: BoxConfiguration
): IWorker {
    private lateinit var process: Process
    companion object {
        private val logger = KotlinLogging.logger {  }
        private const val BEFORE_RESTART_INTERVAL = 5000L
        private const val MAX_HEAP_OPTION = "-Xmx"
        private const val JAVA_OPTS = "JAVA_OPTS"
    }

    override fun run() {
        while (true) {
            startProcess()
            Thread.sleep(BEFORE_RESTART_INTERVAL)
        }
    }

    private fun startProcess() {
        val command = listOf("nohup", "bash", "-c") + listOf("`${(listOf(path.absolutePath) + args).joinToString(" ")}`")
        logger.info { "Running command ${command.joinToString(" ")}" }
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(componentFolder)
        val env = processBuilder.environment()
        env[JAVA_OPTS] = MAX_HEAP_OPTION + boxConfig.memoryLimit.removeSuffix("i")
        process = processBuilder.start()

        val exitCode = process.waitFor()
        if(exitCode != 0) {
            logger.error { "${boxConfig.boxName} script exited with non zero exit code. Restarting process." }
            process.destroy()
        }
    }

    override fun close() {
        logger.info { "Closing process for ${boxConfig.boxName} script." }
        if(this::process.isInitialized) process.destroy()
    }
}