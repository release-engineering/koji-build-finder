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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.finder.KojiBuild;

import j2html.tags.ContainerTag;

public class ProductReport extends Report {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductReport.class);

    private Map<String, List<String>> productMap;

    public ProductReport(File outputDirectory, List<KojiBuild> builds) {
        setName("Products");
        setDescription("List of builds partitioned by product (build target)");
        setBaseFilename("products");
        setOutputDirectory(outputDirectory);

        List<String> targets = builds.stream().filter(b -> b.getBuildInfo() != null && b.getBuildInfo().getId() > 0).filter(b -> b.getTaskRequest() != null && b.getTaskRequest().asBuildRequest() != null && b.getTaskRequest().asBuildRequest().getTarget() != null).map(b -> b.getTaskRequest().asBuildRequest().getTarget()).collect(Collectors.toList());
        Map<String, Long> map = targets.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<Map.Entry<String, Long>> countList = map.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toList());
        Collections.reverse(countList);

        MultiValuedMap<String, KojiBuild> prodMap = new ArrayListValuedHashMap<>();

        for (KojiBuild build : builds) {
            if (build.getTaskRequest() == null || build.getTaskRequest().asBuildRequest() == null || build.getTaskRequest().asBuildRequest().getTarget() == null) {
                  continue;
            }

            for (Entry<String, Long> countEntry : countList) {
                String target = countEntry.getKey();

                if (build.getTaskRequest().asBuildRequest().getTarget().equals(target)) {
                    prodMap.put(targetToProduct(target), build);
                    break;
                }
            }
        }

        Map<String, Collection<KojiBuild>> pm = prodMap.asMap();
        Set<String> keySet = pm.keySet();
        int size = keySet.size();

        LOGGER.debug("Product List ({}):", size);

        this.productMap = new HashMap<>(size);
        Set<Entry<String, Collection<KojiBuild>>> entrySet = pm.entrySet();

        for (Entry<String, Collection<KojiBuild>> entry : entrySet) {
            String target = entry.getKey();
            Collection<KojiBuild> prodBuilds = entry.getValue();
            List<String> buildList = prodBuilds.stream().map(b -> b.getBuildInfo().getNvr()).collect(Collectors.toList());

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{} ({}): {}", target, targetToProduct(target), buildList);
            }

            productMap.put(target, buildList);
        }
    }

    private static String targetToProduct(String tagName) {
        String prodName = tagName.replaceAll("([a-z\\-]+)([0-9.?]+)?(.*)", "$1-$2").replaceAll("-+", " ").replaceAll("(jb|jboss)", "JBoss");
        Iterator<String> it = Arrays.asList(prodName.split(" ")).iterator();

        StringBuilder sb = new StringBuilder();

        while (it.hasNext()) {
            String word = it.next();

            if (word.length() <= 4) {
                sb.append(word.toUpperCase(Locale.ENGLISH));
            } else {
                sb.append(String.valueOf(word.charAt(0)).toUpperCase(Locale.ENGLISH)).append(word.substring(1));
            }

            if (it.hasNext()) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    public Map<String, List<String>> getProductMap() {
        return Collections.unmodifiableMap(productMap);
    }

    @Override
    public ContainerTag toHTML() {
        return table(attrs("#table-" + getBaseFilename()), caption(text(getName())), thead(tr(th(text("Product name")), th(text("Builds")))), tbody(each(this.productMap.entrySet(), entry -> tr(td(text(entry.getKey())), td(text(String.join(", ", entry.getValue())))))));
    }
}
