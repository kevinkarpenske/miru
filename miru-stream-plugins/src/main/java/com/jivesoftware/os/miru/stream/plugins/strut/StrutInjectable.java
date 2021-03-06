package com.jivesoftware.os.miru.stream.plugins.strut;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruQueryServiceException;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.plugin.Miru;
import com.jivesoftware.os.miru.plugin.MiruProvider;
import com.jivesoftware.os.miru.plugin.partition.MiruPartitionUnavailableException;
import com.jivesoftware.os.miru.plugin.solution.MiruPartitionResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruRequest;
import com.jivesoftware.os.miru.plugin.solution.MiruRequestAndReport;
import com.jivesoftware.os.miru.plugin.solution.MiruResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLogLevel;
import com.jivesoftware.os.miru.plugin.solution.MiruSolvableFactory;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 *
 */
public class StrutInjectable {

    private final MiruProvider<? extends Miru> provider;
    private final StrutModelScorer modelScorer;
    private final Strut strut;
    private final int maxTermIdsPerRequest;
    private final boolean allowImmediateRescore;
    private final int gatherBatchSize;
    private final boolean gatherParallel;
    private final int scoreConcurrencyLevel;
    private final boolean parallelSolverEnabled;
    private final Set<String> verboseModelIds;
    private final ExecutorService gatherExecutorService;

    public StrutInjectable(MiruProvider<? extends Miru> provider,
        StrutModelScorer modelScorer,
        Strut strut,
        int maxTermIdsPerRequest,
        boolean allowImmediateRescore,
        int gatherBatchSize,
        boolean gatherParallel,
        int scoreConcurrencyLevel,
        boolean parallelSolverEnabled,
        Set<String> verboseModelIds,
        ExecutorService gatherExecutorService) {
        this.provider = provider;
        this.modelScorer = modelScorer;
        this.strut = strut;
        this.maxTermIdsPerRequest = maxTermIdsPerRequest;
        this.allowImmediateRescore = allowImmediateRescore;
        this.gatherBatchSize = gatherBatchSize;
        this.gatherParallel = gatherParallel;
        this.scoreConcurrencyLevel = scoreConcurrencyLevel;
        this.parallelSolverEnabled = parallelSolverEnabled;
        this.verboseModelIds = verboseModelIds;
        this.gatherExecutorService = gatherExecutorService;
    }

    public MiruResponse<StrutAnswer> strut(MiruRequest<StrutQuery> request) throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askAndMerge(tenantId,
                new MiruSolvableFactory<>(request.name, provider.getStats(),
                    "strut",
                    new StrutQuestion(modelScorer,
                        strut,
                        provider.getBackfillerizer(tenantId),
                        request,
                        provider.getRemotePartition(StrutRemotePartition.class),
                        maxTermIdsPerRequest,
                        allowImmediateRescore,
                        gatherBatchSize,
                        gatherParallel,
                        scoreConcurrencyLevel,
                        verboseModelIds,
                        gatherExecutorService)),
                new StrutAnswerEvaluator(parallelSolverEnabled),
                new StrutAnswerMerger(request.query.desiredNumberOfResults),
                StrutAnswer.EMPTY_RESULTS,
                miru.getDefaultExecutor(),
                request.logLevel);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to strut", e);
        }
    }

    public MiruPartitionResponse<StrutAnswer> strut(MiruPartitionId partitionId,
        MiruRequestAndReport<StrutQuery, StrutReport> requestAndReport)
        throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = requestAndReport.request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askImmediate(tenantId,
                partitionId,
                new MiruSolvableFactory<>(requestAndReport.request.name, provider.getStats(),
                    "strut",
                    new StrutQuestion(modelScorer,
                        strut,
                        provider.getBackfillerizer(tenantId),
                        requestAndReport.request,
                        provider.getRemotePartition(StrutRemotePartition.class),
                        maxTermIdsPerRequest,
                        allowImmediateRescore,
                        gatherBatchSize,
                        gatherParallel,
                        scoreConcurrencyLevel,
                        verboseModelIds,
                        gatherExecutorService)),
                Optional.fromNullable(requestAndReport.report),
                StrutAnswer.EMPTY_RESULTS,
                MiruSolutionLogLevel.NONE);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed remote strut for tenant: " + requestAndReport.request.tenantId +
                " partition: " + partitionId.getId(), e);
        }
    }

    public MiruResponse<StrutAnswer> strut(MiruPartitionId partitionId,
        MiruRequest<StrutQuery> request) throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askAndMergePartition(tenantId,
                partitionId,
                new MiruSolvableFactory<>(request.name, provider.getStats(),
                    "strut",
                    new StrutQuestion(modelScorer,
                        strut,
                        provider.getBackfillerizer(tenantId),
                        request,
                        provider.getRemotePartition(StrutRemotePartition.class),
                        maxTermIdsPerRequest,
                        allowImmediateRescore,
                        gatherBatchSize,
                        gatherParallel,
                        scoreConcurrencyLevel,
                        verboseModelIds,
                        gatherExecutorService)),
                new StrutAnswerMerger(request.query.desiredNumberOfResults),
                StrutAnswer.EMPTY_RESULTS,
                StrutAnswer.EMPTY_RESULTS,
                miru.getDefaultExecutor(),
                request.logLevel);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed single strut for tenant: " + request.tenantId + " partition: " + partitionId.getId(), e);
        }
    }

    public void share(StrutShare share) throws MiruQueryServiceException, InterruptedException {
        try {
            MiruHost host = provider.getHost();
            modelScorer.shareIn(new MiruPartitionCoord(share.tenantId, share.partitionId, host), share);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to share strut for tenant: " + share.tenantId + " partition: " + share.partitionId.getId(), e);
        }
    }
}
