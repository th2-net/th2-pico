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
import mu.KLogger
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.streams.toList

abstract class ShellWorker(
    private val componentFolder: File,
    private val stateFolder: File,
    protected val boxConfig: BoxConfiguration
): IWorker {
    private val lock = ReentrantReadWriteLock()
    private lateinit var process: Process
    private val logger = KotlinLogging.logger(this::class.java.canonicalName)

    override val name: String
        get() = boxConfig.boxName

    companion object {
        private const val CLOSE_PROCESS_TIMEOUT = 30000L
        const val BEFORE_RESTART_INTERVAL = 5000L

        private fun ProcessHandle.destroy(logger: KLogger, name: String) {
            val nameAndPid = "${name}.${pid()}"
            destroy()
            if (!destroy()) {
                logger.info { "$nameAndPid descendant process couldn't stopped during $CLOSE_PROCESS_TIMEOUT, $MILLISECONDS. Closing process forcibly" }
                destroyForcibly()
                return
            }
            logger.info { "$nameAndPid descendant process closed gracefully" }
        }

        // Destroy all descendants process manually is important when pico process SIGTERM signal.
        private fun Process.destroyFamily(logger: KLogger, name: String) {
            val nameAndPid = "${name}.${pid()}"
            val descendants = descendants().toList()
            if (descendants.isNotEmpty()) {
                logger.info { "Closing descendants (${descendants.map(ProcessHandle::pid)}) processes for $nameAndPid script." }
                descendants.forEach { descendant ->
                    descendant.destroy(logger, nameAndPid)
                }
            }

            logger.info { "Closing $nameAndPid script." }
            destroy()
            if (!waitFor(CLOSE_PROCESS_TIMEOUT, MILLISECONDS)) {
                logger.info { "$nameAndPid script during $CLOSE_PROCESS_TIMEOUT, $MILLISECONDS. Closing process forcibly" }
                destroyForcibly()
                return
            }
            logger.info { "$nameAndPid script closed gracefully" }
        }
    }

    override fun run() {
        while (!Thread.currentThread().isInterrupted) {
            runCatching {
                startProcess()
                Thread.sleep(BEFORE_RESTART_INTERVAL)
            }.onFailure {
                when (it) {
                    is InterruptedException -> Thread.currentThread().interrupt()
                    else -> logger.error(it) { "Internal exception in $name component" }
                }
            }
        }
        logger.info { "Watcher thread for $name component is interrupted" }
    }

    protected abstract fun buildCommand(): List<String>

    protected abstract fun updateEnvironment(env: MutableMap<String, String>)

    private fun startProcess() {
        val command = listOf("bash", "-c", "`${buildCommand().joinToString(separator = " ")}`")
        logger.info { "Running command ${command.joinToString(" ")}" }
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(componentFolder)
        val env = processBuilder.environment()
        updateEnvironment(env)
        lock.write {
            process = processBuilder.start()
        }

        ComponentState(name, pid = process.pid()).also {
            LOGGER.info { "Started component: $it" }
            LOGGER.debug { "${it.componentName} component:  (env: $env)" }
            it.dumpState(stateFolder)
        }

        val exitCode = process.waitFor()
        if(exitCode != 0) {
            logger.error { "$name script exited with non zero exit code. Restarting process." }
            process.destroyFamily(logger, name)
            ComponentState(name,State.RESTARTING, null).also {
                LOGGER.info { "Stopped component: $it" }
                it.dumpState(stateFolder)
            }
        }
    }

    override fun close() {
        logger.info { "Closing process for $name script." }
        lock.read {
            if(this::process.isInitialized) {
                process.destroyFamily(logger, name)
            }
        }
    }
}