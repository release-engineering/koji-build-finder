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
import static j2html.TagCreator.each;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.text;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.red.build.finder.KojiBuild;

import j2html.tags.ContainerTag;

public class GAVReport extends Report {
    private List<String> gavs;

    public GAVReport(File outputDirectory, List<KojiBuild> builds) {
        setName("Maven artifacts");
        setDescription("List of Maven artifacts in standard Maven <groupId>:<artifactId>:<version> format");
        setBaseFilename("gav");
        setOutputDirectory(outputDirectory);

        this.gavs = builds.stream().filter(b -> b.getBuildInfo().getId() > 0).filter(b -> b.getTypes() != null && b.getTypes().contains("maven")).flatMap(b -> b.getArchives().stream()).map(a -> a.getArchive().getGroupId() + ":" + a.getArchive().getArtifactId() + ":" + a.getArchive().getVersion()).sorted().distinct().collect(Collectors.toList());
    }

    @Override
    public String renderText() {
        return this.gavs.stream().map(Object::toString).collect(Collectors.joining("\n"));
    }

    @Override
    public ContainerTag toHTML() {
        return table(attrs("#table-" + getBaseFilename()), caption(text(getName())), thead(tr(th(text("<groupId>-<artifactId>-<version>")))), tbody(each(gavs, i -> tr(td(text(i))))));
    }
}
