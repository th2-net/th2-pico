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
package com.exactpro.th2.pico

import com.exactpro.th2.pico.configuration.BoxConfiguration
import com.exactpro.th2.pico.configuration.PicoConfiguration
import mu.KotlinLogging
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader

object WorkersLoader {
    private const val MAIN_CLASS_FILE_NAME = "mainclass"
    private const val JAR_FILE_DIR = "lib"
    private const val BOX_CONFIG_FILENAME = "box.json"
    private const val CONFIGS_COMMON_ARGUMENT = "-c"
    private val LOGGER = KotlinLogging.logger {  }


    fun load(configuration: PicoConfiguration): List<Worker> {
        val workers = mutableListOf<Worker>()
        val componentsDir = configuration.componentsDir
        val configsDir = configuration.configsDir
        isDirectory(componentsDir)
        isDirectory(configsDir)
        for(configDir in configsDir.listFiles()) {
            val boxConfig = getBoxConfiguration(configDir)
            val componentDir = findDirectory(
                boxConfig.image.split("/").last(),
                componentsDir
            ) { "Not found image files for ${boxConfig.image}" }
            workers.add(loadWorker(configDir, componentDir, boxConfig));
        }
        return workers;
    }

    private fun loadWorker(configDir: File,
                           componentDir: File,
                           boxConfiguration: BoxConfiguration): Worker {
        val method = getMethod(componentDir);
        val arguments = getArguments(configDir);
        LOGGER.info { "Loaded worker: ${componentDir.name}" }
        return Worker(method, arguments, boxConfiguration.name)
    }

    private fun getMethod(componentDir: File): Method {
        val classLoader = getClassLoader(componentDir);
        val mainClazz = getMainClass(componentDir);
        val clazz = Class.forName(mainClazz, true, classLoader)
        return clazz.getDeclaredMethod("main", Array<String>::class.java)
    }

    private fun getArguments(configDir: File): Array<String> {
        return arrayOf(CONFIGS_COMMON_ARGUMENT, configDir.absolutePath)
    }

    private fun getMainClass(dir: File): String {
        for(file in dir.listFiles()) {
            if(file.name == MAIN_CLASS_FILE_NAME) {
                return file.readLines().first()
            }
        }
        throw IllegalStateException()
    }

    private fun getClassLoader(dir: File): URLClassLoader {
        for(file in dir.listFiles()) {
            if(file.name == JAR_FILE_DIR) {
                val jars = file.listFiles().map { it.toURI().toURL() }.toTypedArray()
                return URLClassLoader(jars)
            }
        }
        throw IllegalStateException()
    }

    private fun getBoxConfiguration(configDir: File): BoxConfiguration {
        for(configFile in configDir.listFiles()) {
            if(configFile.name == BOX_CONFIG_FILENAME) {
                return BoxConfiguration.loadConfiguration(configFile)
            }
        }
        throw IllegalStateException("Not found $BOX_CONFIG_FILENAME in ${configDir.absolutePath}")
    }

    private fun findDirectory(name: String, dir: File, message: () -> (String) = {""}): File {
        isDirectory(dir)
        for(file in dir.listFiles()) {
            if(file.name == name) {
                isDirectory(dir)
                return file
            }
        }
        throw IllegalStateException("Not found $name in ${dir.absolutePath}. ${message}")
    }

    private fun isDirectory(file: File) {
        check(file.isDirectory) { "Expected ${file.absolutePath} to be a directory, but was not." }
    }
}