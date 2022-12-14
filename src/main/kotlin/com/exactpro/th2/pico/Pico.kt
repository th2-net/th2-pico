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

import com.exactpro.th2.pico.classloader.ClassloaderBootstrapper
import com.exactpro.th2.pico.configuration.LibsConfig
import com.exactpro.th2.pico.configuration.PicoConfiguration
import com.exactpro.th2.pico.configuration.PicoConfiguration.Companion.DEFAULT_COMPONENTS_DIR
import com.exactpro.th2.pico.configuration.PicoConfiguration.Companion.DEFAULT_CONFIGS_DIR
import com.exactpro.th2.pico.operator.PicoOperator
import com.exactpro.th2.pico.operator.config.OperatorRunConfig
import com.exactpro.th2.pico.operator.configDir
/*import com.exactpro.th2.pico.operator.PicoOperator
import com.exactpro.th2.pico.operator.config.OperatorRunConfig*/
import com.exactpro.th2.pico.shell.ShellBootstrapper
import com.exactpro.th2.pico.shell.ShellWorker
import mu.KotlinLogging
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.io.File
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    val options = Options()

    val componentsOption = Option("o", "components", true, "Absolute or relative path to the directory containing component files.")
    val operatorMode = Option("m", "mode", true, "Operator mode to run. Possible values: full, configs, none. Default: none")
    val bootstrapType = Option("b", "bootstrap-type", true, "Type of bootstrapping to use for the bundle. Possible values: shell, classloader. Default: classloader")
    val helpOption = Option("h", "help", false, "Displays this help message.")

    options.addOption(componentsOption)
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
            cmdArgs.getOptionValue(componentsOption)
        } else DEFAULT_COMPONENTS_DIR

        if(cmdArgs.hasOption(operatorMode)) {
            val mode = cmdArgs.getOptionValue(operatorMode)
            try {
                PicoOperator.run(OperatorRunConfig(mode, true))
            } catch (e: Exception) {
                LOGGER.error { "Error while running operator: ${e.message}" }
                return
            }
        }

        val configuration = PicoConfiguration(componentsDir, configDir)
        unwrapLibraries(configuration.componentsDir)
        renameComponents(configuration.componentsDir)

        val bootstrapType = if(!cmdArgs.hasOption(bootstrapType)) "shell" else cmdArgs.getOptionValue(bootstrapType)

        val bootstrapper = when(bootstrapType) {
            "classloader" -> ClassloaderBootstrapper(configuration)
            "shell" -> ShellBootstrapper(configuration)
            else -> throw IllegalStateException("unreachable")
        }
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                bootstrapper.close()
            }
        })
        bootstrapper.init()
        try {
            bootstrapper.start()
        } catch (e: Exception) {
            exitProcess(1)
        }
    } catch (e: ParseException) {
        formatter.printHelp("Pico bundle", options)
        return
    }
}

private fun renameComponents(componentsDir: File) {
    for (file in componentsDir.listFiles() ?: emptyArray()) {
        if(file.name.contains(":")) {
            file.renameTo(File(file.parent, file.name.replace(":", "_")))
        }
    }
}

private fun unwrapLibraries(componentsDir: File) {
    val libsDir = componentsDir.resolve(LIBS_DIR)
    for(component in componentsDir.listFiles()) {
        if(component.name == LIBS_DIR) continue
        val config = try {
            LibsConfig.loadConfiguration(component.resolve(File(LIBS_JSON_FILE)))
        } catch (e: Exception) {
            LOGGER.error { "Error while unwrapping libraries files for $component. Message: ${e.message}" }
            continue
        }
        val componentLibsDir = componentsDir.resolve(component.name).resolve(LIBS_DIR)
        config.libs.forEach {
            if(directoryContainsFile(componentLibsDir, it)) return@forEach
            libsDir.resolve(it).copyTo(componentLibsDir.resolve(it))
        }
    }
}

private fun directoryContainsFile(directory: File, fileName: String): Boolean {
    val files = directory.listFiles() ?: return false

    for (file in files) {
        if (file.isFile && file.name == fileName) {
            return true
        }
    }

    return false
}

val LOGGER = KotlinLogging.logger {  }
private const val LIBS_JSON_FILE = "libConfig.json"
private const val LIBS_DIR = "lib"