package com.netflix.niws;

import java.util.regex.Pattern;

import com.netflix.config.ConfigurationManager;
import com.netflix.niws.client.IClientConfig;

public class SimpleVipAddressResolver implements VipAddressResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.*?)\\}");

    @Override
    public String resolve(String vipAddressMacro, IClientConfig niwsClientConfig) {
        if (vipAddressMacro == null || vipAddressMacro.length() == 0) {
            return vipAddressMacro;
        }
        if (!VAR_PATTERN.matcher(vipAddressMacro).matches()) {
            return vipAddressMacro;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String address: vipAddressMacro.split(",")) {
            if (!first) {
                sb.append(",");
            }
            String interpolated = ConfigurationManager.getConfigInstance().getString(address);
            if (interpolated != null) {
                sb.append(interpolated);
            }
            first = false;
        }
        return sb.toString();
    }
}
