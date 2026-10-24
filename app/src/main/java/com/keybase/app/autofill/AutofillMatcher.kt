package com.keybase.app.autofill

import com.keybase.app.data.Entry
import com.keybase.app.data.SiteRules

/**
 * Decides whether a login may be offered to a given screen. Deliberately strict: no subdomain or
 * suffix matching, https only, and websites only inside browsers we recognise.
 */
object AutofillMatcher {
    val TRUSTED_BROWSERS = setOf(
        "com.android.chrome", "org.mozilla.firefox", "org.mozilla.firefox_beta",
        "com.brave.browser", "com.microsoft.emmx", "com.duckduckgo.mobile.android",
        "org.chromium.chrome", "com.vivaldi.browser", "app.vanadium.browser",
    )

    /**
     * @param pkg package of the app showing the form
     * @param webDomain host the browser reports for the page, or null for a native app screen
     * @param webScheme scheme the browser reports for the page
     */
    fun matches(e: Entry, pkg: String?, webDomain: String?, webScheme: String?): Boolean {
        if (e.password.isEmpty()) return false
        val p = pkg ?: return false
        if (webDomain != null) {
            if (p !in TRUSTED_BROWSERS) return false
            if (webScheme != "https") return false
            val page = SiteRules.hostOf(webDomain) ?: return false
            val want = SiteRules.hostOf(e.url) ?: return false
            return page == want
        }
        val app = e.appId.trim()
        return app.isNotEmpty() && p == app && p !in TRUSTED_BROWSERS
    }
}
