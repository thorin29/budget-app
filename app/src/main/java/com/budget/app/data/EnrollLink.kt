package com.budget.app.data

import java.net.URI
import java.net.URLDecoder

/**
 * What the web's QR codes carry: Add a phone (`budget://enroll`, an existing
 * account) or the second person's invite (`budget://join`, a new account).
 */
data class EnrollLink(val server: String, val code: String, val join: Boolean = false)

object EnrollLinks {
    /**
     * budget://enroll?server=https%3A%2F%2Fbudget.example.com&code=ABCD-EFGH
     * budget://join?server=https%3A%2F%2Fbudget.example.com&code=<invite code>
     * Anything malformed, or a server address that doesn't normalise, is null.
     */
    fun parse(raw: String?): EnrollLink? {
        if (raw.isNullOrBlank()) return null
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase()
        if (uri.scheme?.lowercase() != "budget" || (host != "enroll" && host != "join")) return null
        val params = (uri.rawQuery ?: "").split("&").mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else pair.substring(0, i) to decode(pair.substring(i + 1))
        }.toMap()
        val server = params["server"]?.let { ServerUrl.normalize(it) } ?: return null
        if (host == "join") {
            // Invite codes are case-sensitive; kept exactly as sent.
            val invite = params["code"]?.trim()?.takeIf { INVITE.matches(it) } ?: return null
            return EnrollLink(server, invite, join = true)
        }
        val code = params["code"]?.trim()?.takeIf { CODE.matches(it.uppercase()) }?.uppercase() ?: return null
        return EnrollLink(server, code)
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        ""
    }

    private val CODE = Regex("^[A-Z0-9]{4}-?[A-Z0-9]{4}$")
    private val INVITE = Regex("^[A-Za-z0-9_-]{8,64}$")
}
