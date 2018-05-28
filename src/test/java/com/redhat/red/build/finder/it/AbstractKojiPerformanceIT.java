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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.KojiClientException;

public abstract class AbstractKojiPerformanceIT extends AbstractKojiIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKojiPerformanceIT.class);

    protected static final int NUM_LOOPS = 3;

    protected static final List<Integer> BUILD_IDS = Collections.unmodifiableList(Arrays.asList(new Integer[] {34280, 569096, 533769, 583693, 616972, 541198, 518913, 544015, 569088, 583680, 618754, 569091, 470282, 441360, 597023, 569119, 596753, 440606, 441119, 452124, 534802, 541460, 569108, 441115, 576808, 605225, 561705, 557866, 537900, 445986, 537645, 452387, 522797, 558629, 587047, 458038, 442166, 454199, 446773, 441906, 463667, 536639, 569648, 553264, 581168, 586289, 533812, 514618, 618827, 434500, 538699, 506948, 434501, 506946, 618572, 537166, 576591, 587072, 537153, 472397, 586310, 560984, 533592, 569433, 504148, 537180, 503890, 444243, 537437, 434513, 560991, 618579, 495709, 570710, 576360, 585577, 556652, 441955, 610927, 441184, 442222, 557920, 606306, 596580, 547174, 577144, 431990, 431991, 576889, 576633, 537979, 459123, 576636, 507763, 559487, 536191, 568176, 536689, 437628, 442237, 568179, 595058, 585588, 559476, 558964, 536970, 443525, 429445, 448128, 468608, 587648, 556673, 444300, 618626, 569219, 558980, 444042, 533659, 439953, 467868, 536724, 439962, 536727, 608425, 439974, 577449, 607146, 536236, 586414, 448943, 452781, 559294, 586416, 475839, 440764, 537527, 451270, 534728, 609995, 587211, 434626, 569805, 485058, 577229, 441539, 434624, 429258, 582596, 452042, 547269, 461512, 507607, 440790, 553692, 586973, 536542, 546768, 576465, 508125, 576466, 610005, 465882, 562903, 462567, 534767, 576736, 518895, 445932, 536547, 576484, 498676, 440562, 440561, 437745, 518896, 576497, 583153, 484350, 606451, 445693, 465660, 543988, 553463, 457465}));

    private List<Integer> builds;

    @Before
    public void setup() throws IOException, KojiClientException {
        super.setup();

        this.builds = new ArrayList<>(BUILD_IDS);

        Collections.shuffle(this.builds);

        LOGGER.debug("IT parameters: list size: {}, number of loops: {}, max number of connections/multiCall chunk size: {}", this.builds.size(), NUM_LOOPS, MAX_CONNECTIONS);
    }

    public List<Integer> getBuilds() {
        return Collections.unmodifiableList(builds);
    }
}
