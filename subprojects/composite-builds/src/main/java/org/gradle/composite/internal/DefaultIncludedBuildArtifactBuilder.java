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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.FileCollection;
import org.gradle.includedbuild.internal.IncludedBuildArtifactBuilder;
import org.gradle.includedbuild.internal.IncludedBuildTaskGraph;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultIncludedBuildArtifactBuilder implements IncludedBuildArtifactBuilder {
    private final List<CompositeProjectComponentArtifactMetadata> artifacts = Lists.newArrayList();
    private final IncludedBuildTaskGraph includedBuildTaskGraph;

    public DefaultIncludedBuildArtifactBuilder(IncludedBuildTaskGraph includedBuildTaskGraph) {
        this.includedBuildTaskGraph = includedBuildTaskGraph;
    }

    @Override
    public FileCollection buildAll(BuildIdentifier currentBuild, final ResolvableDependencies dependencies) {
        // Collect the included build artifacts
        final List<CompositeProjectComponentArtifactMetadata> includedBuildArtifacts = Lists.newArrayList();
        ArtifactCollection artifacts = dependencies.getArtifacts();
        for (ResolvedArtifactResult artifactResult : artifacts.getArtifacts()) {
            ComponentArtifactIdentifier componentArtifactIdentifier = artifactResult.getId();
            if (componentArtifactIdentifier instanceof CompositeProjectComponentArtifactMetadata) {
                CompositeProjectComponentArtifactMetadata compositeBuildArtifact = (CompositeProjectComponentArtifactMetadata) componentArtifactIdentifier;
                includedBuildArtifacts.add(compositeBuildArtifact);
            }
        }

        if (includedBuildArtifacts.isEmpty()) {
            return artifacts.getArtifactFiles();
        }

        // Get the graph of builds
        final Multimap<BuildIdentifier, BuildIdentifier> requestingBuilds = getBuildGraph(dependencies);
        for (CompositeProjectComponentArtifactMetadata artifact : includedBuildArtifacts) {
            BuildIdentifier targetBuild = getBuildIdentifier(artifact);
            Collection<BuildIdentifier> buildIdentifiers = requestingBuilds.get(targetBuild);
            if (buildIdentifiers.isEmpty()) {
                buildIdentifiers = Collections.singleton(currentBuild);
            }
            for (BuildIdentifier requestingBuild : buildIdentifiers) {
                for (String taskName : artifact.getTasks()) {
                    includedBuildTaskGraph.addTask(requestingBuild, targetBuild, taskName);
                }
            }
        }

        // Actually build the artifacts
        for (CompositeProjectComponentArtifactMetadata artifact : includedBuildArtifacts) {
            BuildIdentifier targetBuild = getBuildIdentifier(artifact);
            for (String taskName : artifact.getTasks()) {
                includedBuildTaskGraph.awaitCompletion(targetBuild, taskName);
            }
        }
        return artifacts.getArtifactFiles();
    }

    private Multimap<BuildIdentifier, BuildIdentifier> getBuildGraph(ResolvableDependencies dependencies) {
        final Multimap<BuildIdentifier, BuildIdentifier> requestingBuilds = LinkedHashMultimap.create();
        ResolutionResult resolutionResult = dependencies.getResolutionResult();
        resolutionResult.allDependencies(new Action<DependencyResult>() {
            @Override
            public void execute(DependencyResult dependencyResult) {
                if (dependencyResult instanceof ResolvedDependencyResult) {
                    ResolvedDependencyResult rdr = (ResolvedDependencyResult) dependencyResult;
                    ComponentIdentifier from = rdr.getFrom().getId();
                    ComponentIdentifier to = rdr.getSelected().getId();
                    if (from instanceof ProjectComponentIdentifier && to instanceof ProjectComponentIdentifier) {
                        requestingBuilds.put(((ProjectComponentIdentifier) to).getBuild(), ((ProjectComponentIdentifier) from).getBuild());
                    }
                }
            }
        });
        return requestingBuilds;
    }

    private BuildIdentifier getBuildIdentifier(CompositeProjectComponentArtifactMetadata artifact) {
        return artifact.getComponentId().getBuild();
    }

}
