package com.netflix.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.ws.rs.core.MultivaluedMap;

public class MultiMapUtil {

  public static Map<String, Collection<String>> getStringCollectionMap(
      MultivaluedMap<String, String> headers) {
    final Set<Entry<String, List<String>>> entries = headers.entrySet();
    Map<String, Collection<String>> map = new HashMap<>();
    for (Entry<String, List<String>> entry : entries) {
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }

}
