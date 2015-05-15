package com.jivesoftware.os.miru.writer.deployable;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.jivesoftware.os.amza.berkeleydb.BerkeleyDBWALIndexProvider;
import com.jivesoftware.os.amza.service.AmzaService;
import com.jivesoftware.os.amza.service.EmbeddedAmzaServiceInitializer;
import com.jivesoftware.os.amza.service.WALIndexProviderRegistry;
import com.jivesoftware.os.amza.service.replication.SendFailureListener;
import com.jivesoftware.os.amza.service.replication.TakeFailureListener;
import com.jivesoftware.os.amza.service.storage.RegionPropertyMarshaller;
import com.jivesoftware.os.amza.shared.PrimaryIndexDescriptor;
import com.jivesoftware.os.amza.shared.RegionProperties;
import com.jivesoftware.os.amza.shared.RingHost;
import com.jivesoftware.os.amza.shared.RowsChanged;
import com.jivesoftware.os.amza.shared.UpdatesSender;
import com.jivesoftware.os.amza.shared.UpdatesTaker;
import com.jivesoftware.os.amza.shared.WALStorageDescriptor;
import com.jivesoftware.os.amza.shared.stats.AmzaStats;
import com.jivesoftware.os.amza.transport.http.replication.HttpUpdatesSender;
import com.jivesoftware.os.amza.transport.http.replication.HttpUpdatesTaker;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.jive.utils.ordered.id.TimestampedOrderIdProvider;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.marshall.MiruVoidByte;
import com.jivesoftware.os.miru.api.wal.MiruActivityLookupEntry;
import com.jivesoftware.os.miru.api.wal.RCVSCursor;
import com.jivesoftware.os.miru.api.wal.RCVSSipCursor;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import com.jivesoftware.os.miru.ui.MiruSoyRendererInitializer;
import com.jivesoftware.os.miru.ui.MiruSoyRendererInitializer.MiruSoyRendererConfig;
import com.jivesoftware.os.miru.wal.MiruWALDirector;
import com.jivesoftware.os.miru.wal.MiruWALInitializer;
import com.jivesoftware.os.miru.wal.activity.rcvs.RCVSActivityWALReader;
import com.jivesoftware.os.miru.wal.activity.rcvs.RCVSActivityWALWriter;
import com.jivesoftware.os.miru.wal.lookup.MiruWALLookup;
import com.jivesoftware.os.miru.wal.lookup.RCVSWALLookup;
import com.jivesoftware.os.miru.wal.readtracking.MiruReadTrackingWALReader;
import com.jivesoftware.os.miru.wal.readtracking.MiruReadTrackingWALWriter;
import com.jivesoftware.os.miru.wal.readtracking.rcvs.RCVSReadTrackingWALReader;
import com.jivesoftware.os.miru.wal.readtracking.rcvs.RCVSReadTrackingWALWriter;
import com.jivesoftware.os.miru.writer.partition.AmzaPartitionIdProvider;
import com.jivesoftware.os.miru.writer.partition.MiruPartitionIdProvider;
import com.jivesoftware.os.rcvs.inmemory.InMemoryRowColumnValueStoreInitializer;
import java.io.File;
import java.util.List;
import org.merlin.config.BindInterfaceToConfiguration;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author jonathan.colt
 */
public class MiruWriterUIServiceNGTest {

    private MiruWriterUIService service;
    private MiruTenantId tenantId;
    private List<MiruHost> hosts;
    private MiruPartitionId partitionId;

    @BeforeClass
    public void before() throws Exception {
        tenantId = new MiruTenantId("test1".getBytes());
        partitionId = MiruPartitionId.of(0);
        hosts = Lists.newArrayList();

        MiruSoyRendererConfig config = BindInterfaceToConfiguration.bindDefault(MiruSoyRendererConfig.class);
        config.setPathToSoyResources("src/main/home/resources/soy");

        InMemoryRowColumnValueStoreInitializer rowColumnValueStoreInitializer = new InMemoryRowColumnValueStoreInitializer();
        ObjectMapper mapper = new ObjectMapper();

        MiruWALInitializer.MiruWAL wal = new MiruWALInitializer().initialize("test", rowColumnValueStoreInitializer, mapper);
        wal.getActivityLookupTable().add(MiruVoidByte.INSTANCE, tenantId, -1L, new MiruActivityLookupEntry(-1, -1, -1, false), null, null);
        wal.getWriterPartitionRegistry().add(MiruVoidByte.INSTANCE, tenantId, 1, MiruPartitionId.of(0), null, null);

        RCVSActivityWALWriter activityWALWriter = new RCVSActivityWALWriter(wal.getActivityWAL(), wal.getActivitySipWAL());
        RCVSActivityWALReader activityWALReader = new RCVSActivityWALReader(wal.getActivityWAL(), wal.getActivitySipWAL());
        MiruReadTrackingWALWriter readTrackingWALWriter = new RCVSReadTrackingWALWriter(wal.getReadTrackingWAL(), wal.getReadTrackingSipWAL());
        MiruReadTrackingWALReader readTrackingWALReader = new RCVSReadTrackingWALReader(wal.getReadTrackingWAL(), wal.getReadTrackingSipWAL());
        MiruWALLookup walLookup = new RCVSWALLookup(wal.getActivityLookupTable(), wal.getRangeLookupTable());

        File amzaDataDir = Files.createTempDir();
        File amzaIndexDir = Files.createTempDir();

        RingHost ringHost = new RingHost("localhost", 10000);
        final TimestampedOrderIdProvider orderIdProvider = new OrderIdProviderImpl(new ConstantWriterIdProvider(1));
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);

        WALIndexProviderRegistry indexProviderRegistry = new WALIndexProviderRegistry();
        String[] walIndexDirs = new String[] { amzaIndexDir.getAbsolutePath() };
        indexProviderRegistry.register("berkeleydb", new BerkeleyDBWALIndexProvider(walIndexDirs, walIndexDirs.length));

        AmzaStats amzaStats = new AmzaStats();
        UpdatesSender changeSetSender = new HttpUpdatesSender(amzaStats);
        UpdatesTaker tableTaker = new HttpUpdatesTaker(amzaStats);

        final com.jivesoftware.os.amza.service.AmzaServiceInitializer.AmzaServiceConfig amzaServiceConfig =
            new com.jivesoftware.os.amza.service.AmzaServiceInitializer.AmzaServiceConfig();
        amzaServiceConfig.workingDirectories = new String[] { amzaDataDir.getAbsolutePath() };
        amzaServiceConfig.numberOfDeltaStripes = amzaServiceConfig.workingDirectories.length;
        amzaServiceConfig.numberOfApplierThreads = 1;
        amzaServiceConfig.numberOfReplicatorThreads = 1;
        amzaServiceConfig.numberOfResendThreads = 1;
        amzaServiceConfig.numberOfTakerThreads = 1;

        RegionPropertyMarshaller regionPropertyMarshaller = new RegionPropertyMarshaller() {

            @Override
            public RegionProperties fromBytes(byte[] bytes) throws Exception {
                return mapper.readValue(bytes, RegionProperties.class);
            }

            @Override
            public byte[] toBytes(RegionProperties regionProperties) throws Exception {
                return mapper.writeValueAsBytes(regionProperties);
            }
        };

        AmzaService amzaService = new EmbeddedAmzaServiceInitializer().initialize(amzaServiceConfig,
            amzaStats,
            ringHost,
            orderIdProvider,
            regionPropertyMarshaller,
            indexProviderRegistry,
            changeSetSender,
            tableTaker,
            Optional.<SendFailureListener>absent(),
            Optional.<TakeFailureListener>absent(),
            (RowsChanged rc) -> {
            });

        amzaService.start();

        MiruWALDirector<RCVSCursor, RCVSSipCursor> director = new MiruWALDirector<>(walLookup,
            activityWALReader, activityWALWriter, readTrackingWALReader, readTrackingWALWriter);

        WALStorageDescriptor storageDescriptor = new WALStorageDescriptor(new PrimaryIndexDescriptor("berkeleydb", 0, false, null),
            null, 1000, 1000);
        MiruPartitionIdProvider miruPartitionIdProvider = new AmzaPartitionIdProvider(amzaService,
            storageDescriptor,
            100_000,
            director);

        MiruSoyRenderer renderer = new MiruSoyRendererInitializer().initialize(config);
        service = new MiruWriterUIServiceInitializer().initialize(renderer, new MiruStats());
    }

    @Test(enabled = false)
    public void testRenderActivityWALWithTenant() throws Exception {
        /*String rendered = service.renderActivityWALWithTenant(tenantId);
        assertTrue(rendered.contains(tenantId.toString()));
        assertTrue(rendered.contains("/miru/wal/activity/" + tenantId + "/" + partitionId + "#focus"));*/
    }

}
