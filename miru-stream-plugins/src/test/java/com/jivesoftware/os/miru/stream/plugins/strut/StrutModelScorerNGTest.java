package com.jivesoftware.os.miru.stream.plugins.strut;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.jivesoftware.os.filer.chunk.store.ChunkStoreInitializer;
import com.jivesoftware.os.filer.chunk.store.transaction.KeyToFPCacheFactory;
import com.jivesoftware.os.filer.chunk.store.transaction.MapCreator;
import com.jivesoftware.os.filer.chunk.store.transaction.MapOpener;
import com.jivesoftware.os.filer.chunk.store.transaction.TxCogs;
import com.jivesoftware.os.filer.chunk.store.transaction.TxMapGrower;
import com.jivesoftware.os.filer.io.ByteBufferFactory;
import com.jivesoftware.os.filer.io.FilerIO;
import com.jivesoftware.os.filer.io.HeapByteBufferFactory;
import com.jivesoftware.os.filer.io.IBA;
import com.jivesoftware.os.filer.io.api.KeyedFilerStore;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.filer.io.chunk.ChunkStore;
import com.jivesoftware.os.filer.io.map.MapContext;
import com.jivesoftware.os.filer.keyed.store.TxKeyedFilerStore;
import com.jivesoftware.os.jive.utils.collections.bah.LRUConcurrentBAHLinkedHash;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.lab.LABEnvironment;
import com.jivesoftware.os.lab.LabHeapPressure;
import com.jivesoftware.os.lab.api.ValueIndex;
import com.jivesoftware.os.lab.guts.Leaps;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.plugin.cache.LabLastIdCacheKeyValues;
import com.jivesoftware.os.miru.plugin.cache.MiruPluginCacheProvider.LastIdCacheKeyValues;
import com.jivesoftware.os.miru.plugin.context.KeyValueRawhide;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.testng.annotations.Test;

/**
 * @author jonathan.colt
 */
public class StrutModelScorerNGTest {

    @Test(enabled = false, description = "No filer cache impl")
    public void testChunk() throws Exception {
        StackBuffer stackBuffer = new StackBuffer();
        ByteBufferFactory byteBufferFactory = new HeapByteBufferFactory();

        ChunkStore[] chunkStores = new ChunkStore[2];
        ChunkStoreInitializer chunkStoreInitializer = new ChunkStoreInitializer();
        for (int i = 0; i < 2; i++) {
            chunkStores[i] = chunkStoreInitializer.create(byteBufferFactory,
                1024,
                byteBufferFactory,
                10,
                100,
                stackBuffer);
        }

        TxCogs transientCogs = new TxCogs(1024, 1024,
            new ConcurrentKeyToFPCacheFactory(),
            new NullKeyToFPCacheFactory(),
            new NullKeyToFPCacheFactory());

        String catwalkId = "catwalkId";
        String modelId = "modelId";

        @SuppressWarnings("unchecked")
        KeyedFilerStore<Integer, MapContext>[] cacheStores = new KeyedFilerStore[16];
        for (int i = 0; i < cacheStores.length; i++) {
            cacheStores[i] = new TxKeyedFilerStore<>(transientCogs,
                1234,
                chunkStores,
                ("cache-" + i + "-" + catwalkId).getBytes(),
                false,
                new MapCreator(2, (int) FilerIO.chunkLength(i), true, 8, false),
                MapOpener.INSTANCE,
                TxMapGrower.MAP_OVERWRITE_GROWER,
                TxMapGrower.MAP_REWRITE_GROWER);

        }

        LastIdCacheKeyValues cacheKeyValues = null; //new MiruFilerCacheKeyValues("test", cacheStores);

        assertScores(modelId, cacheKeyValues, stackBuffer);

    }

    @Test
    public void testLab() throws Exception {

        File root = Files.createTempDir();
        LabHeapPressure labHeapPressure = new LabHeapPressure(1024 * 1024 * 10, new AtomicLong());
        LRUConcurrentBAHLinkedHash<Leaps> leapCache = LABEnvironment.buildLeapsCache(1_000_000, 8);

        LABEnvironment env = new LABEnvironment(LABEnvironment.buildLABCompactorThreadPool(4), LABEnvironment.buildLABDestroyThreadPool(1), root,
            false, labHeapPressure, 4, 10, leapCache);
        String catwalkId = "catwalkId";
        String modelId = "modelId";

        @SuppressWarnings("unchecked")
        ValueIndex[] stores = new ValueIndex[16];
        for (int i = 0; i < stores.length; i++) {
            stores[i] = env.open("cache-" + i + "-" + catwalkId, 4096, 1024 * 1024, 0, 0, 0, new KeyValueRawhide());
        }

        LastIdCacheKeyValues cacheKeyValues = new LabLastIdCacheKeyValues("test", new OrderIdProviderImpl(new ConstantWriterIdProvider(1)), stores);

        assertScores(modelId, cacheKeyValues, new StackBuffer());

    }

    private void assertScores(String modelId, LastIdCacheKeyValues cacheKeyValues, StackBuffer stackBuffer) throws Exception {
        MiruTermId[] termIds = new MiruTermId[] {
            new MiruTermId(new byte[] { (byte) 124 }),
            new MiruTermId(new byte[] { (byte) 124, (byte) 124 }),
            new MiruTermId(new byte[] { (byte) 124, (byte) 124, (byte) 124, (byte) 124 })
        };

        StrutModelScorer.score(modelId, 1, termIds, cacheKeyValues, (int termIndex, float[] scores, int lastId) -> {
            System.out.println(termIndex + " " + Arrays.toString(scores) + " " + lastId);
            return true;
        }, stackBuffer);
        System.out.println("-----------");

        List<Scored> updates = Lists.newArrayList();
        for (int i = 0; i < 1; i++) {
            updates.add(new Scored(-1, new MiruTermId(new byte[] { (byte) 97, (byte) (97 + i) }), 10, 0.5f, new float[] { 0.5f }, null));
        }

        StrutModelScorer.commit(modelId, cacheKeyValues, updates, stackBuffer);

        System.out.println("-----------");

        StrutModelScorer.score(modelId, 1, termIds, cacheKeyValues, (int termIndex, float[] scores, int lastId) -> {
            System.out.println(termIndex + " " + Arrays.toString(scores) + " " + lastId);
            return true;
        }, stackBuffer);
    }

    private static class ConcurrentKeyToFPCacheFactory implements KeyToFPCacheFactory {

        @Override
        public Map<IBA, Long> createCache() {
            return new ConcurrentHashMap<>();
        }
    }

    private static class NullKeyToFPCacheFactory implements KeyToFPCacheFactory {

        @Override
        public Map<IBA, Long> createCache() {
            return null;
        }
    }

}
