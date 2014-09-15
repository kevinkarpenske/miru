package com.jivesoftware.os.miru.query.solution;

import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import java.util.concurrent.Callable;

/**
 *
 */
public class MiruSolvable<R> implements Callable<MiruPartitionResponse<R>> {

    private final MiruPartitionCoord coord;
    private final Callable<MiruPartitionResponse<R>> callable;

    public MiruSolvable(MiruPartitionCoord coord, Callable<MiruPartitionResponse<R>> callable) {
        this.coord = coord;
        this.callable = callable;
    }

    public MiruPartitionCoord getCoord() {
        return coord;
    }

    @Override
    public MiruPartitionResponse<R> call() throws Exception {
        return callable.call();
    }
}
