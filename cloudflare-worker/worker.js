// Cloudflare Worker: provisions a per-device Cloudflare Tunnel + DNS + run token.
// Also serves the PC connect script at /c
//
// Required env vars:
// - CF_API_TOKEN: API token with permissions to manage Tunnels + DNS for the zone
// - CF_ACCOUNT_ID: Cloudflare account id
// - CF_ZONE_ID: Zone id for your base domain
// - CF_DNS_SUFFIX: e.g. "676967.xyz" (deviceId will be prepended)
//
// Endpoints:
// POST /provision - provision a tunnel
// GET /c - bash connect script
// GET /connect.bat - windows connect script

const BASH_SCRIPT = `#!/bin/bash
# Remote ADB Connect - curl -sL 676967.xyz/c | bash -s DEVICE_ID
set -e
DEVICE="\${1:-}"; PORT="\${2:-5555}"; HOST="\$DEVICE.676967.xyz"
[ -z "\$DEVICE" ] && echo "Usage: curl -sL 676967.xyz/c | bash -s DEVICE_ID" && exit 1
echo "Connecting to \$HOST..."
if ! command -v cloudflared &>/dev/null; then
  echo "Installing cloudflared..."
  OS=\$(uname -s); ARCH=\$(uname -m)
  case "\$OS-\$ARCH" in
    Linux-x86_64) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64";;
    Linux-aarch64|Linux-arm64) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64";;
    Darwin-x86_64) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-amd64.tgz";;
    Darwin-arm64) URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-arm64.tgz";;
    *) echo "Unsupported: \$OS-\$ARCH"; exit 1;;
  esac
  if [[ "\$URL" == *.tgz ]]; then
    curl -sL "\$URL" | sudo tar xz -C /usr/local/bin
  else
    sudo curl -sL "\$URL" -o /usr/local/bin/cloudflared && sudo chmod +x /usr/local/bin/cloudflared
  fi
fi
echo "Run in another terminal: adb connect localhost:\$PORT"
echo "Press Ctrl+C to disconnect"
cloudflared access tcp --hostname "\$HOST" --url "localhost:\$PORT"
`;

const BAT_SCRIPT = `@echo off
set D=%1
set P=%2
if "%P%"=="" set P=5555
if "%D%"=="" (echo Usage: connect.bat DEVICE_ID & exit /b 1)
set H=%D%.676967.xyz
echo Connecting to %H%...
where cloudflared >nul 2>nul || (
  echo Downloading cloudflared...
  curl -sLo cloudflared.exe https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
)
echo Run in another terminal: adb connect localhost:%P%
cloudflared access tcp --hostname %H% --url localhost:%P%
`;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    // Serve connect scripts
    if (path === "/c" || path === "/connect.sh") {
      return new Response(BASH_SCRIPT, {
        headers: { "Content-Type": "text/plain; charset=utf-8" }
      });
    }
    if (path === "/connect.bat") {
      return new Response(BAT_SCRIPT, {
        headers: { "Content-Type": "text/plain; charset=utf-8" }
      });
    }

    // Provision endpoint
    if (path === "/provision") {
      if (request.method !== "POST") {
        return new Response("Method Not Allowed", { status: 405 });
      }
      return handleProvision(request, env);
    }

    // Root - simple instructions
    if (path === "/" || path === "") {
      return new Response(`Remote ADB Connect

Linux/macOS:
  curl -sL 676967.xyz/c | bash -s YOUR_DEVICE_ID

Windows:
  curl -sLO 676967.xyz/connect.bat && connect.bat YOUR_DEVICE_ID

Then in another terminal:
  adb connect localhost:5555

Get the app: https://github.com/Copi24/remoteadb/releases
`, { headers: { "Content-Type": "text/plain" }});
    }

    return new Response("Not Found", { status: 404 });
  },
};

async function handleProvision(request, env) {

    let body;
    try {
      body = await request.json();
    } catch {
      return new Response("Bad JSON", { status: 400 });
    }

    const rawId = String(body.deviceId || "");
    const deviceId = rawId.toLowerCase().replace(/[^a-z0-9]/g, "").slice(0, 32);
    if (!deviceId) return new Response("Missing deviceId", { status: 400 });
    
    const adbPort = parseInt(body.adbPort) || 5555;

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

    // 3) Configure tunnel ingress for TCP on port 5555
    const configRes = await fetch(`${apiBase}/accounts/${env.CF_ACCOUNT_ID}/cfd_tunnel/${tunnelId}/configurations`, {
      method: "PUT",
      headers,
      body: JSON.stringify({
        config: {
          ingress: [
            {
              hostname: hostname,
              service: `tcp://localhost:${adbPort}`
            },
            {
              service: "http_status:404"
            }
          ]
        }
      }),
    });
    const configJson = await configRes.json();
    if (!configRes.ok || !configJson?.success) {
      return new Response(JSON.stringify({ error: "config_failed", details: configJson }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 4) Get tunnel run token
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
}
