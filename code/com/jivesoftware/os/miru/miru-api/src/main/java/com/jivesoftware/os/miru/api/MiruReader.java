package com.jivesoftware.os.miru.api;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.base.MiruTenantId;

/** This interface provides the method to read data from miru. */
public interface MiruReader {

    public static final String QUERY_SERVICE_ENDPOINT_PREFIX = "/miru/reader";

    public static final String WARM_ENDPOINT = "/warm";

    <P, R> R read(MiruTenantId tenantId, Optional<MiruActorId> actorId, P params, String endpoint, Class<R> resultClass, R defaultResult)
            throws MiruQueryServiceException;

    /** Warm up tenant. */
    void warm(MiruTenantId tenantId) throws MiruQueryServiceException;
}