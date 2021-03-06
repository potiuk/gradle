/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.ExecutionFailure
import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.junit.Before
import org.junit.Test
import static org.hamcrest.Matchers.containsString
import static org.junit.Assert.assertThat
import spock.lang.Issue

/**
 * @author Hans Dockter
 */
class WrapperProjectIntegrationTest extends AbstractIntegrationTest {

    @Before
    public void createBuildScript() {
        file("build.gradle") << """
            import org.gradle.api.tasks.wrapper.Wrapper
            task wrapper(type: Wrapper) {
                zipBase = Wrapper.PathBase.PROJECT
                zipPath = 'wrapper'
                archiveBase = Wrapper.PathBase.PROJECT
                archivePath = 'dist'
                distributionUrl = '${distribution.binDistribution.toURI().toURL()}'
                distributionBase = Wrapper.PathBase.PROJECT
                distributionPath = 'dist'
            }

            task hello << {
                println 'hello'
            }

            task echoProperty << {
                println "fooD=" + project.properties["fooD"]
            }
        """
        
        executer.withTasks('wrapper').run()
    }
    
    GradleDistributionExecuter getWrapperExecuter() {
        executer.usingExecutable('gradlew').inDirectory(testDir)
    }
    
    @Test
    public void hasNonZeroExitCodeOnBuildFailure() {
        ExecutionFailure failure = wrapperExecuter.withTasks('unknown').runWithFailure()
        failure.assertHasDescription("Task 'unknown' not found in root project")
    }

    @Test
    public void wrapperSample() {
        ExecutionResult result = wrapperExecuter.withTasks('hello').run()
        assertThat(result.output, containsString('hello'))
    }
    
    @Test
    @Issue("http://issues.gradle.org/browse/GRADLE-1871")
    public void canSpecifyProjectPropertiesContainingD() {
        ExecutionResult result = wrapperExecuter.withArguments("-PfooD=bar").withTasks('echoProperty').run()
        assertThat(result.output, containsString("fooD=bar"))
    }
}
