package com.jivesoftware.os.miru.service.index.filer;

import com.jivesoftware.os.filer.io.FilerIO;
import com.jivesoftware.os.filer.io.IBA;
import com.jivesoftware.os.filer.io.StripingLocksProvider;
import com.jivesoftware.os.filer.io.api.KeyRange;
import com.jivesoftware.os.filer.io.api.KeyedFilerStore;
import com.jivesoftware.os.filer.io.map.MapContext;
import com.jivesoftware.os.filer.io.map.MapStore;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.index.MiruFieldIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInvertedIndex;
import com.jivesoftware.os.miru.plugin.index.TermIdStream;
import java.util.Arrays;
import java.util.List;

/**
 * @author jonathan
 */
public class MiruFilerFieldIndex<BM> implements MiruFieldIndex<BM> {

    private final MiruBitmaps<BM> bitmaps;
    private final long[] indexIds;
    private final KeyedFilerStore<Long, Void>[] indexes;
    private final KeyedFilerStore<Integer, MapContext>[] cardinalities;
    private final int cardinalityInitialCapacity;
    // We could lock on both field + termId for improved hash/striping, but we favor just termId to reduce object creation
    private final StripingLocksProvider<MiruTermId> stripingLocksProvider;

    public MiruFilerFieldIndex(MiruBitmaps<BM> bitmaps,
        long[] indexIds,
        KeyedFilerStore<Long, Void>[] indexes,
        KeyedFilerStore<Integer, MapContext>[] cardinalities,
        int cardinalityInitialCapacity,
        StripingLocksProvider<MiruTermId> stripingLocksProvider) throws Exception {
        this.bitmaps = bitmaps;
        this.indexIds = indexIds;
        this.indexes = indexes;
        this.cardinalities = cardinalities;
        this.cardinalityInitialCapacity = cardinalityInitialCapacity;
        this.stripingLocksProvider = stripingLocksProvider;
    }

    @Override
    public void append(int fieldId, MiruTermId termId, int[] ids, long[] counts) throws Exception {
        getIndex(fieldId, termId, -1).append(ids);
        mergeCardinalities(fieldId, termId, ids, counts);
    }

    @Override
    public void set(int fieldId, MiruTermId termId, int[] ids, long[] counts) throws Exception {
        getIndex(fieldId, termId, -1).set(ids);
        mergeCardinalities(fieldId, termId, ids, counts);
    }

    @Override
    public void remove(int fieldId, MiruTermId termId, int id) throws Exception {
        getIndex(fieldId, termId, -1).remove(id);
        mergeCardinalities(fieldId, termId, new int[] { id }, cardinalities[fieldId] != null ? new long[1] : null);
    }

    @Override
    public void streamTermIdsForField(int fieldId, List<KeyRange> ranges, final TermIdStream termIdStream) throws Exception {
        indexes[fieldId].streamKeys(ranges, iba -> termIdStream.stream(new MiruTermId(iba.getBytes())));
    }

    @Override
    public MiruInvertedIndex<BM> get(int fieldId, MiruTermId termId) throws Exception {
        return getIndex(fieldId, termId, -1);
    }

    @Override
    public MiruInvertedIndex<BM> get(int fieldId, MiruTermId termId, int considerIfIndexIdGreaterThanN) throws Exception {
        return getIndex(fieldId, termId, considerIfIndexIdGreaterThanN);
    }

    @Override
    public MiruInvertedIndex<BM> getOrCreateInvertedIndex(int fieldId, MiruTermId term) throws Exception {
        return getIndex(fieldId, term, -1);
    }

    private MiruInvertedIndex<BM> getIndex(int fieldId, MiruTermId termId, int considerIfIndexIdGreaterThanN) throws Exception {
        return new MiruFilerInvertedIndex<>(bitmaps, new IndexKey(indexIds[fieldId], termId.getBytes()), indexes[fieldId],
            considerIfIndexIdGreaterThanN, stripingLocksProvider.lock(termId, 0));
    }

    @Override
    public long getCardinality(int fieldId, MiruTermId termId, int id) throws Exception {
        if (cardinalities[fieldId] != null) {
            Long count = cardinalities[fieldId].read(termId.getBytes(), null, (monkey, filer, lock) -> {
                if (filer != null) {
                    synchronized (lock) {
                        byte[] payload = MapStore.INSTANCE.getPayload(filer, monkey, FilerIO.intBytes(id));
                        if (payload != null) {
                            return FilerIO.bytesLong(payload);
                        }
                    }
                }
                return null;
            });
            return count != null ? count : 0;
        }
        return -1;
    }

    @Override
    public long[] getCardinalities(int fieldId, MiruTermId termId, int[] ids) throws Exception {
        long[] counts = new long[ids.length];
        if (cardinalities[fieldId] != null) {
            cardinalities[fieldId].read(termId.getBytes(), null, (monkey, filer, lock) -> {
                if (filer != null) {
                    synchronized (lock) {
                        for (int i = 0; i < ids.length; i++) {
                            if (ids[i] >= 0) {
                                byte[] payload = MapStore.INSTANCE.getPayload(filer, monkey, FilerIO.intBytes(ids[i]));
                                if (payload != null) {
                                    counts[i] = FilerIO.bytesLong(payload);
                                }
                            }
                        }
                    }
                }
                return null;
            });
        } else {
            Arrays.fill(counts, -1);
        }
        return counts;
    }

    @Override
    public long getGlobalCardinality(int fieldId, MiruTermId termId) throws Exception {
        return getCardinality(fieldId, termId, -1);
    }

    @Override
    public void mergeCardinalities(int fieldId, MiruTermId termId, int[] ids, long[] counts) throws Exception {
        if (cardinalities[fieldId] != null && counts != null) {
            cardinalities[fieldId].readWriteAutoGrow(termId.getBytes(), ids.length, (monkey, filer, lock) -> {
                synchronized (lock) {
                    long delta = 0;
                    for (int i = 0; i < ids.length; i++) {
                        byte[] key = FilerIO.intBytes(ids[i]);
                        long keyHash = MapStore.INSTANCE.hash(key, 0, key.length);
                        byte[] payload = MapStore.INSTANCE.getPayload(filer, monkey, keyHash, key);
                        long existing = payload != null ? FilerIO.bytesLong(payload) : 0;
                        MapStore.INSTANCE.add(filer, monkey, (byte) 1, keyHash, key, FilerIO.longBytes(counts[i]));
                        delta += counts[i] - existing;
                    }

                    byte[] globalKey = FilerIO.intBytes(-1);
                    byte[] globalPayload = MapStore.INSTANCE.getPayload(filer, monkey, globalKey);
                    long globalExisting = globalPayload != null ? FilerIO.bytesLong(globalPayload) : 0;
                    MapStore.INSTANCE.add(filer, monkey, (byte) 1, globalKey, FilerIO.longBytes(globalExisting + delta));
                }
                return null;
            });
        }
    }

    private IBA cardinalityKey(MiruTermId termId, int id) {
        if (id < 0) {
            return null;
        }
        byte[] termBytes = termId.getBytes();
        byte[] keyBytes = new byte[termBytes.length + 4];
        System.arraycopy(termBytes, 0, keyBytes, 0, termBytes.length);
        FilerIO.intBytes(id, keyBytes, termBytes.length);
        return new IBA(keyBytes);
    }
}
