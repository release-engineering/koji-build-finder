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

import java.util.List;
import java.util.stream.Collectors;

import com.redhat.red.build.finder.KojiBuild;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;

public class GAVReport extends Report {
    private List<String> gavs;

    public GAVReport(List<KojiBuild> builds) {
        List<KojiBuildInfo> buildInfos = builds.stream().filter(b -> b.getBuildInfo() != null && b.getTypes() != null && b.getTypes().contains("maven")).map(KojiBuild::getBuildInfo).collect(Collectors.toList());
        this.gavs = buildInfos.stream().map(b -> b.getMavenGroupId() + ":" + b.getMavenArtifactId() + ":" + b.getMavenVersion()).collect(Collectors.toList());
        this.gavs.sort(String::compareToIgnoreCase);
    }

    @Override
    public String render() {
        return this.gavs.stream().map(Object::toString).collect(Collectors.joining("\n"));
    }
}
