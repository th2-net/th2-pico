/*
 * Copyright 2024 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.pico.configuration.BoxConfiguration
import com.exactpro.th2.pico.configuration.ComponentConfig
import com.exactpro.th2.pico.configuration.JAVA_OPTS
import com.exactpro.th2.pico.configuration.JAVA_TOOL_OPTIONS
import com.exactpro.th2.pico.configuration.MemoryAutoScalingConfig
import com.exactpro.th2.pico.configuration.PicoConfiguration
import com.exactpro.th2.pico.configuration.ResourceConfig
import com.exactpro.th2.pico.configuration.ResourceValues
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

class BaseShellWorkerTest {

    @Test
    @Timeout(5)
    fun `component environment variables`(@TempDir(cleanup = ON_SUCCESS) componentDir: Path) {
        val envProperties = componentDir.resolve("env.properties")
        val script = componentDir.resolve("script").apply {
            writeText(
                """
                #!/bin/sh
                env > ${envProperties.name}
                sleep 10
            """.trimIndent()
            )
            toFile().setExecutable(true)
        }
        val cfgDir = componentDir.resolve("cfg").apply(Path::createDirectories)

        val boxConfig = BoxConfiguration(componentDir.name)
        val picoConfiguration = PicoConfiguration(
            ComponentConfig(
                defaultEnvironmentVariables = mapOf(
                    JAVA_OPTS to setOf("test-java-opts"),
                    JAVA_TOOL_OPTIONS to setOf("test-java-tool-options"),
                    "CUSTOM" to setOf("test-custom-a", "test-custom-b")
                )
            )
        )

        createWorker(script, componentDir, boxConfig, cfgDir, picoConfiguration).runAndUse {
            await.until(envProperties::exists)

            val properties = loadProperties(envProperties)
            assertEquals(
                "test-java-opts -Xmx${boxConfig.resources.limits.memory.removeSuffix("i")}",
                properties[JAVA_OPTS]
            )
            assertEquals(
                "test-java-tool-options -Dth2.common.configuration-directory=${cfgDir.absolutePathString()}",
                properties[JAVA_TOOL_OPTIONS]
            )
            assertEquals(
                "test-custom-a test-custom-b",
                properties["CUSTOM"]
            )
        }
    }

    /**
     * 1) test create flag
     * 2) script create env.properties wait until flag exist
     * 3) test check env.properties drop file
     * 4) script drop env.properties create flag file
     * 5) test wait flag file not exist
     */
    @Test
    @Timeout(5)
    fun `auto-scaling test`(@TempDir(cleanup = ON_SUCCESS) componentDir: Path) {
        val envProperties = componentDir.resolve("env.properties")
        val testFlag = componentDir.resolve("test-flag-file").apply(Path::createFile)
        val script = componentDir.resolve("script").apply {
            writeText(
                """
                #!/bin/sh
                env > $envProperties
                
                while [ -f $testFlag ]
                do
                     echo "bip" > /dev/null
                done
                
                rm $envProperties
                touch $testFlag
                touch "java_pid$$.hprof"
                exit 1
            """.trimIndent()
            )
            toFile().setExecutable(true)
        }
        val cfgDir = componentDir.resolve("cfg").apply(Path::createDirectories)

        val memory = 300
        val maxMemory = (memory * 2.5).toInt()
        val boxConfig = BoxConfiguration(
            boxName = componentDir.name,
            resources = ResourceConfig(
                limits = ResourceValues(
                    memory = "${memory}Mi"
                )
            )
        )
        val picoConfiguration = PicoConfiguration(
            ComponentConfig(
                beforeRestartTimeout = 0,
                MemoryAutoScalingConfig(
                    maxMemory = maxMemory,
                    growthFactor = 2.0
                )
            )
        )
        createWorker(script, componentDir, boxConfig, cfgDir, picoConfiguration).runAndUse {
            assertComponent(componentDir, envProperties, memory, testFlag, 0)
            assertComponent(componentDir, envProperties, memory + memory, testFlag, 1)
            assertComponent(componentDir, envProperties, maxMemory, testFlag, 2)
            assertComponent(componentDir, envProperties, maxMemory, testFlag, 3)
        }
    }

    /**
     * 1) test create flag
     * 2) script create env.properties wait until flag exist
     * 3) test check env.properties drop file
     * 4) script drop env.properties create flag file
     * 5) test wait flag file not exist
     */
    @Test
    @Timeout(5)
    fun `auto-scaling when init memory grater than max memory test`(@TempDir(cleanup = ON_SUCCESS) componentDir: Path) {
        val envProperties = componentDir.resolve("env.properties")
        val testFlag = componentDir.resolve("test-flag-file").apply(Path::createFile)
        val script = componentDir.resolve("script").apply {
            writeText(
                """
                #!/bin/sh
                env > $envProperties
                
                while [ -f $testFlag ]
                do
                     echo "bip" > /dev/null
                done
                
                rm $envProperties
                touch $testFlag
                touch "java_pid$$.hprof"
                exit 1
            """.trimIndent()
            )
            toFile().setExecutable(true)
        }
        val cfgDir = componentDir.resolve("cfg").apply(Path::createDirectories)

        val memory = 300
        val boxConfig = BoxConfiguration(
            boxName = componentDir.name,
            resources = ResourceConfig(
                limits = ResourceValues(
                    memory = "${memory}Mi"
                )
            )
        )
        val picoConfiguration = PicoConfiguration(
            ComponentConfig(
                beforeRestartTimeout = 0,
                MemoryAutoScalingConfig(
                    maxMemory = memory - 1,
                )
            )
        )
        createWorker(script, componentDir, boxConfig, cfgDir, picoConfiguration).runAndUse {
            assertComponent(componentDir, envProperties, memory, testFlag, 0)
            assertComponent(componentDir, envProperties, memory, testFlag, 1)
        }
    }

    @Test
    @Timeout(5)
    fun `component empty environment variables`(@TempDir(cleanup = ON_SUCCESS) componentDir: Path) {
        val envProperties = componentDir.resolve("env.properties")
        val script = componentDir.resolve("script").apply {
            writeText(
                """
                #!/bin/sh
                env > ${envProperties.name}
                sleep 10
            """.trimIndent()
            )
            toFile().setExecutable(true)
        }
        val cfgDir = componentDir.resolve("cfg").apply(Path::createDirectories)

        val boxConfig = BoxConfiguration(componentDir.name)
        val picoConfiguration = PicoConfiguration(
            ComponentConfig(
                defaultEnvironmentVariables = emptyMap()
            )
        )
        createWorker(script, componentDir, boxConfig, cfgDir, picoConfiguration).runAndUse {
            await.until(envProperties::exists)

            val properties = loadProperties(envProperties)
            assertEquals(
                "-Xmx${boxConfig.resources.limits.memory.removeSuffix("i")}",
                properties[JAVA_OPTS]
            )
            assertEquals(
                "-Dth2.common.configuration-directory=${cfgDir.absolutePathString()}",
                properties[JAVA_TOOL_OPTIONS]
            )
        }
    }

    private fun assertComponent(componentDir: Path, envProperties: Path, memory: Int, testFlag: Path, restarts: Int) {
        await.until(envProperties::exists)

        assertTrue(loadProperties(envProperties).getProperty(JAVA_OPTS).contains("-Xmx${memory}M")) {
            "check memory $memory"
        }

        assertTrue(componentDir.listDirectoryEntries("*.hprof").isEmpty()) {
            "component directory doesn't contain *.hprof files"
        }

        assertEquals(restarts, componentDir.resolve("heap_dumps").listDirectoryEntries("*.hprof").size) {
            "component directory doesn't contain *.hprof files"
        }

        testFlag.deleteExisting()
        await.until(testFlag::exists)
    }

    private fun createWorker(
        script: Path,
        componentDir: Path,
        boxConfig: BoxConfiguration,
        cfgDir: Path,
        picoConfiguration: PicoConfiguration = PicoConfiguration(),
    ) = BaseShellWorker(
        script.toFile(),
        componentDir.toFile(),
        arrayOf("test-arg"),
        componentDir.resolve("state").apply(Path::createDirectories).toFile(),
        boxConfig,
        cfgDir.absolutePathString(),
        picoConfiguration
    )

    private fun loadProperties(envProperties: Path): Properties = Properties().apply {
        envProperties.inputStream().use(::load)
    }

    private fun BaseShellWorker.runAndUse(func: () -> Unit) {
        val thread = Thread(this).apply(Thread::start)
        try {
            use {
                func.invoke()
            }
        } finally {
            thread.interrupt()
            thread.join()
        }
    }
}
