package com.jivesoftware.os.miru.service.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.jive.utils.ordered.id.TimestampedOrderIdProvider;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.realtime.MiruRealtimeDelivery;
import com.jivesoftware.os.miru.api.realtime.RealtimeUpdate;
import com.jivesoftware.os.routing.bird.http.client.HttpResponse;
import com.jivesoftware.os.routing.bird.http.client.TailAtScaleStrategy;
import com.jivesoftware.os.routing.bird.http.client.TenantAwareHttpClient;
import com.jivesoftware.os.routing.bird.shared.ClientCall.ClientResponse;
import com.jivesoftware.os.routing.bird.shared.NextClientStrategy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 *
 */
public class RoutingBirdRealtimeDelivery implements MiruRealtimeDelivery {

    private final MiruHost miruHost;
    private final TenantAwareHttpClient<String> deliveryClient;
    private final ExecutorService tasExecutors;
    private final int tasWindowSize;
    private final float tasPercentile;
    private final long tasInitialSLAMillis;
    private final String deliveryEndpoint;
    private final ObjectMapper objectMapper;
    private final MiruStats miruStats;
    private final TimestampedOrderIdProvider orderIdProvider;
    private final long dropRealtimeDeliveryOlderThanNMillis;

    private final Map<MiruTenantId, NextClientStrategy> tenantNextClientStrategy = Maps.newConcurrentMap();

    public RoutingBirdRealtimeDelivery(MiruHost miruHost,
        TenantAwareHttpClient<String> deliveryClient,
        String deliveryEndpoint,
        ObjectMapper objectMapper, MiruStats miruStats,
        TimestampedOrderIdProvider orderIdProvider,
        long dropRealtimeDeliveryOlderThanNMillis,
        ExecutorService tasExecutors,
        int tasWindowSize,
        float tasPercentile,
        long tasInitialSLAMillis) {

        this.miruHost = miruHost;
        this.deliveryClient = deliveryClient;
        this.tasExecutors = tasExecutors;
        this.tasWindowSize = tasWindowSize;
        this.tasPercentile = tasPercentile;
        this.deliveryEndpoint = deliveryEndpoint;
        this.objectMapper = objectMapper;
        this.miruStats = miruStats;
        this.orderIdProvider = orderIdProvider;
        this.dropRealtimeDeliveryOlderThanNMillis = dropRealtimeDeliveryOlderThanNMillis;
        this.tasInitialSLAMillis = tasInitialSLAMillis;
    }

    @Override
    public int deliver(MiruPartitionCoord coord, List<Long> activityTimes) throws Exception {
        List<Long> deliverables = filter(activityTimes);
        long start = System.currentTimeMillis();
        try {
            NextClientStrategy nextClientStrategy = tenantNextClientStrategy.computeIfAbsent(coord.tenantId,
                (key) -> new TailAtScaleStrategy(tasExecutors, tasWindowSize, tasPercentile, tasInitialSLAMillis));

            deliveryClient.call("", nextClientStrategy, "deliverRealtime", httpClient -> {
                String json = null;
                try {
                    json = objectMapper.writeValueAsString(new RealtimeUpdate(miruHost, coord, activityTimes));
                } catch (JsonProcessingException e) {
                    throw new MiruRealtimeDeliveryException("Failed to serialize update", e);
                }
                HttpResponse httpResponse = httpClient.postJson(deliveryEndpoint, json, null);
                if (httpResponse.getStatusCode() < 200 || httpResponse.getStatusCode() >= 300) {
                    throw new MiruRealtimeDeliveryException("Invalid response code: " + httpResponse.getStatusCode());
                }
                return new ClientResponse<Void>(null, true);
            });
            miruStats.egressed("realtime>delivery>success", activityTimes.size(), System.currentTimeMillis() - start);
            return deliverables.size();
        } catch (Exception e) {
            miruStats.egressed("realtime>delivery>failure", activityTimes.size(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    private List<Long> filter(List<Long> activityTimes) {
        if (dropRealtimeDeliveryOlderThanNMillis > 0) {
            long cutoffOrderId = orderIdProvider.getApproximateId(System.currentTimeMillis() - dropRealtimeDeliveryOlderThanNMillis);
            List<Long> deliverables = Lists.newArrayList();
            for (Long activityTime : activityTimes) {
                if (activityTime > cutoffOrderId) {
                    deliverables.add(activityTime);
                }
            }
            return deliverables;
        } else {
            return activityTimes;
        }
    }
}
