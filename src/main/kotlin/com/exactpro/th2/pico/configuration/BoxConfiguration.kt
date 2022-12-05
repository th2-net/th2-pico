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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class BoxConfiguration(val boxName: String = "", val image: String = "") {
    companion object {

        private val LOGGER = KotlinLogging.logger {  }
        private val MAPPER = ObjectMapper()

        fun loadConfiguration(file: File): BoxConfiguration = try {
            MAPPER.readValue(file, BoxConfiguration::class.java)
        } catch (e: Exception) {
            LOGGER.error { "Error while loading box configuration in ${file.absolutePath}" }
            throw IllegalStateException(e.message)
        }

    }
}