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
package com.redhat.red.build.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

import com.redhat.red.build.finder.report.BuildStatisticsReport;
import com.redhat.red.build.finder.report.GAVReport;
import com.redhat.red.build.finder.report.HTMLReport;
import com.redhat.red.build.finder.report.NVRReport;

public class ReportTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Test
    public void verifyReports() throws IOException {
        File folder = temp.newFolder();

        File buildsFile = TestUtils.loadFile("report-test/builds.json");
        List<File> files = Collections.unmodifiableList(Collections.emptyList());
        Map<Integer, KojiBuild> builds = JSONUtils.loadBuildsFile(buildsFile);

        File newBuildsFile = new File(folder, "builds.json");
        JSONUtils.dumpObjectToFile(builds, newBuildsFile);

        String buildsString = FileUtils.readFileToString(buildsFile, "UTF-8").replaceAll("\\s", "");
        String newBuildsString = FileUtils.readFileToString(newBuildsFile, "UTF-8").replaceAll("\\s", "");

        assertEquals(newBuildsString, buildsString);

        List<KojiBuild> buildList = new ArrayList<>(builds.values());
        Collections.sort(buildList, (b1, b2) -> Integer.compare(b1.getBuildInfo().getId(), b2.getBuildInfo().getId()));
        buildList = Collections.unmodifiableList(buildList);

        assertEquals(builds.size(), buildList.size());

        final String nvrExpected = "artemis-native-linux-2.3.0.amq_710003-1.redhat_1.el6\ncommons-beanutils-commons-beanutils-1.9.2.redhat_1-1\ncommons-lang-commons-lang-2.6-1\ncommons-lang-commons-lang-2.6-2\norg.wildfly.swarm-config-api-parent-1.1.0.Final_redhat_14-1";
        NVRReport nvrReport = new NVRReport(folder, buildList);
        assertEquals(nvrReport.renderText(), nvrExpected);
        nvrReport.outputText();
        assertEquals(FileUtils.readFileToString(new File(folder, nvrReport.getBaseFilename() + ".txt"), "UTF-8"), nvrExpected);

        final String gavExpected = "artemis-native-linux:artemis-native-linux-repolib:2.3.0.amq_710003-1.redhat_1.el6\ncommons-beanutils:commons-beanutils:1.9.2.redhat-1\ncommons-lang:commons-lang:2.6\ncommons-lang:commons-lang:2.6\norg.wildfly.swarm:config-api-parent:1.1.0.Final-redhat-14";
        GAVReport gavReport = new GAVReport(folder, buildList);
        assertEquals(gavReport.renderText(), gavExpected);
        gavReport.outputText();
        assertEquals(FileUtils.readFileToString(new File(folder, gavReport.getBaseFilename() + ".txt"), "UTF-8"), gavExpected);

        BuildStatisticsReport buildStatisticsReport = new BuildStatisticsReport(folder, buildList);
        buildStatisticsReport.outputText();

        HTMLReport htmlReport = new HTMLReport(folder, files, buildList, ConfigDefaults.KOJI_WEB_URL, new ArrayList<>());
        htmlReport.outputHTML();
        assertTrue(FileUtils.readFileToString(new File(folder, htmlReport.getBaseFilename() + ".html"), "UTF-8").contains("<html>"));

        assertTrue(buildList.get(0).isImport());
        assertNull(buildList.get(0).getSourcesZip());
        assertNull(buildList.get(0).getPatchesZip());
        assertNull(buildList.get(0).getProjectSourcesTgz());
        assertNull(buildList.get(0).getDuplicateArchives());
        assertNotNull(buildList.get(0).toString());

        assertTrue(buildList.get(1).isImport());
        assertNull(buildList.get(1).getSourcesZip());
        assertNull(buildList.get(1).getPatchesZip());
        assertNull(buildList.get(1).getProjectSourcesTgz());
        assertEquals(buildList.get(1).getDuplicateArchives().size(), 1);
        assertNotNull(buildList.get(1).toString());

        assertTrue(buildList.get(2).isImport());
        assertNull(buildList.get(2).getSourcesZip());
        assertNull(buildList.get(2).getPatchesZip());
        assertNull(buildList.get(2).getProjectSourcesTgz());
        assertEquals(buildList.get(2).getDuplicateArchives().size(), 1);
        assertNotNull(buildList.get(2).toString());
        assertNotNull(buildList.get(2).getDuplicateArchives().get(0));

        assertTrue(buildList.get(3).isMaven());
        assertTrue(buildList.get(3).getTypes().contains("maven"));
        assertNotNull(buildList.get(3).getSource());
        assertNotNull(buildList.get(3).getSourcesZip());
        assertNotNull(buildList.get(3).getPatchesZip());
        assertNotNull(buildList.get(3).getProjectSourcesTgz());
        assertNotNull(buildList.get(3).getTaskRequest().asMavenBuildRequest().getProperties());
        assertNull(buildList.get(3).getDuplicateArchives());
        assertNotNull(buildList.get(3).toString());

        assertTrue(buildList.get(4).isMaven());
        assertNotNull(buildList.get(4).getSource());
        assertNull(buildList.get(4).getSourcesZip());
        assertNull(buildList.get(4).getPatchesZip());
        assertNotNull(buildList.get(4).getProjectSourcesTgz());
        assertNotNull(buildList.get(4).getBuildInfo().getExtra());
        assertTrue((buildList.get(4).getMethod().equals("PNC")));
        assertNull(buildList.get(4).getDuplicateArchives());
        assertNotNull(buildList.get(4).toString());

        assertFalse(buildList.get(5).isMaven());
        assertNotNull(buildList.get(5).getSource());
        assertNull(buildList.get(5).getSourcesZip());
        assertNull(buildList.get(5).getPatchesZip());
        assertNull(buildList.get(5).getProjectSourcesTgz());
        assertNull(buildList.get(5).getDuplicateArchives());
        assertNotNull(buildList.get(5).toString());

        assertEquals(buildList.size() - 1, buildStatisticsReport.getBuildStatistics().getNumberOfBuilds());
        assertEquals(2, buildStatisticsReport.getBuildStatistics().getNumberOfImportedBuilds());
        assertEquals(5, buildStatisticsReport.getBuildStatistics().getNumberOfArchives());
        assertEquals(2, buildStatisticsReport.getBuildStatistics().getNumberOfImportedArchives());
        assertEquals(((double) 2 / (double) 5) * 100.00, buildStatisticsReport.getBuildStatistics().getPercentOfBuildsImported(), 0);
        assertEquals(((double) 2 / (double) 5) * 100.00, buildStatisticsReport.getBuildStatistics().getPercentOfArchivesImported(), 0);
    }
}
