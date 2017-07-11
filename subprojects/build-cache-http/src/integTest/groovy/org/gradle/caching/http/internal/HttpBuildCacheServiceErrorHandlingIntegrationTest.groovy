/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.caching.http.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class HttpBuildCacheServiceErrorHandlingIntegrationTest extends AbstractIntegrationSpec implements HttpBuildCacheFixture {
    def setup() {
        buildFile << """   
            import org.gradle.api.*
            apply plugin: 'base'
        """
    }

    def "build does not fail if connection drops during store"() {
        withSimpleCacheableTask()
        httpBuildCacheServer.dropConnectionForPutAfterBytes(1024)
        startServer()
        String errorPattern = /(Broken pipe|Connection reset|Software caused connection abort: socket write error)/

        when:
        executer.withStackTraceChecksDisabled()
        executer.withFullDeprecationStackTraceDisabled()
        withBuildCache().succeeds "customTask"
        then:
        output ==~ /(?s).*org\.gradle\.api\.GradleException: Could not pack property 'outputFile': ${errorPattern}.*/ ||
            output ==~ /(?s).*org\.gradle\.caching\.BuildCacheException: Unable to store entry at .*: ${errorPattern}.*/
    }

    def "build does not fail if corrupted cache entry is loaded"() {
        withSimpleCacheableTask()
        httpBuildCacheServer.truncateContentAfterPutBytes(1024)
        startServer()

        withBuildCache().succeeds "customTask"
        succeeds "clean"

        when:
        executer.withStackTraceChecksDisabled()
        withBuildCache().succeeds "customTask"

        then:
        executedTasks == [":customTask"]
        output =~ /Could not load entry \w+ from remote build cache/
    }

    def "task is not incremental after corrupted cache entry is loaded"() {
        withIncrementalCacheableTask()
        httpBuildCacheServer.truncateContentAfterPutBytes(1024)
        startServer()

        withBuildCache().succeeds "incrementalTask"
        file("src/input.txt") << " changed"

        when:
        executer.withStackTraceChecksDisabled()
        withBuildCache().succeeds "incrementalTask", "-PexpectIncremental=false"

        then:
        executedTasks == [":incrementalTask"]
        output =~ /Could not load entry \w+ from remote build cache/
    }

    private void startServer() {
        httpBuildCacheServer.start()
        settingsFile << useHttpBuildCache(httpBuildCacheServer.uri)
    }

    def withSimpleCacheableTask() {
        buildFile << """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @Input
                Long fileSize = 1024 * 1024
            
                @OutputFile
                File outputFile
            
                @TaskAction
                void createFile() {
                    outputFile.withOutputStream { OutputStream out ->
                        def random = new Random()
                        def buffer = new byte[fileSize]
                        random.nextBytes(buffer)
                        out.write(buffer)
                    }
                }
            }
            
            task customTask(type: CustomTask) {
                outputFile = file('build/outputFile.bin')
            }
        """
    }

    def withIncrementalCacheableTask() {
        file("src/input.txt").text = "Input"
        buildFile << """
            @CacheableTask
            class IncrementalTask extends DefaultTask {
                @Input
                Long fileSize = 1024 * 1024
                
                @InputDirectory
                File sourceDir
            
                @OutputFile
                File outputFile
            
                @TaskAction
                void createFile(IncrementalTaskInputs inputs) {
                    if (project.hasProperty("expectIncremental")) {
                        def expectIncremental = Boolean.parseBoolean(project.property("expectIncremental"))
                        assert inputs.incremental == expectIncremental
                    }
                    outputFile.withOutputStream { OutputStream out ->
                        def random = new Random()
                        def buffer = new byte[fileSize]
                        random.nextBytes(buffer)
                        out.write(buffer)
                    }
                }
            }
            
            task incrementalTask(type: IncrementalTask) {
                sourceDir = file("src")
                outputFile = file('build/outputFile.bin')
            }
        """
    }
}
