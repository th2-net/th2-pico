package com.exactpro.th2.pico.classloader

import com.exactpro.th2.pico.AbstractBootstraper
import com.exactpro.th2.pico.IWorker
import com.exactpro.th2.pico.WorkersLoader
import com.exactpro.th2.pico.configuration.PicoConfiguration

class ClassloaderBootstrapper(private val config: PicoConfiguration): AbstractBootstraper(config) {
    override fun populateThreadsList() {
        for (worker in workers) {
            check(worker is ClassloaderWorker)
            val thread = Thread(worker)
            thread.contextClassLoader = worker.loader
            workerThreads.add(thread)
        }
    }

    override fun populateWorkers(): List<IWorker> {
        return WorkersLoader.loadClassloaderWorkers(config)
    }
}