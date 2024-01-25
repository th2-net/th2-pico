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

import com.exactpro.th2.pico.configuration.BoxConfiguration
import com.exactpro.th2.pico.configuration.PicoConfiguration
import org.apache.commons.text.StringSubstitutor
import java.io.File

class CustomExecutionShellWorker(
    private val config: CustomExecutionConfiguration,
    stateFolder: File,
    boxConfig: BoxConfiguration,
    configsDirectory: File,
    componentDir: File,
    picoConfiguration: PicoConfiguration,
) : ShellWorker(
    stateFolder = stateFolder,
    boxConfig = boxConfig,
    componentFolder = componentDir,
    picoConfiguration = picoConfiguration,
) {
    init {
        require(config.command.isNotEmpty()) { "commands list must not be empty" }
    }
    private val substitutor = StringSubstitutor(
        mapOf(
            "COMPONENT_CONFIGURATION" to configsDirectory.absolutePath,
            "COMPONENT_DIRECTORY_ABSOLUTE" to componentDir.absolutePath,
            "COMPONENT_MEMORY_LIMIT" to boxConfig.resources.limits.memory,
        )
    )
    override fun buildCommand(): List<String> {
        return config.command.map(substitutor::replace)
    }

    override fun updateEnvironment(env: MutableMap<String, String>) {
        config.env.mapValuesTo(env) { (_, value) -> substitutor.replace(value) }
    }
}