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
package com.exactpro.th2.pico

import com.exactpro.th2.pico.classloader.ClassloaderWorker
import com.exactpro.th2.pico.configuration.BoxConfiguration
import com.exactpro.th2.pico.configuration.PicoConfiguration
import com.exactpro.th2.pico.configuration.PicoSettings
import com.exactpro.th2.pico.shell.BaseShellWorker
import com.exactpro.th2.pico.shell.CustomExecutionConfiguration
import com.exactpro.th2.pico.shell.CustomExecutionShellWorker
import com.exactpro.th2.pico.shell.ShellWorker
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileFilter
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.LinkedList

object WorkersLoader {
    private const val MAIN_CLASS_FILE_NAME = "mainclass"
    private const val JAR_FILE_DIR = "lib"
    private const val SCRIPT_DIRECTORY = "bin"
    private const val SCRIPT_FILE_NAME = "service"
    private const val BOX_CONFIG_FILENAME = "boxConfig.json"
    private const val CONFIGS_COMMON_ARGUMENT = "--configs"
    private const val CUSTOM_EXECUTION_CONFIG = "execute.json"

    private val LOGGER = KotlinLogging.logger {  }

    fun loadClassloaderWorkers(configuration: PicoSettings): List<ClassloaderWorker> {
        val workers = mutableListOf<ClassloaderWorker>()
        val componentsDir = configuration.componentsDir
        val configsDir = configuration.configsDir
        isDirectory(componentsDir)
        isDirectory(configsDir)
        for(configDir in configsDir.saveListFiles()) {
            val boxConfig = getBoxConfiguration(configDir) ?: continue
            val componentDir = findDirectory(
                boxConfig.directoryName,
                componentsDir
            ) ?: continue
            val worker = loadClassloaderWorker(configDir, componentDir, boxConfig) ?: continue
            workers.add(worker)
        }
        return workers
    }

    fun loadShellWorkers(configuration: PicoSettings): List<ShellWorker> {
        val workers = mutableListOf<ShellWorker>()
        val componentsDir = configuration.componentsDir
        val configsDir = configuration.configsDir
        val workDir = File(configuration.workPathName)
        val stateFolder = configuration.stateFolder

        isDirectory(componentsDir)
        isDirectory(configsDir)
        isDirectory(workDir)
        for ( configDir in configsDir.saveListFiles()) {
            val boxConfig = getBoxConfiguration(configDir) ?: continue

            val componentDir = findDirectory(
                boxConfig.directoryName,
                componentsDir
            ) ?: continue

            //First of all check that workDirectory contains this service.
            val boxWorkDir = workDir.resolve(configDir.name)

            if ( boxWorkDir.exists()) {
                //if the box directory exists, the bootstrapper won't check that the directory is in up-to-date
                //if a user would like to have the fresh copy, he/she just needs to remove boxWorkDir
            } else {
                //prepare box work dir
                if ( boxWorkDir.mkdir() ) {
                    FileUtils.copyDirectory(componentDir, boxWorkDir, false)

                    val relativeCommonLibsDir = boxWorkDir.toPath().toAbsolutePath().relativize(componentsDir.toPath().toAbsolutePath().resolve("lib")).toFile()
                    val relativeComponentDir= boxWorkDir.toPath().toAbsolutePath().relativize(componentDir.toPath().toAbsolutePath()).toFile()
                    //Change service file for Java boxes
                    changeJavaBoxServiceFile(componentDir, boxWorkDir, relativeComponentDir, relativeCommonLibsDir)

                } else {
                    ComponentState(boxConfig.boxName, State.CONFIGURATION_ERROR, null, "Could not create $boxWorkDir for this box.").also {
                        it.dumpState(stateFolder)
                    }
                    LOGGER.error("Could not create {}", boxWorkDir.absolutePath)
                }
            }

            val worker = loadShellWorker(
                configDir,
                boxWorkDir,
                boxConfig,
                stateFolder,
                configuration.picoConfiguration
            ) ?: continue
            workers.add(worker)
        }
        return workers
    }

    private fun loadShellWorker(
        configDir: File,
        componentDir: File,
        boxConfiguration: BoxConfiguration,
        stateFolder: File,
        picoConfiguration: PicoConfiguration,
    ): ShellWorker? {
        componentDir.resolve(CUSTOM_EXECUTION_CONFIG).takeIf { it.exists() }?.also {
            return createCustomShellWorker(
                it,
                boxConfiguration,
                configDir,
                stateFolder,
                componentDir,
                picoConfiguration
            )
        }
        val scriptDir = findDirectory(SCRIPT_DIRECTORY, componentDir) ?: kotlin.run {
            ComponentState(boxConfiguration.boxName, State.CONFIGURATION_ERROR, null, "Could not find $SCRIPT_DIRECTORY directory with bash script to run.").also {
                it.dumpState(stateFolder)
            }
            return null
        }
        val operatingSystem = System.getProperty("os.name")
        val scriptPath = if (operatingSystem.contains("Windows")) {
            scriptDir.resolve("$SCRIPT_FILE_NAME.bat")
        } else {
            scriptDir.resolve(SCRIPT_FILE_NAME)
        }
        if(!scriptPath.canExecute()) giveExecutionPermissions(scriptPath)
        val arguments = getArguments(configDir, componentDir)
        return BaseShellWorker(
            scriptPath,
            componentDir,
            arguments,
            stateFolder,
            boxConfiguration,
            configDir.absolutePath,
            picoConfiguration
        )
    }

    private fun createCustomShellWorker(
        customExecutionConfigFile: File,
        boxConfiguration: BoxConfiguration,
        configDir: File,
        stateFolder: File,
        componentDir: File,
        picoConfiguration: PicoConfiguration,
    ): ShellWorker? {
        val config: CustomExecutionConfiguration = try {
            CustomExecutionConfiguration.load(customExecutionConfigFile)
        } catch (ex: Exception) {
            LOGGER.error(ex) { "cannot load custom execution configuration from $customExecutionConfigFile" }
            ComponentState(
                boxConfiguration.boxName,
                State.CONFIGURATION_ERROR,
                null,
                "cannot load custom execution configuration: ${ex.message}",
            ).also {
                it.dumpState(stateFolder)
            }
            return null
        }

        return CustomExecutionShellWorker(
            config,
            stateFolder,
            boxConfiguration,
            configDir.absoluteFile,
            componentDir,
            picoConfiguration,
        )
    }


    private fun changeJavaBoxServiceFile(componentDir : File, boxWorkDir : File, componentDirRelative : File, commonLibsDirRelative : File) {
        val binDir  = boxWorkDir.resolve("bin")

        if ( binDir.exists() ) {

            binDir.saveListFiles { pathname -> pathname.name.startsWith("service") }.forEach{serviceFile ->
                run {

                    val lines = serviceFile.readLines()
                    val newLines = LinkedList<String>()
                    val classPathToken = "CLASSPATH="
                    for (i: Int in lines.indices) {

                        if (lines[i].startsWith(classPathToken)) {
                            val classpath = lines[i].substring(classPathToken.length)

                            val libs = classpath.split(File.pathSeparator)

                            val newLibs = LinkedList<String>()

                            newLines.add("PICO_COMMON_LIBS=$commonLibsDirRelative")
                            newLines.add("PICO_IMAGE_LIB=" + componentDirRelative.resolve("lib").toString())
                            newLines.add("PICO_IMAGE_CODEC_LIB=" + componentDirRelative.resolve("codec_implementation").toString())

                            for (libPath in libs) {
                                val index = libPath.lastIndexOf(File.separator)

                                val libName = libPath.substring(if (index > 0) index + 1  else 0)

                                //First check that the library is located in lib directory of the image
                                if (componentDir.resolve("lib").resolve(libName).exists()) {
                                    newLibs.add("\$PICO_IMAGE_LIB" + File.separator + libName)
                                } else if (componentDir.resolve("codec_implementation").resolve(libName).exists())
                                    newLibs.add("\$PICO_IMAGE_CODEC_LIB" + File.separator + libName)
                                else {
                                    newLibs.add("\$PICO_COMMON_LIBS" + File.separator + libName)
                                }
                            }

                            newLines.add(classPathToken + newLibs.joinToString(File.pathSeparator))

                        } else {
                            newLines.add(lines[i])
                        }

                    }
                    Files.write(serviceFile.toPath(), newLines)

                }
            }
        }
    }

    private fun loadClassloaderWorker(configDir: File,
                                      componentDir: File,
                                      boxConfiguration: BoxConfiguration
    ): ClassloaderWorker? {
        val classLoader = getClassLoader(componentDir) ?: return null
        val method = getMethod(componentDir, classLoader) ?: return null
        val arguments = getArguments(configDir, componentDir)
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
        ) + codecSailfishArg(libDir, componentDir)
    }

    // TODO: Find better approach to it
    private fun codecSailfishArg(libDir: File, componentDir: File): List<String> {
        if (!libDir.isDirectory) {
            return emptyList()
        }

        return if(libDir.saveListFiles().any { it.name.contains("codec-sailfish") }) {
            listOf("--sailfish-codec-config", componentDir.resolve("codec_config.yml").absolutePath)
        } else {
            emptyList()
        }
    }

    private fun getMainClass(dir: File): String? {
        dir.logDirectoryDoesNotExist()
        if(!dir.isDirectory) return null
        for(file in dir.saveListFiles()) {
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
        for(file in dir.saveListFiles()) {
            if(file.name == JAR_FILE_DIR) {
                mainJar = file.saveListFiles().map { it.toURI().toURL() }.toTypedArray()
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
        for(configFile in configDir.saveListFiles()) {
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
        for(file in dir.saveListFiles()) {
            if(file.name == name) {
                isDirectory(dir)
                return file
            }
        }
        LOGGER.error { "Not found $name directory. Skipping related component." }
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

    private fun File.saveListFiles() = requireNotNull(listFiles()) {
        "directory $this does not exist or application does not have permissions to read it"
    }

    private fun File.saveListFiles(filter: FileFilter) = requireNotNull(listFiles(filter)) {
        "directory $this does not exist or application does not have permissions to read it"
    }
}