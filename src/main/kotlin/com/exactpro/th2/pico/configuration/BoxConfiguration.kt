package com.exactpro.th2.pico.configuration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class BoxConfiguration(val name: String = "", val image: String = "") {
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