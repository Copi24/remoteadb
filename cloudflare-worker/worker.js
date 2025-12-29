// Cloudflare Worker: provisions a per-device Cloudflare Tunnel + DNS + run token.
// Secure this endpoint with Cloudflare Access (recommended) and rate limits.
//
// Required env vars:
// - CF_API_TOKEN: API token with permissions to manage Tunnels + DNS for the zone
// - CF_ACCOUNT_ID: Cloudflare account id
// - CF_ZONE_ID: Zone id for your base domain
// - CF_DNS_SUFFIX: e.g. "adb.676967.xyz" (deviceId will be prepended)
//
// Expected request JSON:
// {"deviceId":"<stable-id>"}
// Response JSON:
// {"hostname":"<deviceId>.adb.676967.xyz","token":"<tunnel-run-token>"}

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response("Method Not Allowed", { status: 405 });
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return new Response("Bad JSON", { status: 400 });
    }

    const rawId = String(body.deviceId || "");
    const deviceId = rawId.toLowerCase().replace(/[^a-z0-9]/g, "").slice(0, 32);
    if (!deviceId) return new Response("Missing deviceId", { status: 400 });

    const suffix = env.CF_DNS_SUFFIX || "676967.xyz";
    const hostname = `${deviceId}.${suffix}`;
    const tunnelName = `remoteadb-${deviceId}`;

    const apiBase = "https://api.cloudflare.com/client/v4";
    const headers = {
      Authorization: `Bearer ${env.CF_API_TOKEN}`,
      "Content-Type": "application/json",
    };

    // 1) Find existing tunnel by name (idempotent), otherwise create it.
    let tunnelId;
    {
      const listRes = await fetch(
        `${apiBase}/accounts/${env.CF_ACCOUNT_ID}/cfd_tunnel?name=${encodeURIComponent(tunnelName)}`,
        { method: "GET", headers }
      );
      const listJson = await listRes.json();
      if (listRes.ok && listJson?.success && Array.isArray(listJson.result) && listJson.result[0]?.id) {
        tunnelId = listJson.result[0].id;
      }
    }

    if (!tunnelId) {
      const secretBytes = new Uint8Array(32);
      crypto.getRandomValues(secretBytes);
      const tunnelSecret = btoa(String.fromCharCode(...secretBytes));

      const createRes = await fetch(`${apiBase}/accounts/${env.CF_ACCOUNT_ID}/cfd_tunnel`, {
        method: "POST",
        headers,
        body: JSON.stringify({ name: tunnelName, tunnel_secret: tunnelSecret }),
      });
      const createJson = await createRes.json();
      if (!createRes.ok || !createJson?.success) {
        return new Response(JSON.stringify({ error: "create_tunnel_failed", details: createJson }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }
      tunnelId = createJson.result.id;
    }

    // 2) Upsert DNS CNAME: hostname -> <tunnelId>.cfargotunnel.com
    const dnsTarget = `${tunnelId}.cfargotunnel.com`;

    const existingDnsRes = await fetch(
      `${apiBase}/zones/${env.CF_ZONE_ID}/dns_records?type=CNAME&name=${encodeURIComponent(hostname)}`,
      { method: "GET", headers }
    );
    const existingDnsJson = await existingDnsRes.json();

    const existingRecord =
      existingDnsRes.ok && existingDnsJson?.success && Array.isArray(existingDnsJson.result)
        ? existingDnsJson.result[0]
        : null;

    const dnsBody = JSON.stringify({
      type: "CNAME",
      name: hostname,
      content: dnsTarget,
      ttl: 60,
      proxied: true,
    });

    const dnsUrl = existingRecord?.id
      ? `${apiBase}/zones/${env.CF_ZONE_ID}/dns_records/${existingRecord.id}`
      : `${apiBase}/zones/${env.CF_ZONE_ID}/dns_records`;

    const dnsRes = await fetch(dnsUrl, {
      method: existingRecord?.id ? "PUT" : "POST",
      headers,
      body: dnsBody,
    });
    const dnsJson = await dnsRes.json();
    if (!dnsRes.ok || !dnsJson?.success) {
      return new Response(JSON.stringify({ error: "dns_failed", details: dnsJson }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 3) Get tunnel run token
    const tokenRes = await fetch(`${apiBase}/accounts/${env.CF_ACCOUNT_ID}/cfd_tunnel/${tunnelId}/token`, {
      method: "GET",
      headers,
    });
    const tokenJson = await tokenRes.json();
    if (!tokenRes.ok || !tokenJson?.success) {
      return new Response(JSON.stringify({ error: "token_failed", details: tokenJson }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ hostname, token: tokenJson.result }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  },
};
