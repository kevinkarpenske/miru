package com.jivesoftware.os.miru.service.partition;

import com.google.common.hash.Hashing;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.index.BloomIndex;
import com.jivesoftware.os.miru.plugin.partition.MiruHostedPartition;
import com.jivesoftware.os.miru.service.MiruServiceConfig;
import com.jivesoftware.os.miru.service.stream.MiruContextFactory;
import com.jivesoftware.os.miru.service.stream.MiruIndexAuthz;
import com.jivesoftware.os.miru.service.stream.MiruIndexBloom;
import com.jivesoftware.os.miru.service.stream.MiruIndexFieldValues;
import com.jivesoftware.os.miru.service.stream.MiruIndexLatest;
import com.jivesoftware.os.miru.service.stream.MiruIndexPairedLatest;
import com.jivesoftware.os.miru.service.stream.MiruIndexer;
import com.jivesoftware.os.miru.service.stream.MiruRebuildDirector;
import com.jivesoftware.os.miru.wal.activity.MiruActivityWALReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author jonathan
 */
public class MiruLocalPartitionFactory {

    private final MiruServiceConfig config;
    private final MiruContextFactory miruContextFactory;
    private final MiruActivityWALReader activityWALReader;
    private final MiruPartitionEventHandler partitionEventHandler;
    private final MiruRebuildDirector rebuildDirector;
    private final ScheduledExecutorService scheduledBoostrapExecutor;
    private final ScheduledExecutorService scheduledRebuildExecutor;
    private final ScheduledExecutorService scheduledSipMigrateExecutor;
    private final ExecutorService rebuildExecutors;
    private final ExecutorService sipIndexExecutor;
    private final int rebuildIndexerThreads;
    private final MiruIndexRepairs indexRepairs;

    public MiruLocalPartitionFactory(MiruServiceConfig config,
        MiruContextFactory miruContextFactory,
        MiruActivityWALReader activityWALReader,
        MiruPartitionEventHandler partitionEventHandler,
        MiruRebuildDirector rebuildDirector,
        ScheduledExecutorService scheduledBoostrapExecutor,
        ScheduledExecutorService scheduledRebuildExecutor,
        ScheduledExecutorService scheduledSipMigrateExecutor,
        ExecutorService rebuildExecutors,
        ExecutorService sipIndexExecutor,
        int rebuildIndexerThreads,
        MiruIndexRepairs indexRepairs) {
        this.config = config;
        this.miruContextFactory = miruContextFactory;
        this.activityWALReader = activityWALReader;
        this.partitionEventHandler = partitionEventHandler;
        this.rebuildDirector = rebuildDirector;
        this.scheduledBoostrapExecutor = scheduledBoostrapExecutor;
        this.scheduledRebuildExecutor = scheduledRebuildExecutor;
        this.scheduledSipMigrateExecutor = scheduledSipMigrateExecutor;
        this.rebuildExecutors = rebuildExecutors;
        this.sipIndexExecutor = sipIndexExecutor;
        this.rebuildIndexerThreads = rebuildIndexerThreads;
        this.indexRepairs = indexRepairs;
    }

    public <BM> MiruHostedPartition<BM> create(MiruBitmaps<BM> bitmaps, MiruPartitionCoord coord) throws Exception {
        return new MiruLocalHostedPartition<>(bitmaps,
            coord,
            miruContextFactory,
            activityWALReader,
            partitionEventHandler,
            rebuildDirector,
            scheduledBoostrapExecutor,
            scheduledRebuildExecutor,
            scheduledSipMigrateExecutor,
            rebuildExecutors,
            sipIndexExecutor,
            rebuildIndexerThreads,
            indexRepairs,
            new MiruIndexer<>(
                new MiruIndexAuthz<BM>(),
                new MiruIndexFieldValues<BM>(),
                new MiruIndexBloom<>(new BloomIndex<>(bitmaps, Hashing.murmur3_128(), 100_000, 0.01f)),
                new MiruIndexLatest<BM>(),
                new MiruIndexPairedLatest<BM>()),
            config.getPartitionWakeOnIndex(),
            config.getPartitionRebuildBatchSize(),
            config.getPartitionSipBatchSize(),
            config.getMergeAfterLiveCount(),
            config.getMergeAfterRebuildCount(),
            new MiruLocalHostedPartition.Timings(
                config.getPartitionRebuildFailureSleepMillis(),
                config.getPartitionBootstrapIntervalInMillis(),
                config.getPartitionRebuildIntervalInMillis(),
                config.getPartitionSipMigrateIntervalInMillis(),
                config.getPartitionBanUnregisteredSchemaMillis(),
                config.getPartitionReleaseContextCacheAfterMillis(),
                config.getPartitionMigrationWaitInMillis()));
    }

    public void prioritizeRebuild(MiruLocalHostedPartition<?> partition) {
        rebuildDirector.prioritize(partition);
    }
}
