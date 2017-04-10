package com.jivesoftware.os.miru.plugin.index;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruLifecyle;
import com.jivesoftware.os.miru.plugin.backfill.MiruInboxReadTracker;
import com.jivesoftware.os.miru.plugin.backfill.MiruJustInTimeBackfillerizer;
import com.jivesoftware.os.routing.bird.shared.BoundedExecutor;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang.StringUtils;

public class MiruBackfillerizerInitializer {

    public MiruLifecyle<MiruJustInTimeBackfillerizer> initialize(String readStreamIdsPropName,
        MiruHost miruHost,
        MiruInboxReadTracker inboxReadTracker) {
        if (StringUtils.isEmpty(readStreamIdsPropName)) {
            readStreamIdsPropName = null;
        }

        final ExecutorService backfillExecutor =  BoundedExecutor.newBoundedExecutor(10, "backfillerizer");

        final MiruJustInTimeBackfillerizer backfillerizer = new MiruJustInTimeBackfillerizer(inboxReadTracker,
            miruHost, Optional.fromNullable(readStreamIdsPropName), backfillExecutor);

        return new MiruLifecyle<MiruJustInTimeBackfillerizer>() {

            @Override
            public MiruJustInTimeBackfillerizer getService() {
                return backfillerizer;
            }

            @Override
            public void start() throws Exception {
            }

            @Override
            public void stop() throws Exception {
                backfillExecutor.shutdownNow();
            }
        };
    }

}
