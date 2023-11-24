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
 * Abstract MBean interface for objects that describe the general
 * characteristics of a distribution of (double) values.
 * This interface supports the standard MBean management interface,
 * so implementing classes will support JMX monitoring.
 *
 * @author netflixoss $
 * @version $Revision: $
 */
public interface DistributionMBean {

    /**
     * Clears out the distribution, resetting it to its initial state.
     */
    void clear();

    /**
     * Get the number of values in the distribution.
     */
    long getNumValues();

    /**
     * Get the average value in the distribtion.
     */
    double getMean();

    /**
     * Get the variance (the square of the standard deviation)
     * of values in the distribution.
     */
    double getVariance();

    /**
     * Get the standard deviation of values in the distribution.
     */
    double getStdDev();

    /**
     * Get the minimum value found in the distribution.
     */
    double getMinimum();

    /**
     * Get the maximum value found in the distribution.
     */
    double getMaximum();

} // DistributionMBean
