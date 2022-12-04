package com.exactpro.th2.pico.shell

import com.exactpro.th2.pico.AbstractBootstraper
import com.exactpro.th2.pico.IWorker
import com.exactpro.th2.pico.WorkersLoader
import com.exactpro.th2.pico.configuration.PicoConfiguration

class ShellBootstrapper(private val config: PicoConfiguration): AbstractBootstraper(config) {
    override fun populateThreadsList() {
        for (worker in workers) {
            check(worker is ShellWorker)
            val thread = Thread(worker)
            workerThreads.add(thread)
        }
    }

    override fun populateWorkers(): List<IWorker> {
        return WorkersLoader.loadShellWorkers(config)
    }

}