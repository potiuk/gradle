/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.notations;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.ProjectDependenciesBuildInstruction;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;

/**
 * by Szczepan Faber, created at: 11/10/11
 */
public class ProjectDependencyNotationParser extends TypedNotationParser<Project, ProjectDependency> {

    private final ProjectDependenciesBuildInstruction instruction;
    private final Instantiator instantiator;

    public ProjectDependencyNotationParser(ProjectDependenciesBuildInstruction instruction, Instantiator instantiator) {
        super(Project.class);
        this.instruction = instruction;
        this.instantiator = instantiator;
    }

    public ProjectDependency parseType(Project notation) {
        return instantiator.newInstance(DefaultProjectDependency.class, notation, instruction);
    }
}
