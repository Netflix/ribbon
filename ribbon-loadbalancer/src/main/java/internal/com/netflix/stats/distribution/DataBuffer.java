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

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * A fixed-size data collection buffer that holds a sliding window
 * of the most recent values added.
 * The {@code DataBuffer} is also a {@link Distribution} and so collects
 * basic statistics about the data added to the buffer.
 * This statistical data is managed on-the-fly, and reflects all the data
 * added, if those values that may have been dropped due to buffer overflow.
 * <p>
 * This class is <em>not</em> synchronized, but can instead managed by a
 * {@link Lock} attached to the {@code DataBuffer} (see {@link #getLock}).
 * @author netflixoss
 */
public class DataBuffer extends Distribution {

    private final Lock lock;
    private final double[] buf;
    private long startMillis;
    private long endMillis;
    private int size;
    private int insertPos;

    /*
     * Constructors
     */

    /**
     * Creates a new {@code DataBuffer} with a given capacity.
     */
    public DataBuffer(int capacity) {
        lock = new ReentrantLock();
        buf = new double[capacity];
        startMillis = 0;
        size = 0;
        insertPos = 0;
    }

    /*
     * Accessors
     */

    /**
     * Gets the {@link Lock} to use to manage access to the
     * contents of the {@code DataBuffer}.
     */
    public Lock getLock() {
        return lock;
    }

    /**
     * Gets the capacity of the {@code DataBuffer}; that is,
     * the maximum number of values that the {@code DataBuffer} can hold.
     */
    public int getCapacity() {
        return buf.length;
    }

    /**
     * Gets the length of time over which the data was collected,
     * in milliseconds.
     * The value is only valid after {@link #endCollection}
     * has been called (and before a subsequent call to {@link #startCollection}).
     */
    public long getSampleIntervalMillis() {
        return (endMillis - startMillis);
    }

    /**
     * Gets the number of values currently held in the buffer.
     * This value may be smaller than the value of {@link #getNumValues}
     * depending on how the percentile values were computed.
     */
    public int getSampleSize() {
        return size;
    }

    /*
     * Managing the data
     */

    /** {@inheritDoc} */
    @Override
    public void clear() {
        super.clear();
        startMillis = 0;
        size = 0;
        insertPos = 0;
    }

    /**
     * Notifies the buffer that data is collection is now enabled.
     */
    public void startCollection() {
        clear();
        startMillis = System.currentTimeMillis();
    }

    /**
     * Notifies the buffer that data has just ended.
     * <p>
     * <b>Performance Note:</b>
     * <br>This method sorts the underlying data buffer,
     * and so may be slow.  It is best to call this at most once
     * and fetch all percentile values desired, instead of making
     * a number of repeated calls.
     */
    public void endCollection() {
        endMillis = System.currentTimeMillis();
        Arrays.sort(buf, 0, size);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The buffer wraps-around if it is full, overwriting the oldest
     * entry with the new value.
     */
    @Override
    public void noteValue(double val) {
        super.noteValue(val);
        buf[insertPos++] = val;
        if (insertPos >= buf.length) {
            insertPos = 0;
            size = buf.length;
        } else if (insertPos > size) {
            size = insertPos;
        }
    }

    /**
     * Gets the requested percentile statistics.
     *
     * @param percents array of percentile values to compute,
     *    which must be in the range {@code [0 .. 100]}
     * @param percentiles array to fill in with the percentile values;
     *    must be the same length as {@code percents}
     * @return the {@code percentiles} array
     * @see <a href="http://en.wikipedia.org/wiki/Percentile">Percentile (Wikipedia)</a>
     * @see <a href="http://cnx.org/content/m10805/latest/">Percentile</a>
     */
    public double[] getPercentiles(double[] percents, double[] percentiles) {
        for (int i = 0; i < percents.length; i++) {
            percentiles[i] = computePercentile(percents[i]);
        }
        return percentiles;
    }

    private double computePercentile(double percent) {
        // Some just-in-case edge cases
        if (size <= 0) {
            return 0.0;
        } else if (percent <= 0.0) {
            return buf[0];
        } else if (percent >= 100.0) {        // SUPPRESS CHECKSTYLE MagicNumber
            return buf[size - 1];
        }
        /*
         * Note:
         * Documents like http://cnx.org/content/m10805/latest
         * use a one-based ranking, while this code uses a zero-based index,
         * so the code may not look exactly like the formulas.
         */
        double index = (percent / 100.0) * size; // SUPPRESS CHECKSTYLE MagicNumber
        int iLow = (int) Math.floor(index);
        int iHigh = (int) Math.ceil(index);
        assert 0 <= iLow && iLow <= index && index <= iHigh && iHigh <= size;
        assert (iHigh - iLow) <= 1;
        if (iHigh >= size) {
            // Another edge case
            return buf[size - 1];
        } else if (iLow == iHigh) {
            return buf[iLow];
        } else {
            // Interpolate between the two bounding values
            return buf[iLow] + (index - iLow) * (buf[iHigh] - buf[iLow]);
        }
    }

} // DataBuffer
