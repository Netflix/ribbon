/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package internal.com.netflix.stats.distribution;

import java.util.concurrent.locks.Lock;


/**
 * A double-buffer of {@link DataBuffer} objects.
 * One is the "current" buffer, and new data is added to it.
 * The other is the "previous" buffer, and is used as a sorce
 * of computed statistics.
 *
 * @see DataPublisher
 *
 * @author $Author: netflixoss $
 */
public abstract class DataAccumulator implements DataCollector {

    private DataBuffer current;
    private DataBuffer previous;
    private final Object swapLock = new Object();

    /*
     * Constructor(s)
     */

    /**
     * Creates a new initially empty DataAccumulator.
     *
     * @param bufferSize the size of the buffers to use
     */
    public DataAccumulator(int bufferSize) {
        this.current = new DataBuffer(bufferSize);
        this.previous = new DataBuffer(bufferSize);
    }

    /*
     * Accumulating new values
     */

    /** {@inheritDoc} */
    public void noteValue(double val) {
        synchronized (swapLock) {
            Lock l = current.getLock();
            l.lock();
            try {
                current.noteValue(val);
            } finally {
                l.unlock();
            }
        }
    }

    /**
     * Swaps the data collection buffers, and computes statistics
     * about the data collected up til now.
     */
    public void publish() {
        /*
         * Some care is required here to correctly swap the DataBuffers,
         * but not hold the synchronization object while compiling stats
         * (a potentially long computation).  This ensures that continued
         * data collection (calls to noteValue()) will not be blocked for any
         * significant period.
         */
        DataBuffer tmp = null;
        Lock l = null;
        synchronized (swapLock) {
            // Swap buffers
            tmp = current;
            current = previous;
            previous = tmp;
            // Start collection in the new "current" buffer
            l = current.getLock();
            l.lock();
            try {
                current.startCollection();
            } finally {
                l.unlock();
            }
            // Grab lock on new "previous" buffer
            l = tmp.getLock();
            l.lock();
        }
        // Release synchronizaton *before* publishing data
        try {
            tmp.endCollection();
            publish(tmp);
        } finally {
            l.unlock();
        }
    }

    /**
     * Called to publish recently collected data.
     * When called, the {@link Lock} associated with the "previous"
     * buffer is held, so the data will not be changed.
     * Other locks have been released, and so new data can be
     * collected in the "current" buffer.
     * The data in the buffer has also been sorted in increasing order.
     *
     * @param buf the {@code DataBuffer} that is now "previous".
     */
    protected abstract void publish(DataBuffer buf);

} // DataAccumulator
