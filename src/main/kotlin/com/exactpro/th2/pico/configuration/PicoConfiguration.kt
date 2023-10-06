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
package com.exactpro.th2.pico.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import mu.KotlinLogging
import java.io.File
import java.io.IOException

data class PicoConfiguration(
    private val componentsPath: String,
    private val configsPath: String,
    val workPathName: String,
    private val stateFolderPath: String
) {
    val componentsDir: File by lazy { findPath(componentsPath) {"Not found components dir by path: $componentsPath"} }
    val configsDir: File by lazy { findPath(configsPath) {"Not found configs dir by path: $configsPath"} }
    val stateFolder: File by lazy { File(stateFolderPath).also { if(!it.exists()) it.mkdirs() } }

    private fun findPath(path: String, message: () -> String): File {
        val absolute = File(System.getProperty("user.dir") + File.separator + File(path))
        if(absolute.exists()) {
            return absolute
        }
        val relative = File(path)
        if(relative.exists()) {
            return relative
        }
        throw IllegalStateException(message.invoke())
    }

    companion object {

        val DEFAULT_COMPONENTS_DIR = System.getProperty("user.dir") + File.separator + "components" + File.separator
        val DEFAULT_STATE_FOLDER = System.getProperty("user.dir") + File.separator + "states" + File.separator
        val DEFAULT_CONFIGS_DIR = System.getProperty("user.dir") + File.separator + "configs" + File.separator
    }
}