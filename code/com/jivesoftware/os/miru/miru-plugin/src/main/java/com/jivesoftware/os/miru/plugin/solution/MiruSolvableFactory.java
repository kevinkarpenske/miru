package com.jivesoftware.os.miru.plugin.solution;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.plugin.partition.MiruPartitionUnavailableException;
import com.jivesoftware.os.miru.plugin.partition.MiruQueryablePartition;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.util.concurrent.Callable;

/**
 * @param <Q> query type
 * @param <A> answer type
 * @param <P> report type
 */
public class MiruSolvableFactory<Q, A, P> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final MiruStats miruStats;
    private final String queryKey;
    private final Question<Q, A, P> question;

    public MiruSolvableFactory(MiruStats miruStats, String queryKey, Question<Q, A, P> question) {
        this.miruStats = miruStats;
        this.queryKey = queryKey;
        this.question = question;
    }

    public <BM> MiruSolvable<A> create(final MiruQueryablePartition<BM> replica, final Optional<P> report) {
        Callable<MiruPartitionResponse<A>> callable = new Callable<MiruPartitionResponse<A>>() {
            @Override
            public MiruPartitionResponse<A> call() throws Exception {
                try (MiruRequestHandle<BM> handle = replica.acquireQueryHandle()) {
                    if (handle.isLocal()) {
                        long start = System.currentTimeMillis();
                        MiruPartitionResponse<A> response = question.askLocal(handle, report);
                        long latency = System.currentTimeMillis() - start;
                        miruStats.egressed(queryKey + ">local", 1, latency);
                        miruStats.egressed(queryKey + ">local>" + replica.getCoord().tenantId.toString() + ">" + replica.getCoord().partitionId.getId(), 1,
                            latency);
                        return response;
                    } else {
                        long start = System.currentTimeMillis();
                        MiruRemotePartitionReader<Q, A, P> remotePartitionReader = new MiruRemotePartitionReader<>(question.getRemotePartition(),
                            handle.getRequestHelper());
                        MiruPartitionResponse<A> response = remotePartitionReader.read(handle.getCoord().partitionId, question.getRequest(), report);
                        long latency = System.currentTimeMillis() - start;
                        miruStats.egressed(queryKey + ">remote", 1, latency);
                        miruStats.egressed(queryKey + ">remote>" + replica.getCoord().host.toStringForm(), 1, latency);
                        miruStats.egressed(queryKey + ">remote>" + replica.getCoord().tenantId.toString() + ">" + replica.getCoord().partitionId.getId(), 1,
                            latency);
                        return response;
                    }
                } catch (MiruPartitionUnavailableException e) {
                    LOG.info("Partition unavailable on {}: {}", replica.getCoord(), e.getMessage());
                    throw e;
                } catch (Throwable t) {
                    LOG.info("Solvable encountered a problem", t);
                    throw t;
                }
            }
        };
        return new MiruSolvable<>(replica.getCoord(), callable);
    }

    public Question<Q, A, P> getQuestion() {
        return question;
    }

    public Optional<P> getReport(Optional<A> answer) {
        return question.createReport(answer);
    }

    public String getQueryKey() {
        return queryKey;
    }
}
