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

import com.exactpro.th2.pico.IWorker
import com.exactpro.th2.pico.configuration.BoxConfiguration
import com.exactpro.th2.pico.configuration.PicoConfiguration
import org.apache.commons.io.FileUtils
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText

class ShellWorkerTest {
    companion object {
        private val TMP_FOLDER = Paths.get("build", "tmp", "shell-worker-test")
        private val TEST_COMPONENTS_FOLDER = TMP_FOLDER.resolve("component-folder")
        private val TEST_STATE_FOLDER = TMP_FOLDER.resolve("state-folder")

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            FileUtils.deleteDirectory(TMP_FOLDER.toFile())

            assertTrue(TEST_COMPONENTS_FOLDER.toFile().mkdirs())
            assertTrue(TEST_STATE_FOLDER.toFile().mkdirs())
        }
    }
    @Test
    @Timeout(5)
    fun `component logging`() {
        val components = mutableListOf<ComponentMetadata>()

        try {
            repeat(2) {
                val componentName = "component-$it"
                val componentPath = TEST_COMPONENTS_FOLDER.resolve(componentName)
                val componentFile = componentPath.toFile()

                assertTrue(componentFile.mkdirs())
                val component = TestShellWorker(
                    componentFile,
                    TEST_STATE_FOLDER.toFile(),
                    BoxConfiguration(componentName)
                )
                val thread = Thread(component)

                components.add(ComponentMetadata(
                    componentName,
                    componentPath,
                    component,
                    thread
                ))

                thread.start()
            }

            components.forEach { component ->
                val logFile = component.path.resolve(Path.of("logs", "system.log"))
                await until {
                    logFile.readText() == "${component.name}${System.lineSeparator()}"
                }
            }
        } finally {
            components.forEach(AutoCloseable::close)
        }

    }

    class ComponentMetadata(
        val name: String,
        val path: Path,
        private val worker: IWorker,
        private val thread: Thread
    ): AutoCloseable {
        override fun close() {
            thread.interrupt()
            thread.join()

            worker.close()
        }
    }
    class TestShellWorker(
        componentFolder: File,
        stateFolder: File,
        boxConfig: BoxConfiguration
    ) : ShellWorker(
        componentFolder,
        stateFolder,
        boxConfig,
        PicoConfiguration(),
    ) {
        override fun buildCommand(): List<String> = listOf("echo", boxConfig.boxName, ";", "sleep", "999")

        override fun updateEnvironment(env: MutableMap<String, String>) {
            // do nothing
        }
    }
}
