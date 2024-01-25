/*
 * Copyright 2023-2024 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.pico.LOGGER
import com.exactpro.th2.pico.configuration.BoxConfiguration
import com.exactpro.th2.pico.configuration.JAVA_OPTS
import com.exactpro.th2.pico.configuration.JAVA_TOOL_OPTIONS
import com.exactpro.th2.pico.configuration.PicoConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.moveTo

class BaseShellWorker(
    private val script: File,
    componentFolder: File,
    private val args: Array<String>,
    stateFolder: File,
    boxConfig: BoxConfiguration,
    configsDirectory: String,
    picoConfiguration: PicoConfiguration
): ShellWorker(
    componentFolder = componentFolder,
    stateFolder = stateFolder,
    boxConfig = boxConfig,
    picoConfiguration = picoConfiguration,
) {
    companion object {
        private const val HEAP_DUMP_DIR_NAME = "heap_dumps"
        private const val MAX_HEAP_OPTION = "-Xmx"
        private const val TH2_COMMON_CONFIGS_DIR_SYSTEM_PROPERTY = "th2.common.configuration-directory"

        private fun Map<String, String>.merge(other: Map<String, String>): Map<String, String> =
            (asSequence() + other.asSequence())
                .groupingBy { it.key }
                .aggregate { _, accumulator, element, first ->
                    if (first) element.value else "$accumulator ${element.value}"
                }
    }

    private val configuredMemory: Int = boxConfig.resources.limits.memory.removeSuffix("Mi").toInt()
    private val heapDumpsDir: Path = componentFolder.resolve(HEAP_DUMP_DIR_NAME)
        .toPath().apply(Files::createDirectories)
    private var memory: Int = configuredMemory

    private val staticEnvVars: Map<String, String> = picoConfiguration.componentConfig.defaultEnvironmentVariables
        .mapValues { (_, values) -> values.joinToString(" ") }
        .merge(mapOf(JAVA_TOOL_OPTIONS to "-D$TH2_COMMON_CONFIGS_DIR_SYSTEM_PROPERTY=$configsDirectory"))

    override fun buildCommand(): List<String> {
        return listOf(script.absolutePath) + args
    }

    override fun updateEnvironment(env: MutableMap<String, String>) = env.putAll(
        staticEnvVars.merge(mapOf(JAVA_OPTS to "$MAX_HEAP_OPTION${memory}M")).also { println(it) }
    )

    override fun onRestart(pid: Long, code: Int) {
        with(picoConfiguration.componentConfig.memoryAutoScalingConfig) {
            val heapDumpFile = componentFolder.resolve("java_pid${pid}.hprof")
            if (heapDumpFile.exists()) {
                heapDumpFile.toPath().moveTo(heapDumpsDir.resolve(heapDumpFile.name), overwrite = true)
            } else {
                return
            }

            if (memory < maxMemory) {
                memory = (memory * growthFactor).toInt().coerceAtMost(maxMemory).also {
                    LOGGER.info { "Memory limit increased from $memory to $it for ${boxConfig.boxName}" }
                }
            }
        }
    }
}
