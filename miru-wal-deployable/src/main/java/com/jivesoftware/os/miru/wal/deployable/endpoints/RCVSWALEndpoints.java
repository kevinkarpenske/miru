package com.jivesoftware.os.miru.wal.deployable.endpoints;

import com.google.common.base.Charsets;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.wal.AmzaSipCursor;
import com.jivesoftware.os.miru.api.wal.MiruActivityWALStatus;
import com.jivesoftware.os.miru.api.wal.MiruVersionedActivityLookupEntry;
import com.jivesoftware.os.miru.api.wal.MiruWALClient.OldestReadResult;
import com.jivesoftware.os.miru.api.wal.MiruWALClient.RoutingGroupType;
import com.jivesoftware.os.miru.api.wal.MiruWALClient.StreamBatch;
import com.jivesoftware.os.miru.api.wal.MiruWALClient.WriterCursor;
import com.jivesoftware.os.miru.api.wal.MiruWALEntry;
import com.jivesoftware.os.miru.api.wal.RCVSCursor;
import com.jivesoftware.os.miru.api.wal.RCVSSipCursor;
import com.jivesoftware.os.miru.api.wal.SipAndLastSeen;
import com.jivesoftware.os.miru.wal.MiruWALNotInitializedException;
import com.jivesoftware.os.miru.wal.MiruWALWrongRouteException;
import com.jivesoftware.os.miru.wal.RCVSWALDirector;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.routing.bird.shared.HostPort;
import com.jivesoftware.os.routing.bird.shared.ResponseHelper;
import java.util.List;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * @author jonathan.colt
 */
@Singleton
@Path("/miru/wal/rcvs")
public class RCVSWALEndpoints {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    private final RCVSWALDirector walDirector;
    private final MiruStats stats;
    private final ResponseHelper responseHelper = ResponseHelper.INSTANCE;

    public RCVSWALEndpoints(@Context RCVSWALDirector walDirector, @Context MiruStats stats) {
        this.walDirector = walDirector;
        this.stats = stats;
    }

    @GET
    @Path("/routing/tenantPartition/{type}/{tenantId}/{partitionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTenantPartitionRoutingGroup(@PathParam("type") RoutingGroupType routingGroupType,
        @PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) {
        try {
            long start = System.currentTimeMillis();
            HostPort[] routingGroup = walDirector.getTenantPartitionRoutingGroup(routingGroupType, new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId), true);
            stats.ingressed("/routing/tenantPartition/" + routingGroupType.name() + "/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(routingGroup);
        } catch (Exception x) {
            log.error("Failed calling getTenantPartitionRoutingGroup({},{},{})", new Object[] { routingGroupType, tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/routing/lazyTenantPartition/{type}/{tenantId}/{partitionId}/{createIfAbsent}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTenantPartitionRoutingGroup(@PathParam("type") RoutingGroupType routingGroupType,
        @PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        @PathParam("createIfAbsent") boolean createIfAbsent) {
        try {
            long start = System.currentTimeMillis();
            HostPort[] routingGroup = walDirector.getTenantPartitionRoutingGroup(routingGroupType, new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId), createIfAbsent);
            stats.ingressed("/routing/lazyTenantPartition/" + routingGroupType.name() + "/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(routingGroup);
        } catch (Exception x) {
            log.error("Failed calling getLazyTenantPartitionRoutingGroup({},{},{},{})",
                new Object[] { routingGroupType, tenantId, partitionId, createIfAbsent }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/routing/tenantStream/{type}/{tenantId}/{streamId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTenantStreamRoutingGroup(@PathParam("type") RoutingGroupType routingGroupType,
        @PathParam("tenantId") String tenantId,
        @PathParam("streamId") String streamId) {
        try {
            long start = System.currentTimeMillis();
            HostPort[] routingGroup = walDirector.getTenantStreamRoutingGroup(routingGroupType, new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                new MiruStreamId(streamId.getBytes(Charsets.UTF_8)), true);
            stats.ingressed("/routing/tenantStream/" + routingGroupType.name() + "/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(routingGroup);
        } catch (Exception x) {
            log.error("Failed calling getTenantStreamRoutingGroup({},{},{})", new Object[] { routingGroupType, tenantId, streamId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/routing/lazyTenantStream/{type}/{tenantId}/{streamId}/{createIfAbsent}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLazyTenantStreamRoutingGroup(@PathParam("type") RoutingGroupType routingGroupType,
        @PathParam("tenantId") String tenantId,
        @PathParam("streamId") String streamId,
        @PathParam("createIfAbsent") boolean createIfAbsent) {
        try {
            long start = System.currentTimeMillis();
            HostPort[] routingGroup = walDirector.getTenantStreamRoutingGroup(routingGroupType, new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                new MiruStreamId(streamId.getBytes(Charsets.UTF_8)), createIfAbsent);
            stats.ingressed("/routing/lazyTenantStream/" + routingGroupType.name() + "/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(routingGroup);
        } catch (Exception x) {
            log.error("Failed calling getLazyTenantStreamRoutingGroup({},{},{},{})",
                new Object[] { routingGroupType, tenantId, streamId, createIfAbsent }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/repairRanges/{fast}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response repairRanges(@PathParam("fast") boolean fast) throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.repairRanges(fast);
            stats.ingressed("/repairRanges", 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (Exception x) {
            log.error("Failed calling repairRanges()", x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/tenants/all")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllTenantIds() throws Exception {
        try {
            long start = System.currentTimeMillis();
            List<MiruTenantId> allTenantIds = walDirector.getAllTenantIds();
            stats.ingressed("/tenants/all", 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(allTenantIds);
        } catch (Exception x) {
            log.error("Failed calling getAllTenantIds()", x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/write/activities/{tenantId}/{partitionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response writeActivity(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        List<MiruPartitionedActivity> partitionedActivities) throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.writeActivity(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), MiruPartitionId.of(partitionId), partitionedActivities);
            stats.ingressed("/write/activities/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling writeActivity({},count:{})",
                new Object[] { tenantId, partitionedActivities != null ? partitionedActivities.size() : null }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling writeActivity({},count:{})",
                new Object[] { tenantId, partitionedActivities != null ? partitionedActivities.size() : null }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling writeActivity({},count:{})",
                new Object[] { tenantId, partitionedActivities != null ? partitionedActivities.size() : null }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/write/reads/{tenantId}/{streamId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response writeReadTracking(@PathParam("tenantId") String tenantId,
        @PathParam("streamId") String streamId,
        List<MiruPartitionedActivity> partitionedActivities) throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.writeReadTracking(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), new MiruStreamId(streamId.getBytes(Charsets.UTF_8)),
                partitionedActivities);
            stats.ingressed("/write/reads/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling writeReadTracking({},count:{})",
                new Object[] { tenantId, partitionedActivities != null ? partitionedActivities.size() : null }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling writeReadTracking({},count:{})",
                new Object[] { tenantId, partitionedActivities != null ? partitionedActivities.size() : null }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling writeReadTracking({},{},count:{})",
                new Object[] { tenantId, streamId, partitionedActivities != null ? partitionedActivities.size() : null }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/largestPartitionId/{tenantId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLargestPartitionIdAcrossAllWriters(@PathParam("tenantId") String tenantId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            MiruPartitionId partitionId = walDirector.getLargestPartitionId(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)));
            stats.ingressed("/largestPartitionId/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(partitionId);
        } catch (Exception x) {
            log.error("Failed calling getLargestPartitionId({})", new Object[] { tenantId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/cursor/writer/{tenantId}/{partitionId}/{writerId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCursorForWriterId(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        @PathParam("writerId") int writerId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            WriterCursor cursor = walDirector.getCursorForWriterId(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId), writerId);
            stats.ingressed("/cursor/writer/" + tenantId + "/" + writerId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(cursor);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling getCursorForWriterId({},{},{})",
                new Object[] { tenantId, partitionId, writerId }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling getCursorForWriterId({},{},{})",
                new Object[] { tenantId, partitionId, writerId }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling getCursorForWriterId({},{},{})", new Object[] { tenantId, partitionId, writerId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/activity/wal/status/{tenantId}/{partitionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getActivityWALStatus(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            MiruActivityWALStatus partitionStatus = walDirector.getActivityWALStatusForTenant(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId));
            stats.ingressed("/activity/wal/status/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(partitionStatus);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling getActivityWALStatus({},{})",
                new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling getActivityWALStatus({},{})",
                new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling getActivityWALStatus({},{})", new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/oldest/activity/{tenantId}/{partitionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response oldestActivityClockTimestamp(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            long timestamp = walDirector.oldestActivityClockTimestamp(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), MiruPartitionId.of(partitionId));
            stats.ingressed("/oldest/activity/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(timestamp);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling oldestActivityClockTimestamp({},{})",
                new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling oldestActivityClockTimestamp({},{})",
                new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling oldestActivityClockTimestamp({},{})", new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/versioned/entries/{tenantId}/{partitionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVersionedEntries(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        Long[] timestamps) throws Exception {
        try {
            long start = System.currentTimeMillis();
            List<MiruVersionedActivityLookupEntry> versionedEntries = walDirector.getVersionedEntries(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId),
                timestamps);
            stats.ingressed("/versioned/entries/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(versionedEntries);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling getVersionedEntries({},{},count:{})",
                new Object[] { tenantId, partitionId, timestamps != null ? timestamps.length : null }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling getVersionedEntries({},{},count:{})",
                new Object[] { tenantId, partitionId, timestamps != null ? timestamps.length : null }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling getVersionedEntries({},{},count:{})",
                new Object[] { tenantId, partitionId, timestamps != null ? timestamps.length : null }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/sip/activity/{tenantId}/{partitionId}/{batchSize}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sipActivity(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        @PathParam("batchSize") int batchSize,
        SipAndLastSeen<RCVSSipCursor> sipAndLastSeen)
        throws Exception {
        try {
            long start = System.currentTimeMillis();
            StreamBatch<MiruWALEntry, RCVSSipCursor> sipActivity = walDirector.sipActivity(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId), sipAndLastSeen.sipCursor, sipAndLastSeen.lastSeen, batchSize);
            stats.ingressed("/sip/activity/" + batchSize, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(sipActivity);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling sipActivity({},{},{},{})",
                new Object[] { tenantId, partitionId, batchSize, sipAndLastSeen }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling sipActivity({},{},{})",
                new Object[] { tenantId, partitionId, batchSize, sipAndLastSeen }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling sipActivity({},{},{},{})", new Object[] { tenantId, partitionId, batchSize, sipAndLastSeen }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/activityCount/{tenantId}/{partitionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getActivityCount(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId)
        throws Exception {
        try {
            long start = System.currentTimeMillis();
            long count = walDirector.getActivityCount(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), MiruPartitionId.of(partitionId));
            stats.ingressed("/activityCount/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(count);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling getActivityCount({},{})",
                new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling getActivityCount({},{})",
                new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling getActivityCount({},{})", new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/activity/{tenantId}/{partitionId}/{batchSize}/{stopAtTimestamp}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getActivity(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        @PathParam("batchSize") int batchSize,
        @PathParam("stopAtTimestamp") long stopAtTimestamp,
        RCVSCursor cursor)
        throws Exception {
        try {
            long start = System.currentTimeMillis();
            StreamBatch<MiruWALEntry, RCVSCursor> activity = walDirector.getActivity(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId), cursor, batchSize, stopAtTimestamp, null);
            stats.ingressed("/activity/" + tenantId + "/" + partitionId + "/" + batchSize, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(activity);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling getActivity({},{},{},{},{})",
                new Object[] { tenantId, partitionId, batchSize, stopAtTimestamp, cursor }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling getActivity({},{},{},{},{})",
                new Object[] { tenantId, partitionId, batchSize, stopAtTimestamp, cursor }, x);
            return responseHelper.errorResponse(Response.Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling getActivity({},{},{},{},{})", new Object[] { tenantId, partitionId, batchSize, stopAtTimestamp, cursor }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/oldestReadEventId/{tenantId}/{streamId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response oldestReadEventId(@PathParam("tenantId") String tenantId,
        @PathParam("streamId") String streamId,
        RCVSSipCursor cursor) throws Exception {
        try {
            long start = System.currentTimeMillis();
            OldestReadResult<RCVSSipCursor> result = walDirector.oldestReadEventId(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                new MiruStreamId(streamId.getBytes(Charsets.UTF_8)), cursor);
            stats.ingressed("/oldestReadEventId/" + tenantId + "/" + streamId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(result);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling oldestReadEventId({},{},{})",
                new Object[] { tenantId, streamId, cursor }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling oldestReadEventId({},{},{})",
                new Object[] { tenantId, streamId, cursor }, x);
            return responseHelper.errorResponse(Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling oldestReadEventId({},{},{})", new Object[] { tenantId, streamId, cursor }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/scanRead/{tenantId}/{streamId}/{oldestEventId}/{batchSize}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response scanRead(@PathParam("tenantId") String tenantId,
        @PathParam("streamId") String streamId,
        @PathParam("oldestEventId") long oldestEventId,
        @PathParam("batchSize") int batchSize) throws Exception {
        try {
            long start = System.currentTimeMillis();
            StreamBatch<MiruWALEntry, Long> read = walDirector.scanRead(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                new MiruStreamId(streamId.getBytes(Charsets.UTF_8)), oldestEventId, batchSize);
            stats.ingressed("/scanRead/" + tenantId + "/" + streamId + "/" + batchSize, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(read);
        } catch (MiruWALNotInitializedException x) {
            log.error("WAL not initialized calling scanRead({},{},{},{})",
                new Object[] { tenantId, streamId, oldestEventId, batchSize }, x);
            return responseHelper.errorResponse(Response.Status.SERVICE_UNAVAILABLE, "WAL not initialized", x);
        } catch (MiruWALWrongRouteException x) {
            log.error("Wrong route calling scanRead({},{},{},{})",
                new Object[] { tenantId, streamId, oldestEventId, batchSize }, x);
            return responseHelper.errorResponse(Status.CONFLICT, "Wrong route", x);
        } catch (Exception x) {
            log.error("Failed calling scanRead({},{},{},{})", new Object[] { tenantId, streamId, oldestEventId, batchSize }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

}
