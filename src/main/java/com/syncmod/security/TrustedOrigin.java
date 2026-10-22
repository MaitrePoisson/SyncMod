package com.syncmod.security;

import java.util.Locale;
import java.util.regex.Pattern;

public record TrustedOrigin(String host, int port) {
    private static final Pattern HOSTNAME = Pattern.compile("^[A-Za-z0-9.\\-]{1,253}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    public static TrustedOrigin of(String rawHost, int port) {
        if (rawHost == null || port < 1 || port > 65535) {
            return null;
        }
        String host = rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        int scope = host.indexOf('%');
        if (scope >= 0) {
            host = host.substring(0, scope);
        }
        if (host.contains(":")) {
            if (!IPV6.matcher(host).matches()) {
                return null;
            }
            host = "[" + host + "]";
        } else if (!HOSTNAME.matcher(host).matches()) {
            return null;
        }
        return new TrustedOrigin(host.toLowerCase(Locale.ROOT), port);
    }

    public String baseUrl() {
        return "http://" + host + ":" + port;
    }
}
