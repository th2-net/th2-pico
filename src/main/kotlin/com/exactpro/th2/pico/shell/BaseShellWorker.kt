/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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
import java.io.File

class BaseShellWorker(
    private val script: File,
    componentFolder: File,
    private val args: Array<String>,
    stateFolder: File,
    boxConfig: BoxConfiguration,
    private val configsDirectory: String
): ShellWorker(
    componentFolder = componentFolder,
    stateFolder = stateFolder,
    boxConfig = boxConfig,
) {
    companion object {
        private const val MAX_HEAP_OPTION = "-Xmx"
        private const val JAVA_OPTS = "JAVA_OPTS"
        private const val JAVA_TOOL_OPTIONS = "JAVA_TOOL_OPTIONS"
        private const val TH2_COMMON_CONFIGS_DIR_SYSTEM_PROPERTY = "th2.common.configuration-directory"
    }

    override fun buildCommand(): List<String> {
        return listOf(script.absolutePath) + args
    }

    override fun updateEnvironment(env: MutableMap<String, String>) {
        env[JAVA_OPTS] = MAX_HEAP_OPTION + boxConfig.resources.limits.memory.removeSuffix("i")
        env[JAVA_TOOL_OPTIONS] = "-D$TH2_COMMON_CONFIGS_DIR_SYSTEM_PROPERTY=$configsDirectory"
    }
}