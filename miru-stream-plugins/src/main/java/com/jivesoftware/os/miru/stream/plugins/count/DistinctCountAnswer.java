package com.jivesoftware.os.miru.stream.plugins.count;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/** @author jonathan */
public class DistinctCountAnswer {

    public static final DistinctCountAnswer EMPTY_RESULTS = new DistinctCountAnswer(ImmutableSet.<String>of(), 0, true);

    public final ImmutableSet<String> aggregateTerms;
    public final int collectedDistincts;
    public final boolean resultsExhausted;

    public DistinctCountAnswer(ImmutableSet<String> aggregateTerms, int collectedDistincts, boolean resultsExhausted) {
        this.aggregateTerms = aggregateTerms;
        this.collectedDistincts = collectedDistincts;
        this.resultsExhausted = resultsExhausted;
    }

    @JsonCreator
    public static DistinctCountAnswer fromJson(
        @JsonProperty("aggregateTerms") Set<String> aggregateTerms,
        @JsonProperty("collectedDistincts") int collectedDistincts,
        @JsonProperty("resultsExhausted") boolean resultsExhausted) {
        return new DistinctCountAnswer(ImmutableSet.copyOf(aggregateTerms), collectedDistincts, resultsExhausted);
    }

    @Override
    public String toString() {
        return "DistinctCountAnswer{" +
            "aggregateTerms=" + aggregateTerms +
            ", collectedDistincts=" + collectedDistincts +
            ", resultsExhausted=" + resultsExhausted +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DistinctCountAnswer that = (DistinctCountAnswer) o;

        if (collectedDistincts != that.collectedDistincts) {
            return false;
        }
        if (resultsExhausted != that.resultsExhausted) {
            return false;
        }
        return !(aggregateTerms != null ? !aggregateTerms.equals(that.aggregateTerms) : that.aggregateTerms != null);
    }

    @Override
    public int hashCode() {
        int result = aggregateTerms != null ? aggregateTerms.hashCode() : 0;
        result = 31 * result + collectedDistincts;
        result = 31 * result + (resultsExhausted ? 1 : 0);
        return result;
    }
}
