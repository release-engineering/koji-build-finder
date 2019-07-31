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

public enum BuildSystem {
    none(0),
    koji(1),
    pnc(2);

    private int value;

    BuildSystem(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static BuildSystem fromInteger(int value) {
        for (BuildSystem buildSystem : values()) {
            if (value == buildSystem.getValue()) {
                return buildSystem;
            }
        }

        throw new IllegalArgumentException("Unknown BuildSystem value: " + value);
    }
}
