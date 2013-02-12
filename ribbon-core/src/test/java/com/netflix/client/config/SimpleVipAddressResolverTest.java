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
