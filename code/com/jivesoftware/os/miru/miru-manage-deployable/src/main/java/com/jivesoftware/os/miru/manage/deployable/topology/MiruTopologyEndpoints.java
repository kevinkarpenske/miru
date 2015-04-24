package com.jivesoftware.os.miru.manage.deployable.topology;

import com.jivesoftware.os.jive.utils.jaxrs.util.ResponseHelper;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.topology.MiruHeartbeatRequest;
import com.jivesoftware.os.miru.cluster.MiruRegistryClusterClient;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.nio.charset.StandardCharsets;
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

@Singleton
@Path("/miru/topology")
public class MiruTopologyEndpoints {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final MiruRegistryClusterClient registry;
    private final MiruStats stats;

    public MiruTopologyEndpoints(@Context MiruRegistryClusterClient registry,
        @Context MiruStats stats) {
        this.registry = registry;
        this.stats = stats;
    }

    @GET
    @Path("/routing/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRouting(@PathParam("tenantId") String tenantId) {
        try {
            long start = System.currentTimeMillis();
            Response r = ResponseHelper.INSTANCE.jsonResponse(registry.routingTopology(new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8))));
            stats.ingressed("/routing/" + tenantId, 1, System.currentTimeMillis() - start);
            return r;
        } catch (Exception x) {
            String msg = "Failed to getRouting for " + tenantId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @POST
    @Path("/thumpthump/{host}/{port}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response thumpthump(@PathParam("host") String host,
        @PathParam("port") int port,
        MiruHeartbeatRequest request) {
        try {
            long start = System.currentTimeMillis();
            MiruHost miruHost = new MiruHost(host, port);
            Response r = ResponseHelper.INSTANCE.jsonResponse(registry.thumpthump(miruHost, request));
            stats.ingressed("/thumpthump/" + host + "/" + port, 1, System.currentTimeMillis() - start);
            return r;
        } catch (Exception x) {
            String msg = "Failed to thumpthump for " + host + ":" + port;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @POST
    @Path("/allHosts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllHosts() {
        try {
            long start = System.currentTimeMillis();
            Response r = ResponseHelper.INSTANCE.jsonResponse(registry.allhosts());
            stats.ingressed("/allHosts", 1, System.currentTimeMillis() - start);
            return r;
        } catch (Exception x) {
            String msg = "Failed to getAllHosts";
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @GET
    @Path("/tenantConfig/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTenantConfig(@PathParam("tenantId") String tenantId) {
        try {
            long start = System.currentTimeMillis();
            Response r = ResponseHelper.INSTANCE.jsonResponse(registry.tenantConfig(new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8))));
            stats.ingressed("/tenantConfig/" + tenantId, 1, System.currentTimeMillis() - start);
            return r;
        } catch (Exception x) {
            String msg = "Failed to getTenantConfig for " + tenantId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @POST
    @Path("/elect/{host}/{port}/{tenantId}/{partitionId}/{electionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addToReplicaRegistry(@PathParam("host") String host,
        @PathParam("port") int port,
        @PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        @PathParam("electionId") long electionId) {
        try {
            long start = System.currentTimeMillis();
            MiruTenantId miruTenantId = new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8));
            MiruPartitionId miruPartitionId = MiruPartitionId.of(partitionId);
            MiruHost miruHost = new MiruHost(host, port);
            registry.elect(miruHost, miruTenantId, miruPartitionId, electionId);
            Response r = ResponseHelper.INSTANCE.jsonResponse("");
            stats.ingressed("/elect/" + host + "/" + port + "/" + tenantId + "/" + partitionId + "/" + electionId, 1, System.currentTimeMillis() - start);
            return r;
        } catch (Exception x) {
            String msg = "Failed to addToReplicaRegistry for " + tenantId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @POST
    @Path("/remove/replica/{tenantId}/{partitionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeTenantPartionReplicaSet(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) {
        try {
            long start = System.currentTimeMillis();
            registry.removeReplica(new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8)),
                MiruPartitionId.of(partitionId));
            stats.ingressed("/remove/replica/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return ResponseHelper.INSTANCE.jsonResponse("");
        } catch (Exception x) {
            String msg = "Failed to addToReplicaRegistry for " + tenantId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @POST
    @Path("/partitions/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPartitionsForTenant(@PathParam("tenantId") String tenantId) {
        try {
            long start = System.currentTimeMillis();
            Response r = ResponseHelper.INSTANCE.jsonResponse(registry.partitions(new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8))));
            stats.ingressed("/partitions/" + tenantId, 1, System.currentTimeMillis() - start);
            return r;
        } catch (Exception x) {
            String msg = "Failed to getPartitionsForTenant for " + tenantId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @GET
    @Path("/replicas/{tenantId}/{partitionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReplicaSets(@PathParam("tenantId") String tenantId, @PathParam("partitionId") int partitionId) {
        try {
            long start = System.currentTimeMillis();
            MiruPartitionId miruPartitionId = MiruPartitionId.of(partitionId);
            Response r = ResponseHelper.INSTANCE.jsonResponse(registry.replicas(new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8)),
                miruPartitionId));
            stats.ingressed("/replicas/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return r;
        } catch (Exception x) {
            String msg = "Failed to getReplicaSets for " + tenantId + "/" + partitionId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @POST
    @Path("/remove/{host}/{port}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response remove(@PathParam("host") String host,
        @PathParam("port") int port, MiruHeartbeatRequest request) {
        try {
            long start = System.currentTimeMillis();
            MiruHost miruHost = new MiruHost(host, port);
            registry.remove(miruHost);
            stats.ingressed("/remove/" + host + "/" + port, 1, System.currentTimeMillis() - start);
            return ResponseHelper.INSTANCE.jsonResponse("");
        } catch (Exception x) {
            String msg = "Failed to removeHost for " + host + ":" + port;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @POST
    @Path("/remove/{host}/{port}/{tenantId}/{partitionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeTopology(@PathParam("host") String host,
        @PathParam("port") int port,
        @PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) {
        try {
            long start = System.currentTimeMillis();
            MiruTenantId miruTenantId = new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8));
            MiruPartitionId miruPartitionId = MiruPartitionId.of(partitionId);
            MiruHost miruHost = new MiruHost(host, port);
            registry.remove(miruHost, miruTenantId, miruPartitionId);
            stats.ingressed("/remove/" + host + "/" + port + "/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return ResponseHelper.INSTANCE.jsonResponse("");
        } catch (Exception x) {
            String msg = "Failed to removeTopology for " + host + ":" + port + " " + tenantId + " " + partitionId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @GET
    @Path("/schema/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSchema(@PathParam("tenantId") String tenantId) {
        try {
            long start = System.currentTimeMillis();
            Response r = ResponseHelper.INSTANCE.jsonResponse(registry.getSchema(new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8))));
            stats.ingressed("/get/schema/" + tenantId, 1, System.currentTimeMillis() - start);
            return r;
        } catch (Exception x) {
            String msg = "Failed to getSchema for " + tenantId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }

    @POST
    @Path("/schema/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registerSchema(@PathParam("tenantId") String tenantId, MiruSchema schema) {
        try {
            long start = System.currentTimeMillis();
            registry.registerSchema(new MiruTenantId(tenantId.getBytes(StandardCharsets.UTF_8)), schema);
            stats.ingressed("register/schema/" + tenantId, 1, System.currentTimeMillis() - start);
            return ResponseHelper.INSTANCE.jsonResponse("");
        } catch (Exception x) {
            String msg = "Failed to getSchema for " + tenantId;
            LOG.error(msg, x);
            return ResponseHelper.INSTANCE.errorResponse(msg, x);
        }
    }
}
