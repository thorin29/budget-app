package com.budget.app.data

import java.net.URI

/**
 * The server address as typed: "budget.example.com" → "https://budget.example.com".
 * Only the scheme, host and port are kept; a path is dropped. Plain http is
 * allowed (a home-network address), anything else is refused.
 */
object ServerUrl {
    fun normalize(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (!text.contains("://")) text = "https://$text"
        val uri = try {
            URI(text)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.userInfo != null) return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    fun host(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (_: Exception) {
        null
    }
}
