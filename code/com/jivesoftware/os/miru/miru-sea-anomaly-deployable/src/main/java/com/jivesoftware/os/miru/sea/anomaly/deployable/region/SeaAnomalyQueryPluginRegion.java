package com.jivesoftware.os.miru.sea.anomaly.deployable.region;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.jive.utils.http.client.rest.RequestHelper;
import com.jivesoftware.os.jive.utils.ordered.id.JiveEpochTimestampProvider;
import com.jivesoftware.os.jive.utils.ordered.id.SnowflakeIdPacker;
import com.jivesoftware.os.miru.api.MiruActorId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.field.MiruFieldType;
import com.jivesoftware.os.miru.api.query.filter.MiruAuthzExpression;
import com.jivesoftware.os.miru.api.query.filter.MiruFieldFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.api.query.filter.MiruFilterOperation;
import com.jivesoftware.os.miru.plugin.solution.MiruRequest;
import com.jivesoftware.os.miru.plugin.solution.MiruResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLogLevel;
import com.jivesoftware.os.miru.plugin.solution.MiruTimeRange;
import com.jivesoftware.os.miru.sea.anomaly.deployable.MiruSoyRenderer;
import com.jivesoftware.os.miru.sea.anomaly.deployable.SeaAnomalySchemaConstants;
import com.jivesoftware.os.miru.sea.anomaly.plugins.SeaAnomalyAnswer;
import com.jivesoftware.os.miru.sea.anomaly.plugins.SeaAnomalyConstants;
import com.jivesoftware.os.miru.sea.anomaly.plugins.SeaAnomalyQuery;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 *
 */
// soy.sea.anomaly.page.seaAnomalyQueryPluginRegion
public class SeaAnomalyQueryPluginRegion implements PageRegion<Optional<SeaAnomalyQueryPluginRegion.SeaAnomalyPluginRegionInput>> {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    private final String template;
    private final MiruSoyRenderer renderer;
    private final RequestHelper[] miruReaders;

    public SeaAnomalyQueryPluginRegion(String template,
        MiruSoyRenderer renderer,
        RequestHelper[] miruReaders) {

        this.template = template;
        this.renderer = renderer;
        this.miruReaders = miruReaders;
    }

    public static class SeaAnomalyPluginRegionInput {

        final String cluster;
        final String host;
        final String service;
        final String instance;
        final String version;

        final int fromAgo;
        final int toAgo;
        final String fromTimeUnit;
        final String toTimeUnit;
        final String tenant;

        final String samplers;
        final String metrics;
        final String tags;
        final String type;

        final int buckets;

        final String expansionField;
        final String expansionValue;

        public SeaAnomalyPluginRegionInput(String cluster,
            String host,
            String service,
            String instance,
            String version,
            int fromAgo,
            int toAgo,
            String fromTimeUnit,
            String toTimeUnit,
            String tenant,
            String samplers,
            String metrics,
            String tags,
            String type,
            int buckets,
            String expansionField,
            String expansionValue) {

            this.cluster = cluster;
            this.host = host;
            this.service = service;
            this.instance = instance;
            this.version = version;
            this.fromAgo = fromAgo;
            this.toAgo = toAgo;
            this.fromTimeUnit = fromTimeUnit;
            this.toTimeUnit = toTimeUnit;
            this.tenant = tenant;
            this.samplers = samplers;
            this.metrics = metrics;
            this.tags = tags;
            this.type = type;
            this.buckets = buckets;
            this.expansionField = expansionField;
            this.expansionValue = expansionValue;
        }

    }

    @Override
    public String render(Optional<SeaAnomalyPluginRegionInput> optionalInput) {
        Map<String, Object> data = Maps.newHashMap();
        try {
            if (optionalInput.isPresent()) {
                SeaAnomalyPluginRegionInput input = optionalInput.get();
                int fromAgo = input.fromAgo > input.toAgo ? input.fromAgo : input.toAgo;
                int toAgo = input.fromAgo > input.toAgo ? input.toAgo : input.fromAgo;

                data.put("cluster", input.cluster);
                data.put("host", input.host);
                data.put("service", input.service);
                data.put("instance", input.instance);
                data.put("version", input.version);
                data.put("fromTimeUnit", input.fromTimeUnit);
                data.put("toTimeUnit", input.toTimeUnit);

                data.put("tenant", input.tenant);
                data.put("samplers", input.samplers);
                data.put("metrics", input.metrics);
                data.put("tags", input.tags);
                data.put("type", input.type);

                data.put("fromAgo", String.valueOf(fromAgo));
                data.put("toAgo", String.valueOf(toAgo));
                data.put("buckets", String.valueOf(input.buckets));

                data.put("expansionField", input.expansionField);
                data.put("expansionValue", input.expansionValue);

                SnowflakeIdPacker snowflakeIdPacker = new SnowflakeIdPacker();
                long jiveCurrentTime = new JiveEpochTimestampProvider().getTimestamp();
                final long packCurrentTime = snowflakeIdPacker.pack(jiveCurrentTime, 0, 0);
                final long fromTime = packCurrentTime - snowflakeIdPacker.pack(TimeUnit.valueOf(input.fromTimeUnit).toMillis(fromAgo), 0, 0);
                final long toTime = packCurrentTime - snowflakeIdPacker.pack(TimeUnit.valueOf(input.toTimeUnit).toMillis(toAgo), 0, 0);

                MiruTenantId tenantId = SeaAnomalySchemaConstants.TENANT_ID;
                MiruResponse<SeaAnomalyAnswer> response = null;
                for (RequestHelper requestHelper : miruReaders) {
                    try {
                        List<MiruFieldFilter> fieldFilters = Lists.newArrayList();
                        List<MiruFieldFilter> notFieldFilters = Lists.newArrayList();
                        addFieldFilter(fieldFilters, notFieldFilters, "cluster", input.cluster);
                        addFieldFilter(fieldFilters, notFieldFilters, "host", input.host);
                        addFieldFilter(fieldFilters, notFieldFilters, "service", input.service);
                        addFieldFilter(fieldFilters, notFieldFilters, "instance", input.instance);
                        addFieldFilter(fieldFilters, notFieldFilters, "version", input.version);
                        addFieldFilter(fieldFilters, notFieldFilters, "tenant", input.tenant);
                        addFieldFilter(fieldFilters, notFieldFilters, "sampler", input.samplers);
                        addFieldFilter(fieldFilters, notFieldFilters, "metric", input.metrics);
                        addFieldFilter(fieldFilters, notFieldFilters, "tags", input.tags);
                        addFieldFilter(fieldFilters, notFieldFilters, "type", input.type);

                        List<MiruFilter> notFilters = null;
                        if (!notFieldFilters.isEmpty()) {
                            notFilters = Arrays.asList(
                                new MiruFilter(MiruFilterOperation.pButNotQ,
                                    true,
                                    notFieldFilters,
                                    null));
                        }

                        ImmutableMap<String, MiruFilter> seaAnomalyFilters = ImmutableMap.of(
                            "metric",
                            new MiruFilter(MiruFilterOperation.and,
                                false,
                                fieldFilters,
                                notFilters));

                        @SuppressWarnings("unchecked")
                        MiruResponse<SeaAnomalyAnswer> miruResponse = requestHelper.executeRequest(
                            new MiruRequest<>(tenantId,
                                MiruActorId.NOT_PROVIDED,
                                MiruAuthzExpression.NOT_PROVIDED,
                                new SeaAnomalyQuery(
                                    new MiruTimeRange(fromTime, toTime),
                                    input.buckets,
                                    "bits",
                                    MiruFilter.NO_FILTER,
                                    seaAnomalyFilters,
                                    input.expansionField,
                                    Arrays.asList(input.expansionValue.split("\\s*,\\s*"))),
                                MiruSolutionLogLevel.INFO),
                            SeaAnomalyConstants.SEA_ANOMALY_PREFIX + SeaAnomalyConstants.CUSTOM_QUERY_ENDPOINT,
                            MiruResponse.class,
                            new Class[]{SeaAnomalyAnswer.class},
                            null);
                        response = miruResponse;
                        if (response != null && response.answer != null) {
                            break;
                        } else {
                            log.warn("Empty seaAnomaly response from {}, trying another", requestHelper);
                        }
                    } catch (Exception e) {
                        log.warn("Failed seaAnomaly request to {}, trying another", new Object[]{requestHelper}, e);
                    }
                }

                if (response != null && response.answer != null) {
                    Map<String, SeaAnomalyAnswer.Waveform> waveforms = response.answer.waveforms;
                    if (waveforms == null) {
                        waveforms = Collections.emptyMap();
                    }
                    data.put("elapse", String.valueOf(response.totalElapsed));

                    Map<String, long[]> rawWaveforms = new HashMap<>();
                    for (Entry<String, SeaAnomalyAnswer.Waveform> e : waveforms.entrySet()) {
                        rawWaveforms.put(e.getKey(), e.getValue().waveform);
                    }

                    List<String> labels = new ArrayList<>();
                    List<Map<String, Object>> datasets = new ArrayList<>();

                    ArrayList<Map<String, Object>> results = new ArrayList<>();
                    int id = 0;
                    for (Entry<String, long[]> t : rawWaveforms.entrySet()) {
                        if (labels.isEmpty()) {
                            for (int i = 0; i < t.getValue().length; i++) {
                                labels.add("\"" + String.valueOf(i) + "\"");
                            }
                        }
                        Color c = new Color(Color.HSBtoRGB((float) id / (float) (rawWaveforms.size()), 1f, 1f));
                        Map<String, Object> w = waveform(t.getKey(), c, 0.2f, t.getValue());
                        results.add(ImmutableMap.of(
                            "id", String.valueOf(id),
                            "rank", "nil",
                            "field", input.expansionField,
                            "value", t.getKey(),
                            "wave", (Object) ImmutableMap.of("labels", labels, "datasets", Arrays.asList(w))));

                        datasets.add(w);
                        id++;
                    }

                    data.put("waves", ImmutableMap.of("labels", labels, "datasets", datasets));
                    data.put("results", results);

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);

                    data.put("summary", Joiner.on("\n").join(response.log) + "\n\n" + mapper.writeValueAsString(response.solutions));
                }
            }
        } catch (Exception e) {
            log.error("Unable to retrieve data", e);
        }

        return renderer.render(template, data);
    }

    public Map<String, Object> waveform(String label, Color color, float alpha, long[] values) {
        Map<String, Object> waveform = new HashMap<>();
        waveform.put("label", "\"" + label + "\"");
        waveform.put("fillColor", "\"rgba(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + String.valueOf(alpha) + ")\"");
        waveform.put("strokeColor", "\"rgba(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ",1)\"");
        waveform.put("pointColor", "\"rgba(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ",1)\"");
        waveform.put("pointStrokeColor", "\"rgba(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ",1)\"");
        waveform.put("pointHighlightFill", "\"rgba(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ",1)\"");
        waveform.put("pointHighlightStroke", "\"rgba(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ",1)\"");
        List<Integer> ints = new ArrayList<>();
        for (long v : values) {
            ints.add((int) v);
        }
        waveform.put("data", ints);
        return waveform;
    }

    private void addFieldFilter(List<MiruFieldFilter> fieldFilters, List<MiruFieldFilter> notFilters, String fieldName, String values) {
        if (values != null) {
            values = values.trim();
            String[] valueArray = values.split("\\s*,\\s*");
            List<String> terms = Lists.newArrayList();
            List<String> notTerms = Lists.newArrayList();
            for (String value : valueArray) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.startsWith("!")) {
                        if (trimmed.length() > 1) {
                            notTerms.add(trimmed.substring(1));
                        }
                    } else {
                        terms.add(trimmed);
                    }
                }
            }
            if (!terms.isEmpty()) {
                fieldFilters.add(new MiruFieldFilter(MiruFieldType.primary, fieldName, terms));
            }
            if (!notTerms.isEmpty()) {
                notFilters.add(new MiruFieldFilter(MiruFieldType.primary, fieldName, notTerms));
            }
        }
    }

    @Override
    public String getTitle() {
        return "Find Anomaly";
    }
}
