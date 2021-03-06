package com.jivesoftware.os.miru.api.realtime;

import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import java.util.List;

/**
 *
 */
public interface MiruRealtimeDelivery {

    int deliver(MiruPartitionCoord coord, List<Long> activityTimes) throws Exception;
}
