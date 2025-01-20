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
package internal.com.netflix.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for getting a count per last X milliseconds
 *
 * @author stonse
 * @author gkim
 *
 */
public class MeasuredRate {
  private final AtomicLong _lastBucket = new AtomicLong(0);
  private final AtomicLong _currentBucket = new AtomicLong(0);
  private final long _sampleInterval;
  private volatile long _threshold;

  /**
   * @param sampleInterval in milliseconds
   */
  public MeasuredRate(long sampleInterval){
    _sampleInterval = sampleInterval;

    _threshold = System.currentTimeMillis() + sampleInterval;
  }

  /**
   * @return the count in the last sample interval
   */
  public long getCount() {
    checkAndResetWindow();
    return _lastBucket.get();
  }

  /**
   * @return the count in the current sample interval which will be incomplete.
   * If you are looking for accurate counts/interval - use {@link MeasuredRate#getCount()}
   * instead.
   */
  public long getCurrentCount() {
    checkAndResetWindow();
    return _currentBucket.get();
  }

  /**
   * Increments the count in the current sample interval.  If the current
   * interval has exceeded, assigns the current count to the
   * last bucket and zeros out the current bucket
   */
  public void increment() {
    checkAndResetWindow();
    _currentBucket.incrementAndGet();
  }

  private void checkAndResetWindow() {
    long now = System.currentTimeMillis();
    if(_threshold < now) {
      _lastBucket.set(_currentBucket.get());
      _currentBucket.set(0);
      _threshold = now + _sampleInterval;
    }
  }

  public String toString(){
    StringBuilder sb = new StringBuilder();
    sb.append("count:" + getCount());
    sb.append("currentCount:" + getCurrentCount());
    return sb.toString();
  }

  public static void main(String args[]){
    MeasuredRate mr = new MeasuredRate(500);

    for(int i=0; i<1000; i++){
      mr.increment();
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    System.out.println("mr:" + mr);

  }
}
