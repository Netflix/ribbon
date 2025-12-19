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

package internal.com.netflix.util.concurrent;

import java.util.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * ShutdownEnabledTimer class handles runtime shutdown issues.
 *
 * Apparently, adding a runtime shutdown hook will create a global reference
 * which can cause memory leaks if not cleaned up.
 *
 * This abstraction provides a wrapped mechanism to manage those runtime
 * shutdown hooks.
 *
 * @author jzarfoss
 *
 */
public class ShutdownEnabledTimer extends Timer {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ShutdownEnabledTimer.class);

  private Thread cancelThread;
  private String name;

  public ShutdownEnabledTimer(String name, boolean daemon) {
    super(name, daemon);

    this.name = name;

    this.cancelThread = new Thread(new Runnable() {
      public void run() {
        ShutdownEnabledTimer.super.cancel();
      }
    });

    LOGGER.info("Shutdown hook installed for: {}", this.name);

    Runtime.getRuntime().addShutdownHook(this.cancelThread);
  }

  @Override
  public void cancel() {
    super.cancel();

    LOGGER.info("Shutdown hook removed for: {}", this.name);

    try {
      Runtime.getRuntime().removeShutdownHook(this.cancelThread);
    } catch (IllegalStateException ise) {
      LOGGER.info("Exception caught (might be ok if at shutdown)", ise);
    }

  }

}
