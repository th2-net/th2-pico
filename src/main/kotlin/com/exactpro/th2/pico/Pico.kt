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

import com.exactpro.th2.pico.classloader.ClassloaderBootstrapper
import com.exactpro.th2.pico.configuration.PicoConfiguration
import com.exactpro.th2.pico.configuration.PicoSettings
import com.exactpro.th2.pico.configuration.PicoSettings.Companion.DEFAULT_COMPONENTS_DIR
import com.exactpro.th2.pico.configuration.PicoSettings.Companion.DEFAULT_STATE_FOLDER
import com.exactpro.th2.pico.operator.CONFIG_FILE_NAME
import com.exactpro.th2.pico.operator.CONFIG_FILE_SYSTEM_PROPERTY
import com.exactpro.th2.pico.operator.PicoOperator
import com.exactpro.th2.pico.operator.config.ConfigLoader
import com.exactpro.th2.pico.operator.config.OperatorRunConfig
import com.exactpro.th2.pico.operator.util.Mapper
import com.exactpro.th2.pico.shell.ShellBootstrapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.inputStream
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    LOGGER.info { "Pico is starting, user dir: ${System.getProperty("user.dir")}" }
    val options = Options()

    val th2ConfigOption = Option(
        "tdc",
        "th2-default-config",
        true,
        "Absolute or relative path to th2 configurations settings."
    )
    val picoConfig = Option(
        "pc",
        "pico-config",
        true,
        "Absolute or relative path to default environment variables config."
    )
    val componentsOption = Option(
        "o",
        "components",
        true,
        "Absolute or relative path to the directory containing component files."
    )
    val workDirectoryNameOption = Option(
        "w",
        "workDir",
        true,
        "Absolute or relative path to the work directory where pico manages all boxes instances."
    )
    val stateFolderOption = Option(
        "s",
        "stateFolder",
        true,
        "Absolute or relative path to directory where component states will be published."
    )
    val operatorMode = Option(
        "m",
        "mode",
        true,
        "Operator mode to run. Possible values: full, configs, none. Default: none"
    )
    val bootstrapType = Option(
        "b",
        "bootstrap-type",
        true,
        "Type of bootstrapping to use for the bundle. Possible values: shell, classloader. Default: classloader"
    )
    val helpOption = Option("h", "help", false, "Displays this help message.")

    options.addOption(th2ConfigOption)
    options.addOption(picoConfig)
    options.addOption(componentsOption)
    options.addOption(workDirectoryNameOption)
    options.addOption(stateFolderOption)
    options.addOption(operatorMode)
    options.addOption(bootstrapType)
    options.addOption(helpOption)

    val formatter = HelpFormatter()

    try {
        val parser: CommandLineParser = DefaultParser()
        val cmdArgs = parser.parse(options, args)
        if(cmdArgs.hasOption(helpOption)) {
            formatter.printHelp("Pico bundle", options)
            return
        }
        val componentsDir = if(cmdArgs.hasOption(componentsOption)) {
            Path.of(cmdArgs.getOptionValue(componentsOption))
        } else DEFAULT_COMPONENTS_DIR

        val workDirName = if(cmdArgs.hasOption(workDirectoryNameOption)) {
            cmdArgs.getOptionValue(workDirectoryNameOption)
        } else WORKING_DIR

        val stateFolder = if(cmdArgs.hasOption(stateFolderOption)) {
            Path.of(cmdArgs.getOptionValue(stateFolderOption))
        } else DEFAULT_STATE_FOLDER

        val th2config = if (cmdArgs.hasOption(th2ConfigOption)) {
            cmdArgs.getOptionValue(th2ConfigOption)
        } else {
            // logic for backward compatible
            System.getProperty(
                CONFIG_FILE_SYSTEM_PROPERTY,
                CONFIG_FILE_NAME
            )
        }
        val appConfig = Paths.get(th2config).inputStream().use(ConfigLoader::loadConfiguration)

        if(cmdArgs.hasOption(operatorMode)) {
            val mode = cmdArgs.getOptionValue(operatorMode)
            try {
                PicoOperator(appConfig).run(OperatorRunConfig(mode, true))
            } catch (e: Exception) {
                LOGGER.error(e) { "Error while running operator: ${e.message}" }
                return
            }
        }

        val picoConfiguration: PicoConfiguration = if (cmdArgs.hasOption(picoConfig)) {
            Path.of(cmdArgs.getOptionValue(picoConfig)).inputStream().use(Mapper.YAML_MAPPER::readValue)
        } else {
            PicoConfiguration()
        }

        val workDirFile = File(workDirName)
        if ( !workDirFile.exists()) {
            workDirFile.mkdirs()
        }

        val configuration = PicoSettings(
            componentsDir,
            appConfig.generatedConfigsLocation,
            workDirName,
            stateFolder,
            picoConfiguration
        )

        val bootstrapTypeValue = if(!cmdArgs.hasOption(bootstrapType)) {
            "shell"
        } else {
            cmdArgs.getOptionValue(bootstrapType)
        }

        val bootstrapper = when(bootstrapTypeValue) {
            "classloader" -> ClassloaderBootstrapper(configuration)
            "shell" -> ShellBootstrapper(configuration)
            else -> throw IllegalStateException("unreachable")
        }
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                runCatching {
                    bootstrapper.close()
                    ComponentState("pico", com.exactpro.th2.pico.State.STOPPED).also {
                        LOGGER.info { "Pico stopped: $it" }
                        it.dumpState(configuration.stateFolder)
                    }
                }.onFailure {
                    LOGGER.error(it) { "Shutdown process failure" }
                }
            }
        })
        bootstrapper.init()
        try {
            val picoState = ComponentState(
                "pico",
                State.RUNNING,
                ProcessHandle.current().pid(),
            )
            LOGGER.info { "Started pico: $picoState" }
            picoState.dumpState(configuration.stateFolder)
            bootstrapper.start()
        } catch (e: Exception) {
            exitProcess(1)
        }
    } catch (e: ParseException) {
        formatter.printHelp("Pico bundle", options)
        return
    }
}

val LOGGER = KotlinLogging.logger {  }
private const val WORKING_DIR = "../work"