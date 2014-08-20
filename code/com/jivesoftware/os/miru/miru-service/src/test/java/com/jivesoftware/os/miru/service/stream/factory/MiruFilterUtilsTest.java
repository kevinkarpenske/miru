package com.jivesoftware.os.miru.service.stream.factory;

import com.google.common.base.Optional;
import com.googlecode.javaewah.EWAHCompressedBitmap;
import com.googlecode.javaewah.IntIterator;
import com.jivesoftware.os.jive.utils.io.Filer;
import com.jivesoftware.os.jive.utils.io.RandomAccessFiler;
import com.jivesoftware.os.miru.service.index.BulkExport;
import com.jivesoftware.os.miru.service.index.MiruFilerProvider;
import com.jivesoftware.os.miru.service.index.MiruTimeIndex;
import com.jivesoftware.os.miru.service.index.disk.MiruOnDiskTimeIndex;
import com.jivesoftware.os.miru.service.index.memory.MiruInMemoryTimeIndex;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 *
 */
public class MiruFilterUtilsTest {

    @Test(dataProvider = "evenTimeIndexDataProvider")
    public void testBuildEvenTimeRangeMask(MiruFilterUtils utils, MiruTimeIndex miruTimeIndex) throws Exception {
        final int size = (EWAHCompressedBitmap.WORD_IN_BITS * 3) + 1;
        for (int lower = 0; lower <= size / 2; lower++) {
            int upper = size - 1 - lower;

            EWAHCompressedBitmap bitmap = utils.buildTimeRangeMask(miruTimeIndex, lower, upper);
            if (lower == 0) {
                // 0 case is inclusive due to ambiguity
                assertExpectedNumberOfConsecutiveBitsStartingFromN(bitmap, lower, size - 2 * lower);
            } else if (lower == upper) {
                // the lower and upper are the same so there should be nothing left
                assertExpectedNumberOfConsecutiveBitsStartingFromN(bitmap, -1, 0);
            } else {
                assertExpectedNumberOfConsecutiveBitsStartingFromN(bitmap, lower + 1, size - 1 - 2 * lower);
            }
        }
    }

    @Test(dataProvider = "oddTimeIndexDataProvider")
    public void testBuildOddTimeRangeMask(MiruFilterUtils utils, MiruTimeIndex miruTimeIndex) throws Exception {
        final int size = EWAHCompressedBitmap.WORD_IN_BITS * 3;
        for (int lower = 0; lower < size / 2; lower++) {
            int upper = size - 1 - lower;

            EWAHCompressedBitmap bitmap = utils.buildTimeRangeMask(miruTimeIndex, lower, upper);
            if (lower == 0) {
                // 0 case is inclusive due to ambiguity
                assertExpectedNumberOfConsecutiveBitsStartingFromN(bitmap, lower, size - 2 * lower);
            } else if (lower == upper) {
                fail();
            } else {
                assertExpectedNumberOfConsecutiveBitsStartingFromN(bitmap, lower + 1, size - 1 - 2 * lower);
            }
        }
    }

    @Test(dataProvider = "singleEntryTimeIndexDataProvider")
    public void testSingleBitTimeRange(MiruFilterUtils utils, MiruTimeIndex miruTimeIndex) {
        EWAHCompressedBitmap bitmap = utils.buildTimeRangeMask(miruTimeIndex, 0, Long.MAX_VALUE);

        assertExpectedNumberOfConsecutiveBitsStartingFromN(bitmap, 0, 1);
    }

    private void assertExpectedNumberOfConsecutiveBitsStartingFromN(EWAHCompressedBitmap bitmap, int expectedStartingFrom, int expectedCardinality) {
        int last = -1;
        int cardinality = 0;
        int startingFrom = -1;
        IntIterator iter = bitmap.intIterator();
        while (iter.hasNext()) {
            int current = iter.next();
            if (last < 0) {
                startingFrom = current;
            } else if (last >= 0) {
                assertEquals(current - last, 1);
            }
            last = current;
            cardinality++;
        }
        assertEquals(startingFrom, expectedStartingFrom);
        assertEquals(cardinality, expectedCardinality);
    }

    @DataProvider(name = "evenTimeIndexDataProvider")
    public Object[][] evenTimeIndexDataProvider() throws Exception {
        MiruFilterUtils miruFilterUtils = new MiruFilterUtils();

        final int size = (EWAHCompressedBitmap.WORD_IN_BITS * 3) + 1;
        final long[] timestamps = new long[size];
        for (int i = 0; i < size; i++) {
            timestamps[i] = i;
        }

        MiruInMemoryTimeIndex miruInMemoryTimeIndex = buildInMemoryTimeIndex(timestamps);
        MiruOnDiskTimeIndex miruOnDiskTimeIndex = buildOnDiskTimeIndex(miruInMemoryTimeIndex);

        return new Object[][] {
            { miruFilterUtils, miruInMemoryTimeIndex },
            { miruFilterUtils, miruOnDiskTimeIndex },
        };
    }

    @DataProvider(name = "oddTimeIndexDataProvider")
    public Object[][] oddTimeIndexDataProvider() throws Exception {
        MiruFilterUtils miruFilterUtils = new MiruFilterUtils();

        final int size = EWAHCompressedBitmap.WORD_IN_BITS * 3;
        final long[] timestamps = new long[size];
        for (int i = 0; i < size; i++) {
            timestamps[i] = i;
        }

        MiruInMemoryTimeIndex miruInMemoryTimeIndex = buildInMemoryTimeIndex(timestamps);
        MiruOnDiskTimeIndex miruOnDiskTimeIndex = buildOnDiskTimeIndex(miruInMemoryTimeIndex);

        return new Object[][] {
            { miruFilterUtils, miruInMemoryTimeIndex },
            { miruFilterUtils, miruOnDiskTimeIndex },
        };
    }

    @DataProvider(name = "singleEntryTimeIndexDataProvider")
    public Object[][] singleEntryTimeIndexDataProvider() throws Exception {
        MiruFilterUtils miruFilterUtils = new MiruFilterUtils();

        final long[] timestamps = new long[] { System.currentTimeMillis() };
        MiruInMemoryTimeIndex miruInMemoryTimeIndex = buildInMemoryTimeIndex(timestamps);
        MiruOnDiskTimeIndex miruOnDiskTimeIndex = buildOnDiskTimeIndex(miruInMemoryTimeIndex);

        return new Object[][] {
            { miruFilterUtils, miruInMemoryTimeIndex },
            { miruFilterUtils, miruOnDiskTimeIndex },
        };
    }

    private MiruInMemoryTimeIndex buildInMemoryTimeIndex(final long[] timestamps) throws Exception {
        MiruInMemoryTimeIndex miruInMemoryTimeIndex = new MiruInMemoryTimeIndex(Optional.<MiruInMemoryTimeIndex.TimeOrderAnomalyStream>absent());
        miruInMemoryTimeIndex.bulkImport(new BulkExport<long[]>() {
            @Override
            public long[] bulkExport() throws Exception {
                return timestamps;
            }
        });
        return miruInMemoryTimeIndex;
    }

    private MiruOnDiskTimeIndex buildOnDiskTimeIndex(MiruInMemoryTimeIndex miruInMemoryTimeIndex) throws Exception {
        final File onDisk = Files.createTempFile("onDisk", "timeIndex").toFile();

        MiruOnDiskTimeIndex miruOnDiskTimeIndex = new MiruOnDiskTimeIndex(new MiruFilerProvider() {
            @Override
            public File getBackingFile() {
                return onDisk;
            }

            @Override
            public Filer getFiler(long length) throws IOException {
                return new RandomAccessFiler(onDisk, "rw");
            }
        }, Files.createTempDirectory("timestampToIndex").toFile());
        miruOnDiskTimeIndex.bulkImport(miruInMemoryTimeIndex);

        return miruOnDiskTimeIndex;
    }
}
