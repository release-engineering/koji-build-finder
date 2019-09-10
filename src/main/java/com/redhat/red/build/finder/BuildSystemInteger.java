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

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

public class BuildSystemInteger implements Comparable<Object> {
    private Integer value;

    private BuildSystem buildSystem;

    BuildSystemInteger(Integer value) {
        this.value = value;
        this.buildSystem = BuildSystem.none;
    }

    BuildSystemInteger(int value, BuildSystem buildSystem) {
        this.value = value;
        this.buildSystem = buildSystem;
    }

    public Integer getValue() {
        return value;
    }

    public BuildSystem getBuildSystem() {
        return buildSystem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildSystem, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof BuildSystemInteger)) {
            return false;
        }

        BuildSystemInteger other = (BuildSystemInteger) obj;

        return buildSystem.equals(other.buildSystem) && value.equals(other.value);
    }

    @Override
    public String toString() {
        if (buildSystem == null) {
            return String.valueOf(value);
        }

        return value + ", " + buildSystem;
    }

    @Override
    public int compareTo(Object obj) {
        BuildSystemInteger other = (BuildSystemInteger) obj;

        if (buildSystem.equals(other.buildSystem)) {
            return value.compareTo(other.value);
        }

        return buildSystem.compareTo(other.buildSystem);
    }

    public static class Deserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            String[] t = key.split(", ");
            int value = Integer.parseInt(t[0]);
            BuildSystem buildSystem = BuildSystem.valueOf(t[1]);

            return new BuildSystemInteger(value, buildSystem);
        }
    }
}
