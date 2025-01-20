package com.netflix.client.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ThreadUtils {

  public static ThreadFactory threadFactory(String name) {
    return r -> {
      Thread thread = Executors.defaultThreadFactory().newThread(r);
      thread.setName(name);
      thread.setDaemon(true);
      return thread;
    };
  }
}
