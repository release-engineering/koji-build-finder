/**
 * Copyright 2017 Red Hat, Inc.
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
import static j2html.TagCreator.body;
import static j2html.TagCreator.document;
import static j2html.TagCreator.each;
import static j2html.TagCreator.footer;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.h2;
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
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.redhat.red.build.finder.KojiBuild;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import j2html.attributes.Attr;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.apache.commons.io.FileUtils;

public class HTMLReport implements Report {
    private static final String HTML_STYLE = "html { font-family: sans-serif; } "
            + "table { width: 100%; border-style: solid; border-width: 1px; border-collapse: collapse; } "
            + "tr:nth-child(even) { background-color: lightgrey; } td { text-align: left; vertical-align: top; } "
            + "th { border-style: solid; border-width: 1px; background-color: darkgrey; text-align: left; font-weight: bold; } "
            + "tr, td { border-style: solid; border-width: 1px; font-size: small; }";
    private static final String HTML_FILENAME = "output.html";

    private final String outputDir;

    private final String kojiwebUrl;

    private List<KojiBuild> builds;

    private Collection<File> files;

    public HTMLReport(String outputDir, Collection<File> files, List<KojiBuild> builds, String kojiwebUrl) {
        this.outputDir = outputDir;
        this.files = files;
        this.builds = builds;
        this.kojiwebUrl = kojiwebUrl;
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
        boolean error = build.isImport();

        if (id > 0) {
            return a().withHref(kojiwebUrl + "/archiveinfo?archiveID=" + id).with(error ? errorText(name) : text(name));
        } else {
            return span().with(error ? errorText(name) : text(name));
        }
    }

    private Tag<?> linkTag(int id, String name) {
        return a().withHref(kojiwebUrl + "/taginfo?tagID=" + id).with(text(name));
    }

    private Tag<?> linkSource(String source) {
        return span(source);
    }

    @Override
    public void render() throws IOException {
        double numImports = builds.stream().filter(b -> b.getBuildInfo().getId() > 0 && b.isImport()).count();
        double percentImported = 0;
        int buildsSize = builds.size() - 1;

        if (buildsSize > 0) {
            percentImported = (numImports / buildsSize) * 100;
        }

        String percentBuiltStr = String.format("%.1f", (100.00 - percentImported));

        String output = document().render()
                     + html().with(
                        head().with(style().withText(HTML_STYLE)).with(
                            title().withText("Build Report")
                        ),
                        body().with(
                            header().with(
                                h1().with(
                                    text("Build Report for "), each(files, file -> text(file.getName() + " ")),
                                    text("(" + buildsSize + " builds, " + percentBuiltStr + "% built from source)")
                                )
                            ),
                            main().with(
                              h2("Builds found in distribution"),
                              table(thead(tr().with(th("#"), th("ID"), th("Name"), th("Version"), th("Artifacts"), th("Tags"), th("Type"), th("Sources"), th("Patches"), th("SCM URL"), th("Options"), th("Extra"))),
                                    tbody(each(builds, build ->
                                          tr(td().with(text(Integer.toString(builds.indexOf(build))),
                                          td().with(build.getBuildInfo().getId() > 0 ? linkBuild(build.getBuildInfo().getId()) : errorText(String.valueOf(build.getBuildInfo().getId()))),
                                          td().with(build.getBuildInfo().getId() > 0 ? linkPackage(build.getBuildInfo().getPackageId(), build.getBuildInfo().getName()) : text(""))),
                                          td().with(build.getBuildInfo().getId() > 0 ? text(build.getBuildInfo().getVersion().replace('_', '-')) : text("")),
                                          td().with(build.getArchives() != null ? ol().with(each(build.getArchives(), archive -> li(linkArchive(build, archive.getArchive()), text(": "), each(archive.getFiles(), file -> text(archive.getFiles().indexOf(file) != (archive.getFiles().size() - 1) ? file + ", " : file))))) : text("")),
                                          td().with(build.getTags() != null ? ul().with(each(build.getTags(), tag -> li(linkTag(tag.getId(), tag.getName())))) : text("")),
                                          td().with(build.getType() != null ? text(build.getType()) : (build.getBuildInfo().getId() > 0 ? errorText("imported build") : text(""))),
                                          td().with(build.getSourcesZip() != null ? linkArchive(build, build.getSourcesZip()) : text("")),
                                          td().with(build.getPatchesZip() != null ? linkArchive(build, build.getPatchesZip()) : text("")),
                                          td().with(build.getSource() != null ? linkSource(build.getSource()) : (build.getBuildInfo().getId() == 0 ? text("") : errorText("missing URL"))),
                                          td().with(build.getBuildInfo().getTaskId() != null ? each(build.getTaskRequest().asMavenBuildRequest().getProperties().entrySet(), entry -> text(entry.getKey() + (entry.getValue() != null ? ("=" + entry.getValue() + "; ") : "; "))) : text("")),
                                          td().with(build.getBuildInfo().getExtra() != null ? each(build.getBuildInfo().getExtra().entrySet(), entry -> text(entry.getKey() + (entry.getValue() != null ? ("=" + entry.getValue() + "; ") : "; "))) : text(""))
                                       ))
                                   )
                                )
                            ),
                              footer().attr(Attr.CLASS, "footer").attr(Attr.ID, "footer").withText("Created: " + new Date())
                        )
                    ).renderFormatted();
        FileUtils.writeStringToFile(new File(outputDir + HTML_FILENAME), output, "UTF-8", false);
    }
}
