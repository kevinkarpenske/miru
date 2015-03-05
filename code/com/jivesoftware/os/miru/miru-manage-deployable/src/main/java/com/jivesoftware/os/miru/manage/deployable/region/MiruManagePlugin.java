package com.jivesoftware.os.miru.manage.deployable.region;

/**
 *
 */
public class MiruManagePlugin {

    public final String name;
    public final String path;
    public final Class<?> endpointsClass;
    public final MiruRegion<?> region;
    public final MiruRegion<?>[] otherRegions;

    public MiruManagePlugin(String name, String path, Class<?> endpointsClass, MiruRegion<?> region, MiruRegion<?>... otherRegions) {
        this.name = name;
        this.path = path;
        this.endpointsClass = endpointsClass;
        this.region = region;
        this.otherRegions = otherRegions;
    }
}
