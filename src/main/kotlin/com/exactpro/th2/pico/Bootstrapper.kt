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

class Bootstrapper(private val configuration: PicoConfiguration): IBootstrap {
    private val workerThreads: MutableList<Thread> = mutableListOf()
    private val workers: MutableList<Worker> = mutableListOf()

    override fun init() {
        try {
            workers.addAll(WorkersLoader.load(configuration))
        } catch (e: Exception) {
            LOGGER.error { "Error while loading workers: ${e.message}" }
            throw e
        }
        LOGGER.info { "There are ${workers.size} workers loaded." }
        //System.setSecurityManager(UnsafeSecurityManager());
    }

    override fun start() {
        LOGGER.info { "Starting workers" }
        for (worker in workers) {
            workerThreads.add(Thread(worker))
        }
        workerThreads.map { it.start() }
        workerThreads.map { it.join() }
    }

    override fun stop() {
        workerThreads.map { it.interrupt() }
        workerThreads.clear()
    }

    override fun close() {
        workerThreads.map { it.interrupt() }
        workerThreads.clear()
        workers.clear()
    }

    companion object {
        private val LOGGER = KotlinLogging.logger {  }
    }
}