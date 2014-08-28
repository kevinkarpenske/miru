package com.jivesoftware.os.miru.stream.plugins.count;

import com.jivesoftware.os.miru.query.Miru;
import com.jivesoftware.os.miru.query.MiruProvider;
import com.jivesoftware.os.miru.query.plugin.MiruEndpointInjectable;
import com.jivesoftware.os.miru.query.plugin.MiruPlugin;
import java.util.Collection;
import java.util.Collections;

/**
 *
 */
public class DistinctCountPlugin implements MiruPlugin<DistinctCountEndpoints, DistinctCountInjectable> {

    @Override
    public Class<DistinctCountEndpoints> getEndpointsClass() {
        return DistinctCountEndpoints.class;
    }

    @Override
    public Collection<MiruEndpointInjectable<DistinctCountInjectable>> getInjectables(MiruProvider<? extends Miru> miruProvider) {
        NumberOfDistincts numberOfDistincts = new NumberOfDistincts();
        return Collections.singletonList(new MiruEndpointInjectable<>(
                DistinctCountInjectable.class,
                new DistinctCountInjectable(miruProvider, numberOfDistincts)
        ));
    }
}
