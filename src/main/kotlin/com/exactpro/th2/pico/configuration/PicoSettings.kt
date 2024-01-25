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
package com.exactpro.th2.pico.configuration

import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

data class PicoSettings(
    private val componentsPath: Path,
    private val configsPath: Path,
    val workPathName: String,
    private val stateFolderPath: Path,
    val picoConfiguration: PicoConfiguration
) {
    val componentsDir: File by lazy { findPath(componentsPath) {"Not found components dir by path: $componentsPath"} }
    val configsDir: File by lazy { findPath(configsPath) {"Not found configs dir by path: $configsPath"} }
    val stateFolder: File by lazy { stateFolderPath.toFile().also { if(!it.exists()) it.mkdirs() } }

    private fun findPath(path: Path, message: () -> String): File {
        val absolute = path.absolute()
        if(absolute.exists()) {
            return absolute.toFile()
        }
        throw IllegalStateException(message.invoke())
    }

    companion object {

        val DEFAULT_COMPONENTS_DIR = Path.of("components").absolute()
        val DEFAULT_STATE_FOLDER = Path.of("states").absolute()
    }
}