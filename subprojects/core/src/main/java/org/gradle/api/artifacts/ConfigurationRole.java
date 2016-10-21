/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;

/**
 * A configuration role defines the role of a configuration during dependency resolution.
 */
@Incubating
public enum ConfigurationRole {
    FOR_BUILDING_OR_PUBLISHING("can be used when building or publishing", true, true),
    FOR_BUILDING_ONLY("can be used only when building", true, false),
    FOR_PUBLISHING_ONLY("can be used only when publishing the project", false, true);

    private final String description;
    private final boolean canBeUsedForBuilding;
    private final boolean canBeUsedForPublishing;

    ConfigurationRole(String desc, boolean canBeUsedForBuilding, boolean canBeUsedForPublishing) {
        this.description = desc;
        this.canBeUsedForBuilding = canBeUsedForBuilding;
        this.canBeUsedForPublishing = canBeUsedForPublishing;
    }

    public String getDescription() {
        return description;
    }

    public boolean canBeUsedForBuilding() {
        return canBeUsedForBuilding;
    }

    public boolean canBeUsedForPublishing() {
        return canBeUsedForPublishing;
    }
}
