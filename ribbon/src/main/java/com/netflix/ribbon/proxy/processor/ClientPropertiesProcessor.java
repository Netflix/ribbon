package com.netflix.ribbon.proxy.processor;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.AggregatedConfiguration;
import com.netflix.config.ConcurrentMapConfiguration;
import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.ResourceGroup.GroupBuilder;
import com.netflix.ribbon.ResourceGroup.TemplateBuilder;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.proxy.annotation.ClientProperties;
import com.netflix.ribbon.proxy.annotation.ClientProperties.Property;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.Configuration;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author Allen Wang
 */
public class ClientPropertiesProcessor implements AnnotationProcessor<GroupBuilder, TemplateBuilder> {
    @Override
    public void process(String templateName, TemplateBuilder templateBuilder, Method method) {
    }

    @Override
    public void process(String groupName, GroupBuilder groupBuilder, RibbonResourceFactory resourceFactory, Class<?> interfaceClass) {
        ClientProperties properties = interfaceClass.getAnnotation(ClientProperties.class);
        if (properties != null) {
            IClientConfig config = resourceFactory.getClientConfigFactory().newConfig();
            for (Property prop : properties.properties()) {
                String name = prop.name();
                config.set(CommonClientConfigKey.valueOf(name), prop.value());
            }
            ClientOptions options = ClientOptions.from(config);
            groupBuilder.withClientOptions(options);
            if (properties.exportToArchaius()) {
                exportPropertiesToArchaius(groupName, config, interfaceClass.getName());
            }
        }
    }

    private void exportPropertiesToArchaius(String groupName, IClientConfig config, String configName) {
        Map<String, Object> map = config.getProperties();
        Configuration configuration = ConfigurationManager.getConfigInstance();
        if (configuration instanceof AggregatedConfiguration) {
            AggregatedConfiguration ac = (AggregatedConfiguration) configuration;
            configuration = ac.getConfiguration(configName);
            if (configuration == null) {
                configuration = new ConcurrentMapConfiguration();
                ac.addConfiguration((AbstractConfiguration) configuration, configName);
            }
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            configuration.setProperty(groupName + "." + config.getNameSpace() + "." + entry.getKey(), entry.getValue());
        }
    }
}
