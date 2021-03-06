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

package org.gradle.api.tasks.diagnostics.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Rule;
import org.gradle.util.GUtil;

import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.gradle.logging.StyledTextOutput.Style.*;

/**
 * <p>A {@code TaskReportRenderer} is responsible for rendering the model of a project task report.</p>
 *
 * @author Hans Dockter
 */
public class TaskReportRenderer extends TextReportRenderer {
    private boolean currentProjectHasTasks;
    private boolean currentProjectHasRules;
    private boolean hasContent;
    private boolean detail;

    @Override
    public void startProject(Project project) {
        currentProjectHasTasks = false;
        currentProjectHasRules = false;
        hasContent = false;
        detail = false;
        super.startProject(project);
    }

    public void showDetail(boolean detail) {
        this.detail = detail;
    }
    
    /**
     * Writes the default task names for the current project.
     *
     * @param defaultTaskNames The default task names (must not be null)
     */
    public void addDefaultTasks(List<String> defaultTaskNames) {
        if (defaultTaskNames.size() > 0) {
            getTextOutput().formatln("Default tasks: %s", GUtil.join(defaultTaskNames, ", "));
            hasContent = true;
        }
    }

    public void startTaskGroup(String taskGroup) {
        if (!GUtil.isTrue(taskGroup)) {
            addSubheading("Tasks");
        } else {
            addSubheading(StringUtils.capitalize(taskGroup) + " tasks");
        }
        currentProjectHasTasks = true;
    }

    /**
     * Writes a task for the current project.
     *
     * @param task The task
     */
    public void addTask(TaskDetails task) {
        writeTask(task, "");
    }

    public void addChildTask(TaskDetails task) {
        if (detail) {
            writeTask(task, "    ");
        }
    }

    private void writeTask(TaskDetails task, String prefix) {
        getTextOutput().text(prefix);
        getTextOutput().withStyle(Identifier).text(task.getPath());
        if (GUtil.isTrue(task.getDescription())) {
            getTextOutput().withStyle(Description).format(" - %s", task.getDescription());
        }
        if (detail) {
            SortedSet<String> sortedDependencies = new TreeSet<String>();
            for (String dependency : task.getDependencies()) {
                sortedDependencies.add(dependency);
            }
            if (sortedDependencies.size() > 0) {
                getTextOutput().withStyle(Info).format(" [%s]", GUtil.join(sortedDependencies, ", "));
            }
        }
        getTextOutput().println();
    }

    private void addSubheading(String header) {
        if (hasContent) {
            getTextOutput().println();
        }
        hasContent = true;
        writeSubheading(header);
    }

    /**
     * Marks the end of the tasks for the current project.
     */
    public void completeTasks() {
        if (!currentProjectHasTasks) {
            getTextOutput().withStyle(Info).println("No tasks");
            hasContent = true;
        }
    }

    /**
     * Writes a rule for the current project.
     *
     * @param rule The rule
     */
    public void addRule(Rule rule) {
        if (!currentProjectHasRules) {
            addSubheading("Rules");
        }
        getTextOutput().println(GUtil.elvis(rule.getDescription(), ""));
        currentProjectHasRules = true;
    }

    @Override
    public void complete() throws IOException {
        if (!detail) {
            getTextOutput().println();
            getTextOutput().text("To see all tasks and more detail, run with ").style(UserInput).text("--all").style(Normal).text(".");
            getTextOutput().println();
        }
        super.complete();
    }
}
