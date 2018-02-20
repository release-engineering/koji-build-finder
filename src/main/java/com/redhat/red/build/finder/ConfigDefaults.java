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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;

public abstract class ConfigDefaults {
    public static final List<String> ARCHIVE_TYPES = Collections.unmodifiableList(Arrays.asList("jar", "xml", "pom", "so", "dll", "dylib"));

    public static final Boolean CHECKSUM_ONLY = Boolean.FALSE;

    public static final KojiChecksumType CHECKSUM_TYPE = KojiChecksumType.md5;

    public static final String CONFIG = FileUtils.getUserDirectoryPath() + File.separator + ".koji-build-finder" + File.separator + "config.json";

    public static final List<String> EXCLUDES = Collections.unmodifiableList(Arrays.asList("^(?!.*/pom\\.xml$).*/.*\\.xml$"));

    public static final String KOJI_HUB_URL = "http://kojihub.my.host/kojihub";

    public static final String KOJI_WEB_URL = "https://kojiweb.my.host/koji";
}
