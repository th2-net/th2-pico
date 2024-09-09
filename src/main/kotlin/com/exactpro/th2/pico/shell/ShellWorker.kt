/*
 * Copyright 2022-2024 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.pico.configuration.PicoConfiguration
import mu.KLogger
import mu.KotlinLogging
import org.slf4j.MDC
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val CTX_COMPONENT_NAME_PROPERTY = "th2.pico.component.name"

private const val CTX_COMPONENT_WORK_DIR_PROPERTY = "th2.pico.component.work-dir"
private const val COMPONENT_LOGGER_NAME_PREFIX = "th2.pico.component"

abstract class ShellWorker(
    protected val componentFolder: File,
    private val stateFolder: File,
    protected val boxConfig: BoxConfiguration,
    protected val picoConfiguration: PicoConfiguration,
): IWorker {
    private val lock = ReentrantReadWriteLock()
    private lateinit var process: Process
    private val logger = KotlinLogging.logger(this::class.java.canonicalName)

    override val name: String
        get() = boxConfig.boxName

    companion object {
        private const val CLOSE_PROCESS_TIMEOUT = 30000L

        private fun ProcessHandle.destroy(logger: KLogger, name: String) {
            val nameAndPid = "${name}.${pid()}"
            destroy()
            if (!destroy()) {
                logger.info {
                    "$nameAndPid descendant process couldn't stopped during $CLOSE_PROCESS_TIMEOUT, " +
                            "$MILLISECONDS. Closing process forcibly"
                }
                destroyForcibly()
                return
            }
            logger.info { "$nameAndPid descendant process closed gracefully" }
        }

        // Destroy all descendants process manually is important when pico process SIGTERM signal.
        private fun Process.destroyFamily(logger: KLogger, name: String) {
            val nameAndPid = "${name}.${pid()}"
            val descendants: List<ProcessHandle> = descendants().collect(Collectors.toList())
            if (descendants.isNotEmpty()) {
                logger.info {
                    "Closing descendants (${descendants.map(ProcessHandle::pid)}) processes for $nameAndPid script."
                }
                descendants.forEach { descendant ->
                    descendant.destroy(logger, nameAndPid)
                }
            }

            logger.info { "Closing $nameAndPid script." }
            destroy()
            if (!waitFor(CLOSE_PROCESS_TIMEOUT, MILLISECONDS)) {
                logger.info {
                    "$nameAndPid script during $CLOSE_PROCESS_TIMEOUT, $MILLISECONDS. Closing process forcibly"
                }
                destroyForcibly()
                return
            }
            logger.info { "$nameAndPid script closed gracefully" }
        }
    }

    override fun run() {
        val componentOutputStream = configureOutputStream()

        while (!Thread.currentThread().isInterrupted) {
            runCatching {
                startProcess(componentOutputStream)
                Thread.sleep(picoConfiguration.componentConfig.beforeRestartTimeout)
            }.onFailure {
                when (it) {
                    is InterruptedException -> Thread.currentThread().interrupt()
                    else -> logger.error(it) { "Internal exception in $name component" }
                }
            }
        }
        logger.info { "Watcher thread for $name component is interrupted" }
    }

    private fun configureOutputStream(): OutputStream {
        /*
            The code below is responsible for passing component name and dir to log context for the current thread.
            Log4j config can resolve passed variables. User can configure `Routing Appender` to route log stream to separate files depend on the variables
            ```
            appender.component.type = Routing
            appender.component.name = component_output_appender
            appender.component.routes.type = Routes
            appender.component.routes.pattern = ${ctx:th2.pico.component.name}
            ...
            appender.component.routes.dynamic_rolling_file.type = Route
            appender.component.routes.dynamic_rolling_file.appender.type = RollingFile
            appender.component.routes.dynamic_rolling_file.appender.fileName = ${ctx:th2.pico.component.work-dir}/logs/app.log
            ```
         */
        MDC.put(CTX_COMPONENT_NAME_PROPERTY, boxConfig.boxName)
        MDC.put(CTX_COMPONENT_WORK_DIR_PROPERTY, componentFolder.absolutePath)
        /*
            The logger below use constant [COMPONENT_LOGGER_NAME_PREFIX] prefix because
             we need a specific Appender to handle sysout / syserr stream from process instead of separate log messages.
            Hardcoded prefix simplifies log configuration
            ```
            logger.component.name=th2.pico.component
            logger.component.appenderRef.routing.ref=component_output_appender
            ```
            This approach splits component log configuration to two parts:
            1) Pico log configuration is responsible for log file location and its rolling configuration.
            2) Component log configuration is responsible for log lines patten. Components should use Console Appender as usual in th2.
         */
        return Slf4jStream.of(KotlinLogging.logger("${COMPONENT_LOGGER_NAME_PREFIX}.${boxConfig.boxName}")).asInfo()
    }

    protected abstract fun buildCommand(): List<String>

    protected abstract fun updateEnvironment(env: MutableMap<String, String>)

    protected open fun onRestart(pid: Long, code: Int) { }

    private fun startProcess(componentOutputStream: OutputStream) {
        val command = listOf("bash", "-c", buildCommand().joinToString(separator = " "))
        logger.info { "Running command ${command.joinToString(" ")}" }

        val processBuilder = ProcessExecutor().command(command)
            .directory(componentFolder)
            .redirectOutput(componentOutputStream)
        val env = processBuilder.environment
        updateEnvironment(processBuilder.environment)
        lock.write {
            process = processBuilder.start().process
        }

        val pid = process.pid()
        ComponentState(name, pid = pid).also {
            LOGGER.info { "Started component: $it" }
            LOGGER.debug { "${it.componentName} component:  (env: $env)" }
            it.dumpState(stateFolder)
        }

        val exitCode = process.waitFor()
        if(exitCode != 0) {
            logger.error { "$name script exited with non zero exit code ($exitCode). Restarting process." }
            process.destroyFamily(logger, name)
            ComponentState(name,State.RESTARTING, null).also {
                LOGGER.info { "Stopped component: $it" }
                it.dumpState(stateFolder)
            }
            onRestart(pid, exitCode)
        }
    }

    override fun close() {
        logger.info { "Closing process for $name script." }
        lock.read {
            if(this::process.isInitialized) {
                process.destroyFamily(logger, name)
                ComponentState(name,State.STOPPED, null).also {
                    LOGGER.info { "Stopped component: $it" }
                    it.dumpState(stateFolder)
                }
            }
        }
    }
}