package com.jivesoftware.os.miru.siphon.deployable;

import com.jivesoftware.os.miru.siphon.deployable.region.MiruSiphonHomeRegion;
import com.jivesoftware.os.miru.siphon.deployable.region.MiruSiphonHeaderRegion;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;

public class MiruSiphonServiceInitializer {

    public MiruSiphonUIService initialize(MiruSoyRenderer renderer) throws Exception {

        return new MiruSiphonUIService(
            renderer,
            new MiruSiphonHeaderRegion("soy.siphon.chrome.headerRegion", renderer),
            new MiruSiphonHomeRegion("soy.siphon.page.home", renderer)
        );
    }

}
