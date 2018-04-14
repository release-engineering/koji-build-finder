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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.redhat.red.build.koji.model.json.util.KojiObjectMapper;

public final class JSONUtils {
    private JSONUtils() {
        throw new AssertionError();
    }

    public static String dumpString(Object obj) throws JsonProcessingException {
        ObjectMapper mapper = new KojiObjectMapper();

        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

    public static void dumpObjectToFile(Object object, File file) throws JsonGenerationException, JsonMappingException, IOException {
        ObjectMapper mapper = new KojiObjectMapper();

        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        FileUtils.forceMkdirParent(file);

        mapper.writerWithDefaultPrettyPrinter().writeValue(file, object);
    }

    public static Map<String, Collection<String>> loadChecksumsFile(File file) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new KojiObjectMapper();

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        TypeReference<Map<String, List<String>>> typeRef = new TypeReference<Map<String, List<String>>>() {

        };

        return mapper.readValue(file, typeRef);
      }

    public static Map<Integer, KojiBuild> loadBuildsFile(File file) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new KojiObjectMapper();

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        TypeReference<Map<Integer, KojiBuild>> ref = new TypeReference<Map<Integer, KojiBuild>>() {

        };

        return mapper.readValue(file, ref);
    }
}
