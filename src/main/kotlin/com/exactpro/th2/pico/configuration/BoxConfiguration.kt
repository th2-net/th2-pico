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
import mu.KotlinLogging
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class BoxConfiguration(val boxName: String = "",
                            val image: String = "",
                            val resources: ResourceConfig = ResourceConfig()
) {
    val directoryName get() = image.split("/").last().replace(":", "_")
    companion object {

        private val LOGGER = KotlinLogging.logger {  }

        fun loadConfiguration(file: File): BoxConfiguration = try {
            Jackson.MAPPER.readValue(file, BoxConfiguration::class.java)
        } catch (e: Exception) {
            LOGGER.error(e) { "Error while loading box configuration in ${file.absolutePath}" }
            throw IllegalStateException(e.message)
        }

    }
}

data class ResourceConfig(val limits: ResourceValues = ResourceValues(),
                          val requests: ResourceValues = ResourceValues())

data class ResourceValues(val cpu: String = "0.1", val memory: String = "300Mi")