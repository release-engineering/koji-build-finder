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

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import j2html.tags.ContainerTag;

public abstract class Report {
    private String name;

    private String description;

    private String baseFilename;

    private File outputDirectory;

    public String renderText() {
        return null;
    }

    public void outputText() throws IOException {
        String text = renderText();

        if (text == null) {
            return;
        }

        FileUtils.writeStringToFile(new File(outputDirectory, baseFilename + ".txt"), text, "UTF-8", false);
    }

    public abstract ContainerTag toHTML();

    public void outputHTML() throws IOException {
        String html = renderText();

        if (html == null) {
            return;
        }

        FileUtils.writeStringToFile(new File(outputDirectory, baseFilename + ".html"), html, "UTF-8", false);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseFilename() {
        return baseFilename;
    }

    public void setBaseFilename(String baseFilename) {
        this.baseFilename = baseFilename;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
}
