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
package com.netflix.client.config;

import static org.junit.Assert.*;

import org.junit.Test;

import com.netflix.client.SimpleVipAddressResolver;
import com.netflix.config.ConfigurationManager;

public class SimpleVipAddressResolverTest {
	@Test
	public void test() {
		ConfigurationManager.getConfigInstance().setProperty("foo", "abc");
		ConfigurationManager.getConfigInstance().setProperty("bar", "xyz");
		SimpleVipAddressResolver resolver = new SimpleVipAddressResolver();
		String resolved = resolver.resolve("www.${foo}.com,myserver,${bar}", null);
		assertEquals("www.abc.com,myserver,xyz", resolved);
	}
	
	@Test
	public void testNoMacro() {
		ConfigurationManager.getConfigInstance().setProperty("foo", "abc");
		ConfigurationManager.getConfigInstance().setProperty("bar", "xyz");
		SimpleVipAddressResolver resolver = new SimpleVipAddressResolver();
		String resolved = resolver.resolve("www.foo.com,myserver,bar", null);
		assertEquals("www.foo.com,myserver,bar", resolved);
	}

	@Test
	public void testUndefinedProp() {
		ConfigurationManager.getConfigInstance().setProperty("bar", "xyz");
		SimpleVipAddressResolver resolver = new SimpleVipAddressResolver();
		String resolved = resolver.resolve("www.${var}.com,myserver,${bar}", null);
		assertEquals("www.${var}.com,myserver,xyz", resolved);
	}

}
