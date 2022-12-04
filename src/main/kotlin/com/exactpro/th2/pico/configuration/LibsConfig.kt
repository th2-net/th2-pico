package com.exactpro.th2.pico.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import java.io.File

data class LibsConfig(val libs: List<String> = listOf()) {

    companion object {

        private val LOGGER = KotlinLogging.logger {  }
        private val MAPPER = ObjectMapper()

        fun loadConfiguration(file: File): LibsConfig = try {
            MAPPER.readValue(file, LibsConfig::class.java)
        } catch (e: Exception) {
            LOGGER.error { "Error while loading libs configuration in ${file.absolutePath}" }
            throw IllegalStateException(e.message)
        }

    }
}