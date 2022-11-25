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

import com.exactpro.th2.pico.configuration.PicoConfiguration
import com.exactpro.th2.pico.configuration.PicoConfiguration.Companion.DEFAULT_COMPONENTS_DIR
import com.exactpro.th2.pico.configuration.PicoConfiguration.Companion.DEFAULT_CONFIGS_DIR
import mu.KotlinLogging
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.io.File


fun main(args: Array<String>) {
    val options = Options()

    val componentsOption = Option("o", "components", true, "absolute/relative path to directory with component files.")
    val configsOption = Option("c", "configs", true, "absolute/relative path to directory with configs.")
    val helpOption = Option("h", "help", false, "help board")

    options.addOption(componentsOption)
    options.addOption(configsOption)
    options.addOption(helpOption)

    val formatter = HelpFormatter()

    val configuration = try {
        val parser: CommandLineParser = DefaultParser()
        val cmdArgs = parser.parse(options, args)
        if(cmdArgs.hasOption(helpOption)) {
            formatter.printHelp("Pico bundle", options)
            return
        }
        val componentsDir = if(cmdArgs.hasOption(componentsOption)) {
            cmdArgs.getOptionValue(componentsOption)
        } else DEFAULT_COMPONENTS_DIR
        val configsDir = if(cmdArgs.hasOption(configsOption)) {
            cmdArgs.getOptionValue(configsOption)
        } else DEFAULT_CONFIGS_DIR
        PicoConfiguration(componentsDir, configsDir)
    } catch (e: ParseException) {
        formatter.printHelp("Pico bundle", options)
        return
    }

    val bootstrapper = Bootstrapper(configuration)
    bootstrapper.init()
    bootstrapper.start()
}