/*
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.red.build.finder.report;

import static j2html.TagCreator.attrs;
import static j2html.TagCreator.caption;
import static j2html.TagCreator.strong;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.text;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

import java.io.File;
import java.util.List;

import com.redhat.red.build.finder.BuildStatistics;
import com.redhat.red.build.finder.KojiBuild;

import j2html.tags.ContainerTag;

public class BuildStatisticsReport extends Report {
    private BuildStatistics buildStatistics;

    public BuildStatisticsReport(File outputDirectory, List<KojiBuild> builds) {
        setOutputDirectory(outputDirectory);
        setName("Statistics");
        setDescription("Number of builds/artifacts built from source");
        setBaseFilename("statistics");

        buildStatistics = new BuildStatistics(builds);
    }

    public BuildStatistics getBuildStatistics() {
        return buildStatistics;
    }

    @Override
    public ContainerTag toHTML() {
        return table(attrs("#table-" + getBaseFilename()),
                caption(text(getName())),
                thead(tr(th(), th(text("Built")), th(text("Imported")), th(text("Total")), th(text("Percent built from source")), th(text("Percent imported")))),
                tbody(tr(td(strong(text("Builds"))), td(text(String.valueOf(buildStatistics.getNumberOfBuilds() - buildStatistics.getNumberOfImportedBuilds()))), td(text(String.valueOf(buildStatistics.getNumberOfImportedBuilds()))), td(text(String.valueOf(buildStatistics.getNumberOfBuilds()))), td(text((100.00 - buildStatistics.getPercentOfBuildsImported()) + "%")), td(text(buildStatistics.getPercentOfBuildsImported() + "%"))),
                      tr(td(strong(text("Archives"))), td(text(String.valueOf(buildStatistics.getNumberOfArchives() - buildStatistics.getNumberOfImportedArchives()))), td(text(String.valueOf(buildStatistics.getNumberOfImportedArchives()))), td(text(String.valueOf(buildStatistics.getNumberOfArchives()))), td(text((100.00 - buildStatistics.getPercentOfArchivesImported()) + "%")), td(text(buildStatistics.getPercentOfArchivesImported() + "%")))));
    }
}
