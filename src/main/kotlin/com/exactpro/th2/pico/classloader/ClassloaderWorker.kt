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
package com.exactpro.th2.pico.classloader

import com.exactpro.th2.pico.IWorker
import mu.KotlinLogging
import java.lang.reflect.Method
import java.net.URLClassLoader


class ClassloaderWorker(private val main: Method,
                        private val args: Array<String>,
                        override val name: String,
                        val loader: URLClassLoader
): IWorker {
    override fun run() {
        LOGGER.info { "Started worker: $name" }
        try {
            main.invoke(null, args)
        } catch (e: Exception) {
            LOGGER.error(e) { "Exception while running $name. $e" }
            throw e
        }
    }

    override fun close() {}

    companion object {
        private val LOGGER = KotlinLogging.logger {  }
    }
}