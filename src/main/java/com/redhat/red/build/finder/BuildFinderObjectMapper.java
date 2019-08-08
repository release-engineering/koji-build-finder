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

import com.fasterxml.jackson.databind.SerializationFeature;
import com.redhat.red.build.koji.model.json.util.KojiObjectMapper;

public class BuildFinderObjectMapper extends KojiObjectMapper {
    private static final long serialVersionUID = -1683456434414665536L;

    public BuildFinderObjectMapper() {
        enable(SerializationFeature.INDENT_OUTPUT);
        enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        registerModule(new BuildFinderModule());
    }
}
