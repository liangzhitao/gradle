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

package org.gradle.internal.logging.console;

import com.google.common.collect.Lists;
import org.gradle.internal.logging.events.BatchOutputEventListener;
import org.gradle.internal.logging.events.BuildHealth;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.Span;
import org.gradle.internal.logging.text.Style;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.time.TimeProvider;

public class BuildStatusRenderer extends BatchOutputEventListener {
    public static final String BUILD_PROGRESS_CATEGORY = "org.gradle.internal.progress.BuildProgressLogger";
    private final TersePrettyDurationFormatter elapsedTimeFormatter = new TersePrettyDurationFormatter();
    private final BatchOutputEventListener listener;
    private final StyledLabel buildStatusLabel;
    private final Console console;
    private final ConsoleMetaData consoleMetaData;
    private final TimeProvider timeProvider;

    // What actually shows up on the console
    private String currentBuildStatus;
    private String currentProgressIndicator = "";
    private Style.Color currentBuildStatusColor = Style.Color.GREEN;
    private long currentPhaseProgressOperationId;

    // Used to maintain timer
    private long buildStartTimestamp;
    private boolean timerEnabled;

    public BuildStatusRenderer(BatchOutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData, TimeProvider timeProvider) {
        this.listener = listener;
        this.buildStatusLabel = buildStatusLabel;
        this.console = console;
        this.consoleMetaData = consoleMetaData;
        this.timeProvider = timeProvider;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            ProgressStartEvent startEvent = (ProgressStartEvent) event;
            if (startEvent.getBuildOperationId() != null && startEvent.getParentBuildOperationId() == null) {
                buildStarted(startEvent);
            } else if (BUILD_PROGRESS_CATEGORY.equals(startEvent.getCategory())) {
                phaseStarted(startEvent);
            }
        } else if (event instanceof ProgressCompleteEvent) {
            ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
            if (isPhaseProgressEvent(completeEvent.getProgressOperationId())) {
                phaseEnded(completeEvent);
            }
        } else if (event instanceof ProgressEvent) {
            ProgressEvent progressEvent = (ProgressEvent) event;
            if (isPhaseProgressEvent(progressEvent.getProgressOperationId())) {
                phaseProgressed(progressEvent);
            }
        }
    }

    @Override
    public void onOutput(Iterable<OutputEvent> events) {
        super.onOutput(events);
        listener.onOutput(events);
        renderNow(timeProvider.getCurrentTime());
    }

    private String trimToConsole(int prefixLength, String str) {
        int consoleWidth = consoleMetaData.getCols() - 1;
        int remainingWidth = consoleWidth - prefixLength;

        if (consoleWidth < 0) {
            return str;
        }
        if (remainingWidth <= 0) {
            return "";
        }
        if (consoleWidth < str.length()) {
            return str.substring(0, consoleWidth);
        }
        return str;
    }

    private void renderNow(long now) {
        if (currentBuildStatus != null && !currentBuildStatus.isEmpty()) {
            final String buildProgressToRender = trimToConsole(0, currentProgressIndicator);
            final String buildStatusToRender = trimToConsole(buildProgressToRender.length(), formatBuildStatus(currentBuildStatus, timerEnabled, now - buildStartTimestamp));
            buildStatusLabel.setText(Lists.newArrayList(
                new Span(Style.of(Style.Emphasis.BOLD, currentBuildStatusColor), buildProgressToRender),
                new Span(Style.of(Style.Emphasis.BOLD), buildStatusToRender)));
        }
        console.flush();
    }

    private String formatBuildStatus(String prefix, boolean timerEnabled, long elapsedTime) {
        if (timerEnabled) {
            return prefix + " [" + elapsedTimeFormatter.format(elapsedTime) + "]";
        }
        return prefix;
    }

    private boolean isPhaseProgressEvent(OperationIdentifier progressOpId) {
        return progressOpId.getId() == currentPhaseProgressOperationId;
    }

    private void buildStarted(ProgressStartEvent startEvent) {
        buildStartTimestamp = timeProvider.getCurrentTime();
    }

    private void phaseStarted(ProgressStartEvent progressStartEvent) {
        timerEnabled = true;
        currentPhaseProgressOperationId = progressStartEvent.getProgressOperationId().getId();
        currentBuildStatus = progressStartEvent.getShortDescription();
    }

    private void phaseProgressed(ProgressEvent progressEvent) {
        currentBuildStatus = progressEvent.getStatus();
        currentProgressIndicator = progressEvent.getProgressIndicator();
        if (progressEvent.getBuildHealth() == BuildHealth.FAILING) {
            currentBuildStatusColor = Style.Color.RED;
        }
    }

    private void phaseEnded(ProgressCompleteEvent progressCompleteEvent) {
        currentBuildStatus = progressCompleteEvent.getStatus();
        timerEnabled = false;
    }
}
