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
        JSONObject block = new JSONObject();
        block.put("type", "block");
        block.put("tag", "block");
        singOutbounds.put(block);

        // Resolve through Cloudflare DoT over the VLESS proxy. A plain UDP DNS
        // request may be blocked by mobile networks or fail on VLESS transports
        // that do not carry UDP reliably.
        JSONObject dnsTLS = new JSONObject();
        dnsTLS.put("enabled", true);
        dnsTLS.put("server_name", "cloudflare-dns.com");
        JSONObject dnsServer = new JSONObject();
        dnsServer.put("type", "tls");
        dnsServer.put("tag", "dns-remote");
        dnsServer.put("server", "1.1.1.1");
        dnsServer.put("server_port", 853);
        dnsServer.put("tls", dnsTLS);
        dnsServer.put("detour", "proxy");
        JSONObject dns = new JSONObject();
        dns.put("servers", new JSONArray().put(dnsServer));
        dns.put("final", "dns-remote");
        dns.put("strategy", "ipv4_only");

        JSONObject route = new JSONObject();
        route.put("auto_detect_interface", true);
        route.put("final", "proxy");

        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("level", "info"));
        root.put("dns", dns);
        root.put("inbounds", new JSONArray().put(
            new JSONObject().put("type", "tun").put("tag", "tun-in")
                .put("interface_name", "seyro").put("address", new JSONArray().put("172.19.0.1/30"))
                .put("mtu", 1380).put("auto_route", true).put("strict_route", true)
        ));
        root.put("outbounds", singOutbounds);
        root.put("route", route);
        return root.toString(2);
    }
}
