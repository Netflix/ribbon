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


/**
 * Accumulator of statistics about a distribution of
 * observed values that are produced incrementally.
 * <p>
 * Note that the implementation is <em>not</em> synchronized,
 * and simultaneous updates may produce incorrect results.
 * In most cases these incorrect results will be unimportant,
 * but applications that care should synchronize carefully
 * to ensure consistent results.
 * <p>
 * Note that this implements {@link DistributionMBean} and so can be
 * registered as an MBean and accessed via JMX if desired.
 *
 * @author netflixoss $
 * @version $Revision: $
 */
public class Distribution implements DistributionMBean, DataCollector {

    private long numValues;
    private double sumValues;
    private double sumSquareValues;
    private double minValue;
    private double maxValue;

    /*
     * Constructors
     */

    /**
     * Creates a new initially empty Distribution.
     */
    public Distribution() {
        numValues = 0L;
        sumValues = 0.0;
        sumSquareValues = 0.0;
        minValue = 0.0;
        maxValue = 0.0;
    }

    /*
     * Accumulating new values
     */

    /** {@inheritDoc} */
    public void noteValue(double val) {
        numValues++;
        sumValues += val;
        sumSquareValues += val * val;
        if (numValues == 1) {
            minValue = val;
            maxValue = val;
        } else if (val < minValue) {
            minValue = val;
        } else if (val > maxValue) {
            maxValue = val;
        }
    }

    /** {@inheritDoc} */
    public void clear() {
        numValues = 0L;
        sumValues = 0.0;
        sumSquareValues = 0.0;
        minValue = 0.0;
        maxValue = 0.0;
    }

    /*
     * Accessors
     */

    /** {@inheritDoc} */
    public long getNumValues() {
        return numValues;
    }

    /** {@inheritDoc} */
    public double getMean() {
        if (numValues < 1) {
            return 0.0;
        } else {
            return sumValues / numValues;
        }
    }

    /** {@inheritDoc} */
    public double getVariance() {
        if (numValues < 2) {
            return 0.0;
        } else if (sumValues == 0.0) {
            return 0.0;
        } else {
            double mean = getMean();
            return (sumSquareValues / numValues) - mean * mean;
        }
    }

    /** {@inheritDoc} */
    public double getStdDev() {
        return Math.sqrt(getVariance());
    }

    /** {@inheritDoc} */
    public double getMinimum() {
        return minValue;
    }

    /** {@inheritDoc} */
    public double getMaximum() {
        return maxValue;
    }

    /**
     * Add another {@link Distribution}'s values to this one.
     *
     * @param anotherDistribution
     *            the other {@link Distribution} instance
     */
    public void add(Distribution anotherDistribution) {
        if (anotherDistribution != null) {
            numValues += anotherDistribution.numValues;
            sumValues += anotherDistribution.sumValues;
            sumSquareValues += anotherDistribution.sumSquareValues;
            minValue = (minValue < anotherDistribution.minValue) ? minValue
                    : anotherDistribution.minValue;
            maxValue = (maxValue > anotherDistribution.maxValue) ? maxValue
                    : anotherDistribution.maxValue;
        }
    }

    @Override
    public String toString() {
        return new StringBuilder()
            .append("{Distribution:")
            .append("N=").append(getNumValues())
            .append(": ").append(getMinimum())
            .append("..").append(getMean())
            .append("..").append(getMaximum())
            .append("}")
            .toString();
    }

} // Distribution
