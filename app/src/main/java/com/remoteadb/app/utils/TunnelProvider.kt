package com.remoteadb.app.utils

enum class TunnelProvider(val displayName: String, val description: String) {
    CLOUDFLARE(
        "Cloudflare",
        "Uses Cloudflare Zero Trust tunnels (requires cloudflared on PC)"
    ),
    SERVEO(
        "Serveo",
        "SSH-based tunnel (only needs SSH on PC, simpler setup)"
    )
}
