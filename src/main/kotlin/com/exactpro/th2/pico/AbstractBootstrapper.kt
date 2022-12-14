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
import mu.KotlinLogging
import java.io.File

abstract class AbstractBootstrapper(private val configuration: PicoConfiguration): IBootstrap {
    protected val workerThreads: MutableList<Thread> = mutableListOf()
    protected val workers: MutableList<IWorker> = mutableListOf()

    override fun init() {
        generateShutdownFile()
        workers.addAll(populateWorkers())
        logger.info { "There are ${workers.size} workers loaded." }
    }

    override fun start() {
        logger.info { "Starting workers" }
        populateThreadsList()
        workerThreads.forEach {
            it.start()
        }
        workerThreads.forEach {
            it.join()
        }
    }

    abstract fun populateThreadsList()
    abstract fun populateWorkers(): List<IWorker>

    override fun stop() {
        workerThreads.apply {
            forEach { it.interrupt() }
            clear()
        }
    }

    override fun close() {
        workerThreads.apply {
            forEach { it.interrupt() }
            clear()
        }

        workers.apply {
            forEach { it.close() }
            clear()
        }
    }

    private fun generateShutdownFile() {
        // open file and create if not exist
        val shutdown = File(SHUTDOWN)
        shutdown.createNewFile()
        shutdown.printWriter().use { out ->
            out.print("ps -ef | grep -E \"${configuration.componentsDir}\" | awk '{print \$2}' | xargs kill -9")
        }
        shutdown.setExecutable(true)
    }

    companion object {
        private val logger = KotlinLogging.logger {  }
        private const val SHUTDOWN = "shutdown.sh"
    }
}