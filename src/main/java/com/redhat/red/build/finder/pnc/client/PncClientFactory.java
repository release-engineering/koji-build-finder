package com.redhat.red.build.finder.pnc.client;

import com.redhat.red.build.finder.BuildConfig;
import com.redhat.red.build.finder.BuildSystem;

/**
 * {@link PncClient} factory which creates the correct implementation based on the {@link BuildConfig}.
 *
 * @author Pedro Ruivo
 */
public final class PncClientFactory {

    private PncClientFactory() {
    }

    public static PncClient create(BuildConfig config) {
        if (config.getPncURL() == null) {
            return null;
        }
        //TODO it would be nice to have REST path to query te version used.
        if (config.getBuildSystems().contains(BuildSystem.pnc2)) {
            //PNC 2
            return new PncClient20(config);
        } else if (config.getBuildSystems().contains(BuildSystem.pnc)) {
            //PNC 1
            return new PncClient14(config);
        } else {
            //no PNC callS
            return null;
        }
    }

}
