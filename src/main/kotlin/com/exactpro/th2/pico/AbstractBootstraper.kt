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

abstract class AbstractBootstraper(private val configuration: PicoConfiguration): IBootstrap {
    protected val workerThreads: MutableList<Thread> = mutableListOf()
    protected val workers: MutableList<IWorker> = mutableListOf()

    override fun init() {
        try {
            workers.addAll(populateWorkers())
        } catch (e: Exception) {
            LOGGER.error { "Error while loading workers: ${e.message}" }
            throw e
        }
        LOGGER.info { "There are ${workers.size} workers loaded." }
        //System.setSecurityManager(UnsafeSecurityManager());
    }

    override fun start() {
        LOGGER.info { "Starting workers" }
        populateThreadsList()
        workerThreads.map {
            it.start()
        }
        workerThreads.map { it.join() }
    }

    abstract fun populateThreadsList()
    abstract fun populateWorkers(): List<IWorker>

    override fun stop() {
        workerThreads.map { it.interrupt() }
        workerThreads.clear()
    }

    override fun close() {
        workerThreads.map { it.interrupt() }
        workers.map { it.close() }
        workerThreads.clear()
        workers.clear()
    }

    companion object {
        private val LOGGER = KotlinLogging.logger {  }
    }
}