package com.sanji.bomberpro.services

import com.sanji.bomberpro.data.OtpExtracted
import java.security.MessageDigest

object OtpExtractor {

    private val RE_DIGITS = Regex("(?<![\\d])(\\d{4,8})(?![\\d])")
    private val RE_ALNUM = Regex("(?<![A-Z0-9])([A-Z0-9]{4,8}(?:-[A-Z0-9]{2,4})?)(?![A-Z0-9])")
    private val RE_SPACED = Regex("(?<!\\d)(\\d(?:[ \\-]\\d){3,7})(?!\\d)")

    private val KEYWORDS = listOf(
        "otp", "code", "verify", "verification", "pin",
        "login", "secure", "password", "passcode", "auth",
        "confirm", "token", "access", "signin", "sign-in",
        "2fa", "mfa", "one-time", "one time", "activation",
        "reset", "unlock", "validation",
    )

    private val BLOCKLIST = setOf(
        "2019", "2020", "2021", "2022", "2023", "2024", "2025", "2026",
        "1234", "0000", "1111", "2222", "3333", "4444", "5555", "6666",
        "7777", "8888", "9999", "1000", "2000", "3000",
    )

    private const val MAX_BODY = 4000

    fun extractOtp(text: String?): List<OtpExtracted> {
        if (text.isNullOrBlank()) return emptyList()
        val raw = text.take(MAX_BODY)
        val lower = raw.lowercase()
        val hasKw = KEYWORDS.any { lower.contains(it) }

        val found = LinkedHashMap<String, OtpExtracted>()

        fun add(code: String, score: Int, snippet: String) {
            val key = code.uppercase().replace(" ", "").replace("-", "")
            if (key in BLOCKLIST) return
            val prev = found[key]
            if (prev == null || score > prev.score) {
                found[key] = OtpExtracted(code, score, snippet)
            }
        }

        for (m in RE_DIGITS.findAll(raw)) {
            val code = m.groupValues[1]
            if (code in BLOCKLIST) continue
            if (code.toSet().size == 1) continue
            var score = 0
            if (hasKw) score += 4
            score += when (code.length) {
                6 -> 3
                4 -> 2
                5, 8 -> 1
                else -> 0
            }
            val ctxStart = (m.range.first - 35).coerceAtLeast(0)
            val ctx = lower.substring(ctxStart, m.range.first)
            if (KEYWORDS.any { ctx.contains(it) }) score += 3

            val snipStart = (m.range.first - 20).coerceAtLeast(0)
            val snipEnd = (m.range.last + 20).coerceAtMost(raw.length - 1)
            val snippet = raw.substring(snipStart, snipEnd + 1).replace("\n", " ").trim()
            add(code, score, snippet)
        }

        for (m in RE_ALNUM.findAll(raw)) {
            val code = m.groupValues[1]
            if (code.all { it.isDigit() }) continue
            if (code.length < 4 || code.length > 12) continue
            val hasDigit = code.any { it.isDigit() }
            val hasLetter = code.any { it.isLetter() }
            if (!(hasDigit && hasLetter)) continue

            var score = if (hasKw) 5 else 3
            val ctxStart = (m.range.first - 35).coerceAtLeast(0)
            val ctx = lower.substring(ctxStart, m.range.first)
            if (KEYWORDS.any { ctx.contains(it) }) score += 3

            val snipStart = (m.range.first - 20).coerceAtLeast(0)
            val snipEnd = (m.range.last + 20).coerceAtMost(raw.length - 1)
            val snippet = raw.substring(snipStart, snipEnd + 1).replace("\n", " ").trim()
            add(code, score, snippet)
        }

        for (m in RE_SPACED.findAll(raw)) {
            val code = m.groupValues[1]
            val digits = code.replace(" ", "").replace("-", "")
            if (!digits.all { it.isDigit() }) continue
            if (digits.length < 4 || digits.length > 8) continue
            if (digits.toSet().size == 1) continue

            val score = if (hasKw) 3 else 1
            val snipStart = (m.range.first - 20).coerceAtLeast(0)
            val snipEnd = (m.range.last + 20).coerceAtMost(raw.length - 1)
            val snippet = raw.substring(snipStart, snipEnd + 1).replace("\n", " ").trim()
            add(digits, score, snippet)
        }

        return found.values.sortedByDescending { it.score }
    }

    fun bodyHash(body: String?, sender: String?, ts: Long): String {
        val normBody = (body ?: "").trim()
            .split(Regex("\\s+"))
            .joinToString(" ")
            .lowercase()
            .take(500)
        val normSender = (sender ?: "").trim()
            .split(Regex("\\s+"))
            .joinToString(" ")
            .lowercase()
            .take(100)
        val bucket = (ts.coerceAtLeast(0)) / 60
        val blob = "$normBody|$normSender|$bucket"
        return sha256(blob).take(32)
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}