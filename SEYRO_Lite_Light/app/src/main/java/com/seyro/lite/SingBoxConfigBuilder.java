package com.seyro.lite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Converts the legacy profile JSON representation used by SEYRO's subscription
 * parser into a sing-box configuration. The JSON is only an intermediate data
 * format: Xray and HEV are not started or bundled as runtime engines.
 */
public final class SingBoxConfigBuilder {
    private SingBoxConfigBuilder() {}

    public static String fromXrayConfig(String xrayJson) throws JSONException {
        JSONObject source = new JSONObject(xrayJson);
        JSONArray outbounds = source.optJSONArray("outbounds");
        JSONObject vless = null;
        if (outbounds != null) {
            for (int i = 0; i < outbounds.length(); i++) {
                JSONObject item = outbounds.optJSONObject(i);
                if (item != null && "vless".equalsIgnoreCase(item.optString("protocol"))) {
                    vless = item;
                    break;
                }
            }
        }
        if (vless == null) throw new JSONException("Xray config has no VLESS outbound");

        JSONObject settings = vless.optJSONObject("settings");
        JSONArray servers = settings == null ? null : settings.optJSONArray("vnext");
        JSONObject server = servers == null ? null : servers.optJSONObject(0);
        JSONArray users = server == null ? null : server.optJSONArray("users");
        JSONObject user = users == null ? null : users.optJSONObject(0);
        if (server == null || user == null) throw new JSONException("VLESS server/user is missing");

        String address = server.optString("address", "");
        int port = server.optInt("port", 0);
        String uuid = user.optString("id", "");
        if (address.isEmpty() || port < 1 || uuid.isEmpty()) {
            throw new JSONException("VLESS address, port or UUID is missing");
        }

        JSONObject stream = vless.optJSONObject("streamSettings");
        if (stream == null) stream = new JSONObject();
        String network = stream.optString("network", "tcp").toLowerCase(java.util.Locale.ROOT);
        String security = stream.optString("security", "none").toLowerCase(java.util.Locale.ROOT);

        JSONObject outbound = new JSONObject();
        outbound.put("type", "vless");
        outbound.put("tag", "proxy");
        outbound.put("server", address);
        outbound.put("server_port", port);
        outbound.put("uuid", uuid);
        String flow = user.optString("flow", "");
        if (!flow.isEmpty()) outbound.put("flow", flow);
        outbound.put("packet_encoding", "xudp");

        if ("reality".equals(security) || "tls".equals(security)) {
            JSONObject tls = new JSONObject();
            tls.put("enabled", true);
            JSONObject tlsSettings = stream.optJSONObject("tlsSettings");
            JSONObject realitySettings = stream.optJSONObject("realitySettings");
            String serverName = "";
            if (realitySettings != null) serverName = realitySettings.optString("serverName", "");
            if (serverName.isEmpty() && tlsSettings != null) serverName = tlsSettings.optString("serverName", "");
            if (!serverName.isEmpty()) tls.put("server_name", serverName);

            String fingerprint = "";
            if (realitySettings != null) fingerprint = realitySettings.optString("fingerprint", "");
            if (fingerprint.isEmpty() && tlsSettings != null) {
                JSONObject fingerprintSettings = tlsSettings.optJSONObject("utls");
                if (fingerprintSettings != null) fingerprint = fingerprintSettings.optString("fingerprint", "");
            }
            if (!fingerprint.isEmpty()) {
                JSONObject utls = new JSONObject();
                utls.put("enabled", true);
                utls.put("fingerprint", fingerprint);
                tls.put("utls", utls);
            }

            if ("reality".equals(security)) {
                if (realitySettings == null) throw new JSONException("REALITY settings are missing");
                String publicKey = realitySettings.optString("publicKey", "");
                String shortId = realitySettings.optString("shortId", "");
                if (publicKey.isEmpty()) throw new JSONException("REALITY publicKey is missing");
                JSONObject reality = new JSONObject();
                reality.put("enabled", true);
                reality.put("public_key", publicKey);
                if (!shortId.isEmpty()) reality.put("short_id", shortId);
                tls.put("reality", reality);
            }
            outbound.put("tls", tls);
        } else if (!"none".equals(security) && !security.isEmpty()) {
            throw new JSONException("Unsupported Xray stream security: " + security);
        }

        JSONObject transport = new JSONObject();
        boolean hasTransport = true;
        if ("tcp".equals(network) || "raw".equals(network)) {
            hasTransport = false;
        } else if ("grpc".equals(network)) {
            transport.put("type", "grpc");
            JSONObject grpc = stream.optJSONObject("grpcSettings");
            if (grpc != null) {
                String serviceName = grpc.optString("serviceName", "");
                if (!serviceName.isEmpty()) transport.put("service_name", serviceName);
                String authority = grpc.optString("authority", "");
                if (!authority.isEmpty()) transport.put("authority", authority);
            }
        } else if ("ws".equals(network)) {
            transport.put("type", "ws");
            JSONObject ws = stream.optJSONObject("wsSettings");
            if (ws != null) {
                String path = ws.optString("path", "");
                if (!path.isEmpty()) transport.put("path", path);
                JSONObject headers = ws.optJSONObject("headers");
                if (headers != null) transport.put("headers", headers);
            }
        } else if ("httpupgrade".equals(network)) {
            transport.put("type", "httpupgrade");
            JSONObject upgrade = stream.optJSONObject("httpupgradeSettings");
            if (upgrade != null) {
                String path = upgrade.optString("path", "");
                if (!path.isEmpty()) transport.put("path", path);
                String host = upgrade.optString("host", "");
                if (!host.isEmpty()) transport.put("host", host);
            }
        } else {
            throw new JSONException("Unsupported Xray transport for initial sing-box test: " + network);
        }
        if (hasTransport) outbound.put("transport", transport);

        JSONArray singOutbounds = new JSONArray();
        singOutbounds.put(outbound);
        JSONObject direct = new JSONObject();
        direct.put("type", "direct");
        direct.put("tag", "direct");
        singOutbounds.put(direct);

        // Use Android's resolver for DNS lookups required by outbound connection
        // setup, while keeping ordinary tunneled client DNS on encrypted DoT
        // through VLESS. This avoids DNS bootstrap recursion and reduces dependence
        // on a single remote resolver for internal outbound lookups.
        JSONObject dnsLocal = new JSONObject();
        dnsLocal.put("type", "local");
        dnsLocal.put("tag", "dns-local");
        JSONObject dnsTLS = new JSONObject();
        dnsTLS.put("enabled", true);
        dnsTLS.put("server_name", "cloudflare-dns.com");
        JSONObject dnsRemote = new JSONObject();
        dnsRemote.put("type", "tls");
        dnsRemote.put("tag", "dns-remote");
        dnsRemote.put("server", "1.1.1.1");
        dnsRemote.put("server_port", 853);
        dnsRemote.put("tls", dnsTLS);
        dnsRemote.put("detour", "proxy");
        JSONObject dns = new JSONObject();
        dns.put("servers", new JSONArray().put(dnsLocal).put(dnsRemote));
        dns.put("rules", new JSONArray().put(
            new JSONObject().put("outbound", "any").put("server", "dns-local")
        ));
        dns.put("final", "dns-remote");
        dns.put("strategy", "prefer_ipv4");

        JSONObject route = new JSONObject();
        route.put("auto_detect_interface", true);
        route.put("find_process", false);
        JSONArray routeRules = new JSONArray();
        routeRules.put(new JSONObject().put("protocol", "dns").put("action", "hijack-dns"));
        routeRules.put(new JSONObject().put("port", 53).put("action", "hijack-dns"));
        // Keep private/LAN destinations on the device's physical network.
        routeRules.put(new JSONObject().put("ip_is_private", true).put("outbound", "direct"));
        // Do not let app-level DoT bypass the VPN. DNS for the tunnel is handled
        // by sing-box's configured resolver; reject independent TCP/853 sessions.
        routeRules.put(new JSONObject().put("port", 853).put("action", "reject").put("method", "default"));
        // Reject QUIC/HTTP3 cleanly so browsers can fall back to TCP-based HTTP/2.
        // An action rule avoids the noisy unsupported packet-listen path in block outbound.
        routeRules.put(new JSONObject().put("network", "udp").put("port", 443)
            .put("action", "reject").put("method", "default"));
        route.put("rules", routeRules);
        route.put("final", "proxy");

        JSONObject root = new JSONObject();
        // Temporary diagnostic build: expose TUN TCP routing decisions and dial failures.
        // Restore info after capturing one representative failing connection.
        root.put("log", new JSONObject().put("level", "debug"));
        root.put("dns", dns);
        JSONArray inbounds = new JSONArray();
        inbounds.put(new JSONObject().put("type", "tun").put("tag", "tun-in")
            .put("interface_name", "seyro").put("address", new JSONArray().put("172.19.0.1/30"))
            .put("mtu", 1380).put("auto_route", true).put("strict_route", false));
        // Loopback-only SOCKS listener is used solely for a real HTTP health-check
        // through sing-box's proxy outbound. It is not exposed to the local network.
        inbounds.put(new JSONObject().put("type", "socks").put("tag", "health-check-in")
            .put("listen", "127.0.0.1").put("listen_port", 2080));
        root.put("inbounds", inbounds);
        root.put("outbounds", singOutbounds);
        root.put("route", route);
        return root.toString(2);
    }
}
