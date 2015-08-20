package com.netflix.client.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleEntry;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

public class CaseInsensitiveMultiMap implements HttpHeaders {
    Multimap<String, Entry<String, String>> map = ArrayListMultimap.create();

    @Override
    public String getFirstValue(String headerName) {
        Collection<Entry<String, String>> entries = map.get(headerName.toLowerCase());
        if (entries == null || entries.isEmpty()) {
            return null;
        } 
        return entries.iterator().next().getValue();
    }

    @Override
    public List<String> getAllValues(String headerName) {
        Collection<Entry<String, String>> entries = map.get(headerName.toLowerCase());
        List<String> values = Lists.newArrayList();
        if (entries != null) {
            for (Entry<String, String> entry: entries) {
                values.add(entry.getValue());
            }
        }
        return values;
    }

    @Override
    public List<Entry<String, String>> getAllHeaders() {
        Collection<Entry<String, String>> all = map.values();
        return new ArrayList<Entry<String, String>>(all);
    }

    @Override
    public boolean containsHeader(String name) {
        return map.containsKey(name.toLowerCase());
    }
    
    public void addHeader(String name, String value) {
        if (getAllValues(name).contains(value)) {
            return;
        }
        SimpleEntry<String, String> entry = new SimpleEntry<String, String>(name, value);
        map.put(name.toLowerCase(), entry);
    }
    
    Map<String, Collection<String>> asMap() {
        Multimap<String, String> result = ArrayListMultimap.create();
        Collection<Entry<String, String>> all = map.values();
        for (Entry<String, String> entry: all) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result.asMap();
    }
}
