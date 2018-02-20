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
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.redhat.red.build.koji.model.json.util.KojiObjectMapper;

public final class JSONUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSONUtils.class);

    private JSONUtils() {
        throw new AssertionError();
    }

    public static String dumpString(Object obj) {
        ObjectMapper mapper = new KojiObjectMapper();

        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        try {
            String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            return s;
        } catch (JsonProcessingException e) {
            LOGGER.error("JSON error", e);
        }

        return null;
    }

    public static boolean dumpObjectToFile(Object object, File file) {
        ObjectMapper mapper = new KojiObjectMapper();

        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        try {
            FileUtils.forceMkdirParent(file);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, object);
            return true;
        } catch (IOException e) {
            LOGGER.error("JSON error", e);
        }

        return false;
    }

    public static Map<String, Collection<String>> loadChecksumsFile(File file) {
        ObjectMapper mapper = new KojiObjectMapper();
        TypeReference<Map<String, List<String>>> typeRef = new TypeReference<Map<String, List<String>>>() {

        };

        try {
            Map<String, Collection<String>> obj = mapper.readValue(file, typeRef);
            return obj;
        } catch (IOException e) {
            LOGGER.error("JSON error", e);
        }

        return null;
    }

    public static Map<Integer, KojiBuild> loadBuildsFile(File file) {
        ObjectMapper mapper = new KojiObjectMapper();
        TypeReference<Map<Integer, KojiBuild>> ref = new TypeReference<Map<Integer, KojiBuild>>() {

        };

        try {
            Map<Integer, KojiBuild> obj = mapper.readValue(file, ref);
            return obj;
        } catch (IOException e) {
            LOGGER.error("JSON error", e);
        }

        return null;
    }
}
