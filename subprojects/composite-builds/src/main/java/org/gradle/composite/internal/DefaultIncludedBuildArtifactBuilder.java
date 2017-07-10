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
package org.gradle.composite.internal;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.includedbuild.internal.IncludedBuildArtifactBuilder;
import org.gradle.includedbuild.internal.IncludedBuildTaskGraph;

import java.util.List;
import java.util.Set;

public class DefaultIncludedBuildArtifactBuilder implements IncludedBuildArtifactBuilder {
    private final List<CompositeProjectComponentArtifactMetadata> artifacts = Lists.newArrayList();
    private final IncludedBuildTaskGraph includedBuildTaskGraph;

    public DefaultIncludedBuildArtifactBuilder(IncludedBuildTaskGraph includedBuildTaskGraph) {
        this.includedBuildTaskGraph = includedBuildTaskGraph;
    }

    @Override
    public void add(BuildIdentifier requestingBuild, ComponentArtifactIdentifier artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            CompositeProjectComponentArtifactMetadata compositeBuildArtifact = (CompositeProjectComponentArtifactMetadata) artifact;
            artifacts.add(compositeBuildArtifact);

            BuildIdentifier targetBuild = getBuildIdentifier(compositeBuildArtifact);
            Set<String> tasks = compositeBuildArtifact.getTasks();
            for (String taskName : tasks) {
                includedBuildTaskGraph.addTask(requestingBuild, targetBuild, taskName);
            }
        }
    }

    @Override
    public void buildAll() {
        for (CompositeProjectComponentArtifactMetadata artifact : artifacts) {
            BuildIdentifier targetBuild = getBuildIdentifier(artifact);
            for (String taskName : artifact.getTasks()) {
                includedBuildTaskGraph.awaitCompletion(targetBuild, taskName);
            }
        }
    }

    @Override
    public FileCollection buildAll(BuildIdentifier currentBuild, ResolvableDependencies dependencies) {
        ArtifactCollection artifacts = dependencies.getArtifacts();
        for (ResolvedArtifactResult artifactResult : artifacts.getArtifacts()) {
            ComponentArtifactIdentifier componentArtifactIdentifier = artifactResult.getId();
            add(currentBuild, componentArtifactIdentifier);
        }
        buildAll();
        return artifacts.getArtifactFiles();
    }

    private BuildIdentifier getBuildIdentifier(CompositeProjectComponentArtifactMetadata artifact) {
        return artifact.getComponentId().getBuild();
    }

}
