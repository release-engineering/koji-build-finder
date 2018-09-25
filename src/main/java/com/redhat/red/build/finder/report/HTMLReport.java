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

import static j2html.TagCreator.a;
import static j2html.TagCreator.attrs;
import static j2html.TagCreator.body;
import static j2html.TagCreator.caption;
import static j2html.TagCreator.div;
import static j2html.TagCreator.document;
import static j2html.TagCreator.each;
import static j2html.TagCreator.filter;
import static j2html.TagCreator.footer;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.head;
import static j2html.TagCreator.header;
import static j2html.TagCreator.html;
import static j2html.TagCreator.li;
import static j2html.TagCreator.main;
import static j2html.TagCreator.ol;
import static j2html.TagCreator.span;
import static j2html.TagCreator.style;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.text;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.title;
import static j2html.TagCreator.tr;
import static j2html.TagCreator.ul;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.red.build.finder.BuildFinder;
import com.redhat.red.build.finder.KojiBuild;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;

import j2html.attributes.Attr;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;

public class HTMLReport extends Report {
    private static final String NAME = "Koji Build Finder";

    private static final String HTML_STYLE = ""
            + "body { font-family: Verdana, Helvetica, Arial, sans-serif; font-size: 13px; }\n"
            + "table { width: 100%; border-style: solid; border-width: 1px; border-collapse: collapse; }\n"
            + "caption { background: lemonchiffon; caption-side: top; font-weight: bold; font-size: larger; text-align: left; margin-top: 50px; }\n"
            + "th { border-style: solid; border-width: 1px; background-color: darkgrey; text-align: left; font-weight: bold; }\n"
            + "tr { border-style: solid; border-width: 1px; }\n"
            + "tr:nth-child(even) { background-color: lightgrey; }\n"
            + "td { border-style: solid; border-width: 1px; text-align: left; vertical-align: top; font-size: small; }\n"
            + "footer { font-size: smaller; }";

    private URL kojiwebUrl;

    private List<KojiBuild> builds;

    private List<Report> reports;

    public HTMLReport(File outputDirectory, Collection<File> files, List<KojiBuild> builds, URL kojiwebUrl, List<Report> reports) {
        setName("Build Report for " + String.join(", ", files.stream().map(File::getName).collect(Collectors.toList())));
        setDescription("List of analyzed artifacts whether or not they were found in a Koji build");
        setBaseFilename("output");
        setOutputDirectory(outputDirectory);

        this.builds = builds;
        this.kojiwebUrl = kojiwebUrl;
        this.reports = reports;
    }

    private static ContainerTag errorText(String text) {
        return span(text).withStyle("color: red; font-weight: bold;");
    }

    private Tag<?> linkBuild(int id) {
        return a().withHref(kojiwebUrl + "/buildinfo?buildID=" + id).with(text(Integer.toString(id)));
    }

    private Tag<?> linkPackage(int id, String name) {
        return a().withHref(kojiwebUrl + "/packageinfo?packageID=" + id).with(text(name));
    }

    private Tag<?> linkArchive(KojiBuild build, KojiArchiveInfo archive) {
        int id = archive.getArchiveId();
        String name = archive.getFilename();
        boolean error = (build.isImport() || id <= 0);
        return error ? errorText(name) : a().withHref(kojiwebUrl + "/archiveinfo?archiveID=" + id).with(text(name));
    }

    private Tag<?> linkTag(int id, String name) {
        return a().withHref(kojiwebUrl + "/taginfo?tagID=" + id).with(text(name));
    }

    private Tag<?> linkSource(String source) {
        return span(source);
    }

    @Override
    public ContainerTag toHTML() {
        return html(
                head(style().withText(HTML_STYLE)).with(
                        title().withText(getName())
                    ),
                    body().with(
                        header(
                            h1(getName())
                        ),
                        main(
                          div(attrs("#div-reports"), table(caption(text("Reports")), thead(tr(th(text("Name")), th(text("Description")))), tbody(tr(td(a().withHref("#div-" + getBaseFilename()).with(text("Builds"))), td(text(getDescription()))), each(reports, report -> tr(td(a().withHref("#div-" + report.getBaseFilename()).with(text(report.getName()))), td(text(report.getDescription()))))))),
                          div(attrs("#div-" + getBaseFilename()),
                          table(caption(text("Builds")), thead(tr(th(text("#")), th(text("ID")), th(text("Name")), th(text("Version")), th(text("Artifacts")), th(text("Tags")), th(text("Type")), th(text("Sources")), th(text("Patches")), th(text("SCM URL")), th(text("Options")), th(text("Extra")))),
                                tbody(each(filter(builds, build -> build.getBuildInfo().getId() > 0 || (build.getBuildInfo().getId() == 0 && build.getArchives() != null)), build ->
                                      tr(
                                      td(text(Integer.toString(builds.indexOf(build)))),
                                      td(build.getBuildInfo().getId() > 0 ? linkBuild(build.getBuildInfo().getId()) : errorText(String.valueOf(build.getBuildInfo().getId()))),
                                      td(build.getBuildInfo().getId() > 0 ? linkPackage(build.getBuildInfo().getPackageId(), build.getBuildInfo().getName()) : text("")),
                                      td(build.getBuildInfo().getId() > 0 ? text(build.getBuildInfo().getVersion().replace('_', '-')) : text("")),
                                      td(build.getArchives() != null ? ol(each(build.getArchives(), archive -> li(linkArchive(build, archive.getArchive()), text(": "), text(String.join(", ", archive.getFiles()))))) : text("")),
                                      td(build.getTags() != null ? ul(each(build.getTags(), tag -> li(linkTag(tag.getId(), tag.getName())))) : text("")),
                                      td(build.getMethod() != null ? text(build.getMethod()) : (build.getBuildInfo().getId() > 0 ? errorText("imported build") : text(""))),
                                      td(build.getScmSourcesZip() != null ? linkArchive(build, build.getScmSourcesZip()) : text("")),
                                      td(build.getPatchesZip() != null ? linkArchive(build, build.getPatchesZip()) : text("")),
                                      td(build.getSource() != null ? linkSource(build.getSource()) : (build.getBuildInfo().getId() == 0 ? text("") : errorText("missing URL"))),
                                      td(build.getTaskInfo() != null && build.getTaskInfo().getMethod() != null && build.getTaskInfo().getMethod().equals("maven") && build.getTaskRequest() != null && build.getTaskRequest().asMavenBuildRequest().getProperties() != null && build.getTaskRequest().asMavenBuildRequest() != null ? each(build.getTaskRequest().asMavenBuildRequest().getProperties().entrySet(), entry -> text(entry.getKey() + (entry.getValue() != null ? ("=" + entry.getValue() + "; ") : "; "))) : text("")),
                                      td(build.getBuildInfo().getExtra() != null ? each(build.getBuildInfo().getExtra().entrySet(), entry -> text(entry.getKey() + (entry.getValue() != null ? ("=" + entry.getValue() + "; ") : "; "))) : text(""))
                                   ))
                               )
                            )), each(reports, report ->
                                div(attrs("#div-" + report.getBaseFilename()), report.toHTML()))
                        ),
                        div(attrs("#div-footer"), footer().attr(Attr.CLASS, "footer").attr(Attr.ID, "footer").with(text("Created: " + new Date() + " by "), a().withHref("https://github.com/release-engineering/koji-build-finder/").with(text(NAME)), text(" " + BuildFinder.getVersion() + " (SHA: "), a().withHref("https://github.com/release-engineering/koji-build-finder/commit/" + BuildFinder.getScmRevision()).with(text(BuildFinder.getScmRevision() + ")"))))
                    )
                );
    }

    @Override
    public String renderText() {
        return document().render() + toHTML().render();
    }
}
