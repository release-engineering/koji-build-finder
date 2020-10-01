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
package com.redhat.red.build.finder.it;

import static org.junit.Assert.assertTrue;

import java.util.Collections;

import com.redhat.red.build.finder.BuildConfig;
import com.redhat.red.build.finder.BuildSystem;
import com.redhat.red.build.finder.pnc.client.PncClient;
import com.redhat.red.build.finder.pnc.client.PncClient20;
import com.redhat.red.build.finder.pnc.client.PncClientFactory;

public class PncClient20IT extends AbstractPncClientIT {

    @Override
    PncClient createPncClient(BuildConfig config) {
        config.setBuildSystems(Collections.singletonList(BuildSystem.pnc2));
        PncClient client = PncClientFactory.create(config);
        assertTrue(client instanceof PncClient20);
        return client;
    }
}
