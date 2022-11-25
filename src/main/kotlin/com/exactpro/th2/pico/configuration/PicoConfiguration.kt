package com.exactpro.th2.pico.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import mu.KotlinLogging
import java.io.File
import java.io.IOException

data class PicoConfiguration(
    private val componentsPath: String,
    private val configsPath: String
) {
    val componentsDir: File by lazy { findPath(componentsPath) {"Not found components dir by path: $componentsPath"} }
    val configsDir: File by lazy { findPath(configsPath) {"Not found configs dir by path: $configsPath"} }

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
        val DEFAULT_CONFIGS_DIR = System.getProperty("user.dir") + File.separator + "configs" + File.separator
    }
}