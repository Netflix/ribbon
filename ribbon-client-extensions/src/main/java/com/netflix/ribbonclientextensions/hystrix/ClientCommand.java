package com.netflix.ribbonclientextensions.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;


/**
 * @author dray
 *
 */
public abstract class ClientCommand<R> extends HystrixCommand<R> {


    protected ClientCommand(HystrixCommandGroupKey group, HystrixCommandKey command) {
        super(Setter.withGroupKey(group).andCommandKey(command));
    }

    protected ClientCommand(HystrixCommandGroupKey group, HystrixCommandKey command, ExecutionIsolationStrategy executionIsolationStrategy) {
        super(Setter.withGroupKey(group)
                .andCommandKey(command)
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(executionIsolationStrategy)));
    }
}