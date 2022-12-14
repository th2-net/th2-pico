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

import com.exactpro.th2.pico.classloader.ClassloaderWorker
import com.exactpro.th2.pico.configuration.BoxConfiguration
import com.exactpro.th2.pico.configuration.PicoConfiguration
import com.exactpro.th2.pico.shell.ShellWorker
import mu.KotlinLogging
import java.io.File
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

object WorkersLoader {
    private const val MAIN_CLASS_FILE_NAME = "mainclass"
    private const val JAR_FILE_DIR = "lib"
    private const val SCRIPT_DIRECTORY = "bin"
    private const val SCRIPT_FILE_NAME = "service"
    private const val BOX_CONFIG_FILENAME = "boxSpec.json"
    private const val CONFIGS_COMMON_ARGUMENT = "--configs"

    private val LOGGER = KotlinLogging.logger {  }

    fun loadClassloaderWorkers(configuration: PicoConfiguration): List<ClassloaderWorker> {
        val workers = mutableListOf<ClassloaderWorker>()
        val componentsDir = configuration.componentsDir
        val configsDir = configuration.configsDir
        isDirectory(componentsDir)
        isDirectory(configsDir)
        for(configDir in configsDir.listFiles()) {
            val boxConfig = getBoxConfiguration(configDir) ?: continue
            val componentDir = findDirectory(
                boxConfig.image.split("/").last(),
                componentsDir
            ) ?: continue
            val worker = loadClassloaderWorker(configDir, componentDir, boxConfig) ?: continue
            workers.add(worker);
        }
        return workers;
    }

    fun loadShellWorkers(configuration: PicoConfiguration): List<ShellWorker> {
        val workers = mutableListOf<ShellWorker>()
        val componentsDir = configuration.componentsDir
        val configsDir = configuration.configsDir
        isDirectory(componentsDir)
        isDirectory(configsDir)
        for(configDir in configsDir.listFiles()) {
            val boxConfig = getBoxConfiguration(configDir) ?: continue
            val componentDir = findDirectory(
                boxConfig.image.split("/").last(),
                componentsDir
            ) ?: continue
            val worker = loadShellWorker(configDir, componentDir, boxConfig) ?: continue
            workers.add(worker)
        }
        return workers
    }

    private fun loadShellWorker(configDir: File,
                                componentDir: File,
                                boxConfiguration: BoxConfiguration
    ): ShellWorker? {
        val scriptDir = findDirectory(SCRIPT_DIRECTORY, componentDir) ?: return null
        val operatingSystem = System.getProperty("os.name")
        val scriptPath = if (operatingSystem.contains("Windows")) {
            scriptDir.resolve("$SCRIPT_FILE_NAME.bat")
        } else {
            scriptDir.resolve(SCRIPT_FILE_NAME)
        }
        if(!scriptPath.canExecute()) giveExecutionPermissions(scriptPath)
        val arguments = getArguments(configDir, componentDir)
        return ShellWorker(scriptPath, componentDir, arguments, boxConfiguration)
    }

    private fun loadClassloaderWorker(configDir: File,
                                      componentDir: File,
                                      boxConfiguration: BoxConfiguration
    ): ClassloaderWorker? {
        val classLoader = getClassLoader(componentDir) ?: return null
        val method = getMethod(componentDir, classLoader) ?: return null
        val arguments = getArguments(configDir)
        LOGGER.info { "Loaded worker: ${componentDir.name}" }
        return ClassloaderWorker(method, arguments, boxConfiguration.boxName, classLoader)
    }


    private fun getMethod(componentDir: File, classLoader: ClassLoader): Method? {
        val mainClazz = getMainClass(componentDir) ?: return null
        val clazz = Class.forName(mainClazz, true, classLoader)
        return clazz.getDeclaredMethod("main", Array<String>::class.java)
    }

    private fun getArguments(configDir: File, componentDir: File): Array<String> {
        val libDir = componentDir.resolve(JAR_FILE_DIR)
        return arrayOf(
            CONFIGS_COMMON_ARGUMENT, configDir.absolutePath
        ) + codecSailfishArg(libDir)
    }

    // TODO: Find better approach to it
    private fun codecSailfishArg(libDir: File) = if(libDir.listFiles().any { it.name.contains("codec-sailfish") }) {
        listOf("--sailfish-codec-config", "../codec_config.yml")
    } else {
        emptyList()
    }

    private fun getMainClass(dir: File): String? {
        dir.logDirectoryDoesNotExist()
        if(!dir.isDirectory) return null
        for(file in dir.listFiles()) {
            if(file.name == MAIN_CLASS_FILE_NAME) {
                return file.readLines().first()
            }
        }
        LOGGER.error { "Not found $MAIN_CLASS_FILE_NAME in $dir. Skipping related component." }
        return null
    }

    private fun getClassLoader(dir: File): URLClassLoader? {
        dir.logDirectoryDoesNotExist()
        if(!dir.isDirectory) return null
        var mainJar: Array<URL> = arrayOf()
        for(file in dir.listFiles()) {
            if(file.name == JAR_FILE_DIR) {
                mainJar = file.listFiles().map { it.toURI().toURL() }.toTypedArray()
            }
        }
        if(mainJar.isEmpty()) {
            LOGGER.error { "Not found $JAR_FILE_DIR in ${dir.absolutePath}. Skipping related component." }
            return null
        }

        return URLClassLoader(mainJar, WorkersLoader::class.java.classLoader)
    }

    private fun getBoxConfiguration(configDir: File): BoxConfiguration? {
        configDir.logDirectoryDoesNotExist()
        if(!configDir.isDirectory) return null
        for(configFile in configDir.listFiles()) {
            if(configFile.name == BOX_CONFIG_FILENAME) {
                return BoxConfiguration.loadConfiguration(configFile)
            }
        }
        LOGGER.error { "Not found $BOX_CONFIG_FILENAME in ${configDir.absolutePath}. Skipping component." }
        return null
    }

    private fun findDirectory(name: String, dir: File): File? {
        dir.logDirectoryDoesNotExist()
        if(!dir.isDirectory) return null
        for(file in dir.listFiles()) {
            if(file.name == name) {
                isDirectory(dir)
                return file
            }
        }
        LOGGER.error { "Not found ${dir.name} directory. Skipping related component." }
        return null
    }

    private fun giveExecutionPermissions(file: File) {
        file.setExecutable(true)
    }

    private fun isDirectory(file: File) {
        check(file.isDirectory) { "Expected ${file.absolutePath} to be a directory, but was not." }
    }

    private fun File.logDirectoryDoesNotExist() {
        if(!isDirectory) LOGGER.error { "$this is not a directory. Skipping related component." }
    }
}