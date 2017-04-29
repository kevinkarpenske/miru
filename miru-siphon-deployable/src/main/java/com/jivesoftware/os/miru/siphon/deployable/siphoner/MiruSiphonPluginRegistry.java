package com.jivesoftware.os.miru.siphon.deployable.siphoner;

import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.siphon.api.MiruSiphonPlugin;
import java.util.Map;

/**
 * Created by jonathan.colt on 4/28/17.
 */
public class MiruSiphonPluginRegistry {

    private final Map<String, MiruSiphonPlugin> siphonPlugins = Maps.newConcurrentMap();

    public void add(String siphonPluginName, MiruSiphonPlugin siphonPlugin) {
        synchronized (siphonPlugins) {
            if (siphonPlugins.containsKey(siphonPluginName)) {
                throw new IllegalStateException("plugin name already used. pluginName=" + siphonPluginName);
            }
            siphonPlugins.putIfAbsent(siphonPluginName, siphonPlugin);
        }
    }

    public MiruSiphonPlugin get(String siphonPluginName) {
        return siphonPlugins.get(siphonPluginName);
    }
}
