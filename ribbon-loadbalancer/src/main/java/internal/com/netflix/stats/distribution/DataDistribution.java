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

import java.util.Date;


/**
 * A {@link DataAccumulator} that also publishes statistics about the "previous" buffer.
 * This implements {@link DataDistributionMBean}
 * and so can be registered as an MBean and accessed via JMX if desired.
 *
 * @author netflixoss
 * @version $Revision: $
 */
public class DataDistribution extends DataAccumulator implements DataDistributionMBean {

    private long numValues = 0L;
    private double mean = 0.0;
    private double variance = 0.0;
    private double stddev = 0.0;
    private double min = 0.0;
    private double max = 0.0;
    private long ts = 0L;
    private long interval = 0L;
    private int size = 0;
    private final double[] percents;
    private final double[] percentiles;

    /**
     * Creates a new DataDistribution with no data summarized.
     *
     * @param bufferSize the size of each buffer held by the {@code DataAccumulator}
     * @param percents array of percentile values to calculate when buffers
     *    are swapped and new data is published.
     *    The array values must be in the range {@code [0 .. 100]}.
     */
    public DataDistribution(int bufferSize, double[] percents) {
        super(bufferSize);
        assert percentsOK(percents);
        this.percents = percents;
        this.percentiles = new double[percents.length];
    }

    private static boolean percentsOK(double[] percents) {
        if (percents == null) {
            return false;
        }
        for (int i = 0; i < percents.length; i++) {
            if (percents[i] < 0.0 || percents[i] > 100.0) { // SUPPRESS CHECKSTYLE MagicNumber
                return false;
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    protected void publish(DataBuffer buf) {
        ts = System.currentTimeMillis();
        numValues = buf.getNumValues();
        mean = buf.getMean();
        variance = buf.getVariance();
        stddev = buf.getStdDev();
        min = buf.getMinimum();
        max = buf.getMaximum();
        interval = buf.getSampleIntervalMillis();
        size = buf.getSampleSize();
        buf.getPercentiles(percents, percentiles);
    }

    /*
     * DataDistributionMBean protocol
     */

    /** {@inheritDoc} */
    public void clear() {
        numValues = 0L;
        mean = 0.0;
        variance = 0.0;
        stddev = 0.0;
        min = 0.0;
        max = 0.0;
        ts = 0L;
        interval = 0L;
        size = 0;
        for (int i = 0; i < percentiles.length; i++) {
            percentiles[i] = 0.0;
        }
    }

    /** {@inheritDoc} */
    public long getNumValues() {
        return numValues;
    }

    /** {@inheritDoc} */
    public double getMean() {
        return mean;
    }

    /** {@inheritDoc} */
    public double getVariance() {
        return variance;
    }

    /** {@inheritDoc} */
    public double getStdDev() {
        return stddev;
    }

    /** {@inheritDoc} */
    public double getMinimum() {
        return min;
    }

    /** {@inheritDoc} */
    public double getMaximum() {
        return max;
    }

    /** {@inheritDoc} */
    public String getTimestamp() {
        return new Date(getTimestampMillis()).toString();
    }

    /** {@inheritDoc} */
    public long getTimestampMillis() {
        return ts;
    }

    /** {@inheritDoc} */
    public long getSampleIntervalMillis() {
        return interval;
    }

    /** {@inheritDoc} */
    public int getSampleSize() {
        return size;
    }

    /** {@inheritDoc} */
    public double[] getPercents() {
        return percents;
    }

    /** {@inheritDoc} */
    public double[] getPercentiles() {
        return percentiles;
    }

} // DataDistribution
