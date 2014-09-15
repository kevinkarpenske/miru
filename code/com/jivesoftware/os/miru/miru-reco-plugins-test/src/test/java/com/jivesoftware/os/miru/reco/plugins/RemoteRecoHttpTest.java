package com.jivesoftware.os.miru.reco.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.jivesoftware.os.jive.utils.http.client.HttpClientConfiguration;
import com.jivesoftware.os.jive.utils.http.client.HttpClientFactory;
import com.jivesoftware.os.jive.utils.http.client.HttpClientFactoryProvider;
import com.jivesoftware.os.jive.utils.http.client.rest.RequestHelper;
import com.jivesoftware.os.jive.utils.id.Id;
import com.jivesoftware.os.jive.utils.ordered.id.JiveEpochTimestampProvider;
import com.jivesoftware.os.jive.utils.ordered.id.SnowflakeIdPacker;
import com.jivesoftware.os.miru.api.MiruActorId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.query.filter.MiruAuthzExpression;
import com.jivesoftware.os.miru.api.query.filter.MiruFieldFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilterOperation;
import com.jivesoftware.os.miru.query.solution.MiruRequest;
import com.jivesoftware.os.miru.query.solution.MiruResponse;
import com.jivesoftware.os.miru.query.solution.MiruTimeRange;
import com.jivesoftware.os.miru.reco.plugins.reco.RecoAnswer;
import com.jivesoftware.os.miru.reco.plugins.reco.RecoConstants;
import com.jivesoftware.os.miru.reco.plugins.reco.RecoQuery;
import com.jivesoftware.os.miru.reco.plugins.trending.TrendingAnswer;
import com.jivesoftware.os.miru.reco.plugins.trending.TrendingConstants;
import com.jivesoftware.os.miru.reco.plugins.trending.TrendingQuery;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.Charsets;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

/**
 *
 */
public class RemoteRecoHttpTest {

    private static final String REMOTE_HOST = ""; //soa-prime-data6.phx1.jivehosted.com";
    private static final int REMOTE_PORT = -1; //10004;

    @Test (enabled = false, description = "Needs REMOTE constants")
    public void testSystemTrending() throws Exception {
        String tenant = "999";
        MiruTenantId tenantId = new MiruTenantId(tenant.getBytes(Charsets.UTF_8));

        HttpClientFactory httpClientFactory = new HttpClientFactoryProvider()
            .createHttpClientFactory(Collections.<HttpClientConfiguration>emptyList());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new GuavaModule());
        RequestHelper requestHelper = new RequestHelper(httpClientFactory.createClient(REMOTE_HOST, REMOTE_PORT), objectMapper);

        MiruFilter constraintsFilter = new MiruFilter(MiruFilterOperation.and,
            Optional.of(Arrays.asList(
                    new MiruFieldFilter("objectType", Lists.transform(
                            Arrays.asList(102, 1, 18, 38, 801, 1464927464, -960826044),
                            Functions.toStringFunction())),
                    new MiruFieldFilter("activityType", Lists.transform(Arrays.asList(
                                0, //viewed
                                11, //liked
                                1, //created
                                65 //outcome_set
                            ), Functions.toStringFunction()))
                )),
            Optional.<List<MiruFilter>>absent());

        SnowflakeIdPacker snowflakeIdPacker = new SnowflakeIdPacker();
        long jiveCurrentTime = new JiveEpochTimestampProvider().getTimestamp();
        long packCurrentTime = snowflakeIdPacker.pack(jiveCurrentTime, 0, 0);
        long packThreeDays = snowflakeIdPacker.pack(TimeUnit.DAYS.toMillis(3), 0, 0);
        MiruRequest<TrendingQuery> query = new MiruRequest<>(tenantId, new MiruActorId(new Id(3765)),
            MiruAuthzExpression.NOT_PROVIDED,
            new TrendingQuery(
                new MiruTimeRange(packCurrentTime - packThreeDays, packCurrentTime),
                32,
                constraintsFilter,
                "parent",
                100), true);

        int numQueries = 1;
        for (int i = 0; i < numQueries; i++) {
            MiruResponse<TrendingAnswer> response = requestHelper.executeRequest(query,
                TrendingConstants.TRENDING_PREFIX + TrendingConstants.CUSTOM_QUERY_ENDPOINT,
                MiruResponse.class, new Class[]{ TrendingAnswer.class }, null);
            System.out.println(response);
            assertNotNull(response);
        }
    }

    @Test (enabled = false, description = "Needs REMOTE constants")
    public void testSystemRecommended() throws Exception {

        String tenant = "999";
        MiruTenantId tenantId = new MiruTenantId(tenant.getBytes(Charsets.UTF_8));

        HttpClientFactory httpClientFactory = new HttpClientFactoryProvider()
            .createHttpClientFactory(Collections.<HttpClientConfiguration>emptyList());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new GuavaModule());
        RequestHelper requestHelper = new RequestHelper(httpClientFactory.createClient(REMOTE_HOST, REMOTE_PORT), objectMapper);

        MiruFilter constraintsFilter = new MiruFilter(MiruFilterOperation.and,
            Optional.of(Arrays.asList(
                    new MiruFieldFilter("user", Arrays.asList(String.valueOf(3765))),
                    new MiruFieldFilter("objectType", Lists.transform(
                            Arrays.asList(102, 1, 18, 38, 801, 1464927464, -960826044),
                            Functions.toStringFunction())),
                    new MiruFieldFilter("activityType", Lists.transform(Arrays.asList(
                                0, //viewed
                                11, //liked
                                1, //created
                                65 //outcome_set
                            ), Functions.toStringFunction()))
                )),
            Optional.<List<MiruFilter>>absent());

        MiruRequest<RecoQuery> request = new MiruRequest<>(tenantId,
            new MiruActorId(new Id(3765)),
            MiruAuthzExpression.NOT_PROVIDED,
            new RecoQuery(constraintsFilter,
                "parent", "parent", "parent",
                "user", "user", "user",
                "parent", "parent",
                100), true);

        MiruResponse<RecoAnswer> recoAnswer = requestHelper.executeRequest(request,
            RecoConstants.RECO_PREFIX + RecoConstants.CUSTOM_QUERY_ENDPOINT,
            MiruResponse.class, new Class[]{ RecoAnswer.class }, null);
        System.out.println(recoAnswer);
        assertNotNull(recoAnswer);
    }

}
