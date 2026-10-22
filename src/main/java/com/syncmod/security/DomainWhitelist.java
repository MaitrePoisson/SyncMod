package com.syncmod.security;

import com.syncmod.config.SyncClientConfig;
import com.syncmod.util.SyncException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class DomainWhitelist {
    private final Set<String> hosts;
    private final TrustedOrigin origin;

    public DomainWhitelist(Collection<? extends String> hosts) {
        this(hosts, null);
    }

    private DomainWhitelist(Collection<? extends String> hosts, TrustedOrigin origin) {
        this.hosts = hosts.stream()
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.origin = origin;
    }

    public static DomainWhitelist fromConfig() {
        return new DomainWhitelist(SyncClientConfig.ALLOWED_DOMAINS.get());
    }

    public DomainWhitelist withOrigin(TrustedOrigin trustedOrigin) {
        return new DomainWhitelist(hosts, trustedOrigin);
    }

    public Optional<TrustedOrigin> origin() {
        return Optional.ofNullable(origin);
    }

    public Set<String> hosts() {
        return hosts;
    }

    public URI check(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new SyncException("syncmod.error.domain_blocked", url);
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null) {
            throw new SyncException("syncmod.error.domain_blocked", url);
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String scheme = uri.getScheme();
        if (origin != null && "http".equalsIgnoreCase(scheme)
                && lowerHost.equals(origin.host()) && uri.getPort() == origin.port()) {
            return uri;
        }
        boolean secure = "https".equalsIgnoreCase(scheme);
        boolean standardPort = uri.getPort() == -1 || uri.getPort() == 443;
        if (!secure || !standardPort || !hosts.contains(lowerHost)) {
            throw new SyncException("syncmod.error.domain_blocked", host);
        }
        return uri;
    }
}
