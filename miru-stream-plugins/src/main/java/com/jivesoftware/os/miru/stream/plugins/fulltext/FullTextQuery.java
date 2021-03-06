package com.jivesoftware.os.miru.stream.plugins.fulltext;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.plugin.solution.MiruTimeRange;
import java.io.Serializable;
import java.util.Arrays;

/**
 *
 */
public class FullTextQuery implements Serializable {

    public enum Strategy implements Serializable {
        TIME, TF_IDF;
    }

    public final MiruTimeRange timeRange;
    public final String defaultField;
    public final String locale;
    public final boolean useStopWords;
    public final String query;
    public final int maxWildcardExpansion;
    public final MiruFilter constraintsFilter;
    public final Strategy strategy;
    public final int desiredNumberOfResults;
    public final String[] gatherTermsForFields;

    public FullTextQuery(
        @JsonProperty("timeRange") MiruTimeRange timeRange,
        @JsonProperty("defaultField") String defaultField,
        @JsonProperty("locale") String locale,
        @JsonProperty("useStopWords") boolean useStopWords,
        @JsonProperty("query") String query,
        @JsonProperty("maxDeterminizedStates") int maxWildcardExpansion,
        @JsonProperty("constraintsFilter") MiruFilter constraintsFilter,
        @JsonProperty("strategy") Strategy strategy,
        @JsonProperty("desiredNumberOfResults") int desiredNumberOfResults,
        @JsonProperty("gatherTermsForFields") String[] gatherTermsForFields) {
        this.timeRange = Preconditions.checkNotNull(timeRange);
        this.defaultField = Preconditions.checkNotNull(defaultField);
        this.locale = locale;
        this.useStopWords = useStopWords;
        this.query = Preconditions.checkNotNull(query);
        this.maxWildcardExpansion = maxWildcardExpansion;
        this.constraintsFilter = Preconditions.checkNotNull(constraintsFilter);
        this.strategy = Preconditions.checkNotNull(strategy);
        Preconditions.checkArgument(desiredNumberOfResults > 0, "Number of results must be at least 1");
        this.desiredNumberOfResults = desiredNumberOfResults;
        this.gatherTermsForFields = gatherTermsForFields;
    }

    @Override
    public String toString() {
        return "FullTextQuery{" +
            "timeRange=" + timeRange +
            ", defaultField='" + defaultField + '\'' +
            ", locale='" + locale + '\'' +
            ", useStopWords=" + useStopWords +
            ", query='" + query + '\'' +
            ", maxWildcardExpansion=" + maxWildcardExpansion +
            ", constraintsFilter=" + constraintsFilter +
            ", strategy=" + strategy +
            ", desiredNumberOfResults=" + desiredNumberOfResults +
            ", gatherTermsForFields=" + Arrays.toString(gatherTermsForFields) +
            '}';
    }
}
