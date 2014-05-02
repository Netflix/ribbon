package com.netflix.ribbonclientextensions.interfaces;

import com.netflix.ribbonclientextensions.Resource;

import java.util.Map;

public interface CacheProvider<Result> extends Map<Resource<Result>, Result> {

}
