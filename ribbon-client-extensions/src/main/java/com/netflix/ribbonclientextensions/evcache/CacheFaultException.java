package com.netflix.ribbonclientextensions.evcache;

import com.netflix.evcache.EVCacheException;

/**
 * Created by mcohen on 4/22/14.
 */
public class CacheFaultException extends Exception {
    public CacheFaultException(Throwable throwable) {
        super(throwable);
    }
}
