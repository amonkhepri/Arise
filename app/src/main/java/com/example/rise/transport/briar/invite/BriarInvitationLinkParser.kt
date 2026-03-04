package com.example.rise.transport.briar.invite

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class BriarInvitationLinkParser {

    fun parse(rawLink: String): BriarInvitationLinkParseResult {
        val candidate = rawLink.trim()
        if (candidate.isEmpty()) {
            return BriarInvitationLinkParseResult.Invalid(
                BriarInvitationLinkParseResult.InvalidReason.EMPTY_INPUT,
            )
        }

        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return BriarInvitationLinkParseResult.Invalid(
                BriarInvitationLinkParseResult.InvalidReason.UNSUPPORTED_LINK,
            )

        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            BRIAR_SCHEME -> parseRawBriar(candidate)
            ARISE_SCHEME -> parseWrappedArise(uri)
            HTTPS_SCHEME -> parseWrappedHttps(uri)
            else -> BriarInvitationLinkParseResult.Invalid(
                BriarInvitationLinkParseResult.InvalidReason.UNSUPPORTED_LINK,
            )
        }
    }

    private fun parseRawBriar(link: String): BriarInvitationLinkParseResult {
        val normalized = normalizeBriarLink(link)
            ?: return BriarInvitationLinkParseResult.Invalid(
                BriarInvitationLinkParseResult.InvalidReason.INVALID_BRIAR_LINK,
            )

        return BriarInvitationLinkParseResult.Success(
            invitation = BriarInvitationLink(
                briarLink = normalized,
                alias = null,
                duplicateKey = "link:$normalized",
            )
        )
    }

    private fun parseWrappedArise(uri: URI): BriarInvitationLinkParseResult {
        val host = uri.host?.lowercase(Locale.ROOT)
        val path = normalizePath(uri.path)
        if (host != ARISE_BRIAR_HOST || path != ARISE_INVITE_PATH) {
            return BriarInvitationLinkParseResult.Invalid(
                BriarInvitationLinkParseResult.InvalidReason.UNSUPPORTED_LINK,
            )
        }

        return parseWrappedInvite(uri)
    }

    private fun parseWrappedHttps(uri: URI): BriarInvitationLinkParseResult {
        val host = uri.host?.lowercase(Locale.ROOT)
        val path = normalizePath(uri.path)
        if (host !in SUPPORTED_HTTPS_HOSTS || path != HTTPS_INVITE_PATH) {
            return BriarInvitationLinkParseResult.Invalid(
                BriarInvitationLinkParseResult.InvalidReason.UNSUPPORTED_LINK,
            )
        }

        return parseWrappedInvite(uri)
    }

    private fun parseWrappedInvite(uri: URI): BriarInvitationLinkParseResult {
        val params = decodeQueryParams(uri.rawQuery)
        val wrappedLink = params[PARAM_LINK]?.trim().orEmpty()
        if (wrappedLink.isEmpty()) {
            return BriarInvitationLinkParseResult.Invalid(
                BriarInvitationLinkParseResult.InvalidReason.MISSING_REQUIRED_BRIAR_LINK,
            )
        }

        val normalizedBriarLink = normalizeBriarLink(wrappedLink)
            ?: return BriarInvitationLinkParseResult.Invalid(
                BriarInvitationLinkParseResult.InvalidReason.INVALID_BRIAR_LINK,
            )
        val alias = params[PARAM_ALIAS]?.trim()?.takeIf { it.isNotEmpty() }
        val duplicateKey = params[PARAM_INVITE_ID]?.trim()?.takeIf { it.isNotEmpty() }
            ?: params[PARAM_INVITE_ID_LEGACY]?.trim()?.takeIf { it.isNotEmpty() }

        val resolvedDuplicateKey = if (duplicateKey != null) {
            "invite:${duplicateKey.lowercase(Locale.ROOT)}"
        } else {
            "link:$normalizedBriarLink"
        }

        return BriarInvitationLinkParseResult.Success(
            invitation = BriarInvitationLink(
                briarLink = normalizedBriarLink,
                alias = alias,
                duplicateKey = resolvedDuplicateKey,
            )
        )
    }

    private fun normalizeBriarLink(link: String): String? {
        val normalized = link.trim()
        if (!normalized.startsWith(BRIAR_PREFIX, ignoreCase = true)) return null

        val payload = normalized.substring(BRIAR_PREFIX.length).trim()
        if (payload.isEmpty()) return null

        return "$BRIAR_PREFIX$payload"
    }

    private fun decodeQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()

        val params = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { segment ->
            if (segment.isBlank()) return@forEach

            val separator = segment.indexOf('=')
            val rawKey = if (separator >= 0) segment.substring(0, separator) else segment
            val rawValue = if (separator >= 0) segment.substring(separator + 1) else ""
            val key = decode(rawKey)
            if (key.isBlank()) return@forEach
            params.putIfAbsent(key, decode(rawValue))
        }

        return params
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
        }.getOrDefault(value)
    }

    private fun normalizePath(path: String?): String {
        if (path.isNullOrBlank()) return "/"
        return if (path.startsWith('/')) path else "/$path"
    }

    companion object {
        private const val BRIAR_SCHEME = "briar"
        private const val ARISE_SCHEME = "arise"
        private const val HTTPS_SCHEME = "https"
        private const val BRIAR_PREFIX = "briar://"

        private const val ARISE_BRIAR_HOST = "briar"
        private const val ARISE_INVITE_PATH = "/invite"
        private const val HTTPS_INVITE_PATH = "/briar/invite"
        private val SUPPORTED_HTTPS_HOSTS = setOf("arise.app", "www.arise.app")

        private const val PARAM_LINK = "link"
        private const val PARAM_ALIAS = "alias"
        private const val PARAM_INVITE_ID = "inviteId"
        private const val PARAM_INVITE_ID_LEGACY = "invite_id"
    }
}

data class BriarInvitationLink(
    val briarLink: String,
    val alias: String?,
    val duplicateKey: String,
)

sealed interface BriarInvitationLinkParseResult {
    data class Success(val invitation: BriarInvitationLink) : BriarInvitationLinkParseResult
    data class Invalid(val reason: InvalidReason) : BriarInvitationLinkParseResult

    enum class InvalidReason {
        EMPTY_INPUT,
        UNSUPPORTED_LINK,
        MISSING_REQUIRED_BRIAR_LINK,
        INVALID_BRIAR_LINK,
    }
}
