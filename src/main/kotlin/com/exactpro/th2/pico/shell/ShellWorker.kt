/*
 * Copyright 2022-2023 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.pico.ComponentState
import com.exactpro.th2.pico.IWorker
import com.exactpro.th2.pico.LOGGER
import com.exactpro.th2.pico.State
import com.exactpro.th2.pico.configuration.BoxConfiguration
import mu.KotlinLogging
import java.io.File

abstract class ShellWorker(
    private val componentFolder: File,
    private val stateFolder: File,
    protected val boxConfig: BoxConfiguration
): IWorker {
    private lateinit var process: Process
    private val logger = KotlinLogging.logger(this::class.java.canonicalName)

    companion object {
        const val BEFORE_RESTART_INTERVAL = 5000L
    }

    override fun run() {
        while (!Thread.currentThread().isInterrupted) {
            startProcess()
            Thread.sleep(BEFORE_RESTART_INTERVAL)
        }
    }

    protected abstract fun buildCommand(): List<String>

    protected abstract fun updateEnvironment(env: MutableMap<String, String>)

    private fun startProcess() {
        val command = listOf("nohup", "bash", "-c", "`${buildCommand().joinToString(separator = " ")}`")
        logger.info { "Running command ${command.joinToString(" ")}" }
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(componentFolder)
        val env = processBuilder.environment()
        updateEnvironment(env)
        process = processBuilder.start()

        ComponentState(boxConfig.boxName, pid = process.pid()).also {
            LOGGER.info { "Started component: $it" }
            LOGGER.debug { "${it.componentName} component:  (env: $env)" }
            it.dumpState(stateFolder)
        }

        val exitCode = process.waitFor()
        if(exitCode != 0) {
            logger.error { "${boxConfig.boxName} script exited with non zero exit code. Restarting process." }
            process.destroy()
            ComponentState(boxConfig.boxName,State.RESTARTING, null).also {
                LOGGER.info { "Stopped component: $it" }
                it.dumpState(stateFolder)
            }
        }
    }

    override fun close() {
        logger.info { "Closing process for ${boxConfig.boxName} script." }
        if(this::process.isInitialized) process.destroy()
    }
}