package com.jivesoftware.os.miru.wal.activity;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.jivesoftware.os.jive.utils.base.interfaces.CallbackStream;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.marshall.MiruVoidByte;
import com.jivesoftware.os.miru.wal.activity.rcvs.MiruActivitySipWALColumnKey;
import com.jivesoftware.os.miru.wal.activity.rcvs.MiruActivityWALColumnKey;
import com.jivesoftware.os.miru.wal.activity.rcvs.MiruActivityWALRow;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.rcvs.api.ColumnValueAndTimestamp;
import com.jivesoftware.os.rcvs.api.RowColumnValueStore;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang.mutable.MutableLong;

/** @author jonathan */
public class MiruActivityWALReaderImpl implements MiruActivityWALReader {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    private final RowColumnValueStore<MiruTenantId,
        MiruActivityWALRow, MiruActivityWALColumnKey, MiruPartitionedActivity, ? extends Exception> activityWAL;
    private final RowColumnValueStore<MiruTenantId,
        MiruActivityWALRow, MiruActivitySipWALColumnKey, MiruPartitionedActivity, ? extends Exception> activitySipWAL;
    private final RowColumnValueStore<MiruVoidByte, MiruTenantId, Integer, MiruPartitionId, ? extends Exception> writerPartitionRegistry;

    public MiruActivityWALReaderImpl(
        RowColumnValueStore<MiruTenantId, MiruActivityWALRow, MiruActivityWALColumnKey, MiruPartitionedActivity, ? extends Exception> activityWAL,
        RowColumnValueStore<MiruTenantId, MiruActivityWALRow, MiruActivitySipWALColumnKey, MiruPartitionedActivity, ? extends Exception> activitySipWAL,
        RowColumnValueStore<MiruVoidByte, MiruTenantId, Integer, MiruPartitionId, ? extends Exception> writerPartitionRegistry
        ) {

        this.activityWAL = activityWAL;
        this.activitySipWAL = activitySipWAL;
        this.writerPartitionRegistry = writerPartitionRegistry;
    }

    private MiruActivityWALRow rowKey(MiruPartitionId partition) {
        return new MiruActivityWALRow(partition.getId());
    }

    @Override
    public void stream(MiruTenantId tenantId,
        MiruPartitionId partitionId,
        long afterTimestamp,
        final int batchSize,
        long sleepOnFailureMillis,
        StreamMiruActivityWAL streamMiruActivityWAL)
        throws Exception {

        MiruActivityWALRow rowKey = rowKey(partitionId);

        final List<ColumnValueAndTimestamp<MiruActivityWALColumnKey, MiruPartitionedActivity, Long>> cvats = Lists.newArrayListWithCapacity(batchSize);
        boolean streaming = true;
        byte lastSort = MiruPartitionedActivity.Type.ACTIVITY.getSort();
        long lastTimestamp = afterTimestamp;
        while (streaming) {
            try {
                MiruActivityWALColumnKey start = new MiruActivityWALColumnKey(lastSort, lastTimestamp);
                activityWAL.getEntrys(tenantId, rowKey, start, Long.MAX_VALUE, batchSize, false, null, null,
                    new CallbackStream<ColumnValueAndTimestamp<MiruActivityWALColumnKey, MiruPartitionedActivity, Long>>() {
                        @Override
                        public ColumnValueAndTimestamp<MiruActivityWALColumnKey, MiruPartitionedActivity, Long> callback(
                            ColumnValueAndTimestamp<MiruActivityWALColumnKey, MiruPartitionedActivity, Long> v) throws Exception {

                                if (v != null) {
                                    cvats.add(v);
                                }
                                if (cvats.size() < batchSize) {
                                    return v;
                                } else {
                                    return null;
                                }
                            }
                    });

                if (cvats.size() < batchSize) {
                    streaming = false;
                }
                for (ColumnValueAndTimestamp<MiruActivityWALColumnKey, MiruPartitionedActivity, Long> v : cvats) {
                    if (streamMiruActivityWAL.stream(v.getColumn().getCollisionId(), v.getValue(), v.getTimestamp())) {
                        // add 1 to exclude last result
                        lastSort = v.getColumn().getSort();
                        lastTimestamp = v.getColumn().getCollisionId() + 1;
                    } else {
                        streaming = false;
                        break;
                    }
                }
                cvats.clear();
            } catch (InterruptedException e) {
                // interrupts are rethrown
                throw e;
            } catch (Exception e) {
                // non-interrupts are retried
                log.warn("Failure while streaming, will retry in {} ms", new Object[]{sleepOnFailureMillis}, e);
                try {
                    Thread.sleep(sleepOnFailureMillis);
                } catch (InterruptedException ie) {
                    Thread.interrupted();
                    throw new RuntimeException("Interrupted during retry after failure, expect partial results");
                }
            }
        }
    }

    @Override
    public void streamSip(MiruTenantId tenantId,
        MiruPartitionId partitionId,
        Sip afterSip,
        final int batchSize,
        long sleepOnFailureMillis,
        final StreamMiruActivityWAL streamMiruActivityWAL)
        throws Exception {

        MiruActivityWALRow rowKey = rowKey(partitionId);

        final List<ColumnValueAndTimestamp<MiruActivitySipWALColumnKey, MiruPartitionedActivity, Long>> cvats = Lists.newArrayListWithCapacity(batchSize);
        boolean streaming = true;
        byte lastSort = MiruPartitionedActivity.Type.ACTIVITY.getSort();
        long lastClockTimestamp = afterSip.clockTimestamp;
        long lastActivityTimestamp = afterSip.activityTimestamp;
        while (streaming) {
            try {
                MiruActivitySipWALColumnKey start = new MiruActivitySipWALColumnKey(lastSort, lastClockTimestamp, lastActivityTimestamp);
                activitySipWAL.getEntrys(tenantId, rowKey, start, Long.MAX_VALUE, batchSize, false, null, null,
                    new CallbackStream<ColumnValueAndTimestamp<MiruActivitySipWALColumnKey, MiruPartitionedActivity, Long>>() {
                        @Override
                        public ColumnValueAndTimestamp<MiruActivitySipWALColumnKey, MiruPartitionedActivity, Long> callback(
                            ColumnValueAndTimestamp<MiruActivitySipWALColumnKey, MiruPartitionedActivity, Long> v) throws Exception {

                                if (v != null) {
                                    cvats.add(v);
                                }
                                if (cvats.size() < batchSize) {
                                    return v;
                                } else {
                                    return null;
                                }
                            }
                    });

                if (cvats.size() < batchSize) {
                    streaming = false;
                }
                for (ColumnValueAndTimestamp<MiruActivitySipWALColumnKey, MiruPartitionedActivity, Long> v : cvats) {
                    if (streamMiruActivityWAL.stream(v.getColumn().getCollisionId(), v.getValue(), v.getTimestamp())) {
                        // add 1 to exclude last result
                        lastSort = v.getColumn().getSort();
                        lastClockTimestamp = v.getColumn().getCollisionId();
                        lastActivityTimestamp = v.getColumn().getSipId() + 1;
                    } else {
                        streaming = false;
                        break;
                    }
                }
                cvats.clear();
            } catch (InterruptedException e) {
                // interrupts are rethrown
                throw e;
            } catch (Exception e) {
                // non-interrupts are retried
                log.warn("Failure while streaming, will retry in {} ms", new Object[]{sleepOnFailureMillis}, e);
                try {
                    Thread.sleep(sleepOnFailureMillis);
                } catch (InterruptedException ie) {
                    Thread.interrupted();
                    throw new RuntimeException("Interrupted during retry after failure, expect partial results");
                }
            }
        }
    }

    @Override
    public MiruActivityWALStatus getStatus(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        final MutableLong count = new MutableLong(0);
        final List<Integer> begins = Lists.newArrayList();
        final List<Integer> ends = Lists.newArrayList();
        activityWAL.getValues(tenantId,
            new MiruActivityWALRow(partitionId.getId()),
            new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.END.getSort(), 0),
            null, 1_000, false, null, null, new CallbackStream<MiruPartitionedActivity>() {
                @Override
                public MiruPartitionedActivity callback(MiruPartitionedActivity partitionedActivity) throws Exception {
                    if (partitionedActivity != null) {
                        if (partitionedActivity.type == MiruPartitionedActivity.Type.BEGIN) {
                            count.add(partitionedActivity.index);
                            begins.add(partitionedActivity.writerId);
                        } else if (partitionedActivity.type == MiruPartitionedActivity.Type.END) {
                            ends.add(partitionedActivity.writerId);
                        }
                    }
                    return partitionedActivity;
                }
            });
        return new MiruActivityWALStatus(count.longValue(), begins, ends);
    }

    @Override
    public long countSip(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        final MutableLong count = new MutableLong(0);
        activitySipWAL.getValues(tenantId,
            new MiruActivityWALRow(partitionId.getId()),
            new MiruActivitySipWALColumnKey(MiruPartitionedActivity.Type.BEGIN.getSort(), 0, 0),
            null, 1_000, false, null, null, new CallbackStream<MiruPartitionedActivity>() {
                @Override
                public MiruPartitionedActivity callback(MiruPartitionedActivity partitionedActivity) throws Exception {
                    if (partitionedActivity != null && partitionedActivity.type == MiruPartitionedActivity.Type.BEGIN) {
                        count.add(partitionedActivity.index);
                    }
                    return partitionedActivity;
                }
            });
        return count.longValue();
    }

    @Override
    public MiruPartitionedActivity findExisting(MiruTenantId tenantId, MiruPartitionId partitionId, MiruPartitionedActivity activity) throws Exception {
        return activityWAL.get(
            tenantId,
            new MiruActivityWALRow(partitionId.getId()),
            new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.ACTIVITY.getSort(), activity.timestamp),
            null, null);
    }

    @Override
    public long oldestActivityClockTimestamp(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        final MutableLong oldestClockTimestamp = new MutableLong(-1);
        activityWAL.getValues(tenantId,
            new MiruActivityWALRow(partitionId.getId()),
            new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.ACTIVITY.getSort(), 0L),
            null,
            1,
            false,
            null,
            null,
            new CallbackStream<MiruPartitionedActivity>() {
                @Override
                public MiruPartitionedActivity callback(MiruPartitionedActivity miruPartitionedActivity) throws Exception {
                    if (miruPartitionedActivity != null && miruPartitionedActivity.type.isActivityType()) {
                        oldestClockTimestamp.setValue(miruPartitionedActivity.clockTimestamp);
                    }
                    return null; // one and done
                }
            });
        return oldestClockTimestamp.longValue();
    }

    @Override
    public Optional<MiruPartitionId> getLatestPartitionIdForTenant(MiruTenantId tenantId) throws Exception {
        final AtomicReference<MiruPartitionId> latestPartitionId = new AtomicReference<>();
        writerPartitionRegistry.getValues(MiruVoidByte.INSTANCE, tenantId, null, null, 1_000, false, null, null, new CallbackStream<MiruPartitionId>() {
            @Override
            public MiruPartitionId callback(MiruPartitionId partitionId) throws Exception {
                if (partitionId != null) {
                    MiruPartitionId latest = latestPartitionId.get();
                    if (latest == null || partitionId.compareTo(latest) > 0) {
                        latestPartitionId.set(partitionId);
                    }
                }
                return partitionId;
            }
        });
        return Optional.fromNullable(latestPartitionId.get());
    }

}
