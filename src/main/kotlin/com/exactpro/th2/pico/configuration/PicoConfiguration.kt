/*
 * Copyright 2024 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.pico.configuration

internal const val JAVA_OPTS = "JAVA_OPTS"
internal const val JAVA_TOOL_OPTIONS = "JAVA_TOOL_OPTIONS"

data class PicoConfiguration(
    val componentConfig: ComponentConfig = ComponentConfig()
)

data class ComponentConfig(
    /** value in milliseconds */
    val beforeRestartTimeout: Long = 5_000,
    val memoryAutoScalingConfig: MemoryAutoScalingConfig = MemoryAutoScalingConfig(),
    val defaultEnvironmentVariables: Map<String, Set<String>> = mapOf(
        JAVA_TOOL_OPTIONS to setOf(
            "-XX:+ExitOnOutOfMemoryError",
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-Dlog4j2.shutdownHookEnabled=false",
        )
    ),
)

/**
 * auto-scaling functionality works when pico detect component crash by out of memory reason
 *  java: jvm generate `java_pid<pid>.hprof` file after crash component.
 *        default environment variables should include values:
 *          JAVA_TOOL_OPTIONS: ["-XX:+ExitOnOutOfMemoryError", "-XX:+HeapDumpOnOutOfMemoryError"]
 */
data class MemoryAutoScalingConfig(
    /** value in MB */
    val maxMemory: Int = 2_000,
    /** new values size is calculated by the formula `previous memory * growthFactor */
    val growthFactor: Double = 1.5,
) {
    init {
        require(maxMemory > 0) {
            "max memory should be positive, current value is $maxMemory"
        }
        require(growthFactor > 1) {
            "factor should be grater than 1, current value is $growthFactor"
        }
    }
}