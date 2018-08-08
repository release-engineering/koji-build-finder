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

import java.util.List;

import org.apache.commons.collections4.ListUtils;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.messages.Constants;

public class KojiMultiCallPerformanceIT extends AbstractKojiPerformanceIT {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testMultiCall(final int chunkSize) {
        final Timer timer = REGISTRY.timer(MetricRegistry.name(KojiMultiCallPerformanceIT.class, String.format("multiCall-%03d", chunkSize)));

        for (int i = 0; i < NUM_LOOPS; i++) {
            List<List<Integer>> chunks = ListUtils.partition(getBuilds(), chunkSize);

            final Timer.Context context = timer.time();

            try {
                for (List<Integer> chunk : chunks) {
                    getKojiClientSession().multiCall(Constants.GET_BUILD, (List) chunk, KojiBuildInfo.class, null);
                }

            } finally {
                context.stop();
            }
        }
    }

    @Test
    public void testMultiCall() {
        final int min = 5;
        final int size = BUILD_IDS.size();
        final int doubleSize = size * 2;

        for (int i = min; i <= doubleSize; i *= 2) {
            testMultiCall(Math.min(i, size));
        }
    }
}
