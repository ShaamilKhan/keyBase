package com.keybase.app.autofill

import com.keybase.app.data.Entry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillMatcherTest {
    private val chrome = "com.android.chrome"
    private val github = Entry(title = "GitHub", password = "pw", url = "https://github.com")
    private val app = Entry(title = "Netflix", password = "pw", appId = "com.netflix.mediaclient")

    private fun web(e: Entry, domain: String?, scheme: String? = "https", pkg: String? = chrome) =
        AutofillMatcher.matches(e, pkg, domain, scheme)

    // ---- websites --------------------------------------------------------------------------

    @Test fun exactHostMatchesInATrustedBrowser() {
        assertTrue(web(github, "github.com"))
        assertTrue(web(github, "www.github.com"))
        assertTrue(web(github.copy(url = "github.com"), "github.com"))
        assertTrue(web(github.copy(url = "https://GitHub.com/login"), "GITHUB.COM"))
    }

    @Test fun lookalikeAndRelatedHostsNeverMatch() {
        for (bad in listOf(
            "github.com.evil.com", "evilgithub.com", "githubb.com", "gith-ub.com", "github.co",
            "login.github.com", "gist.github.com", "github.com.", "xn--github-vb3a.com", "github.com@evil.com",
            "github.com:8080.evil.com", "evil.com/github.com", "evil.com?github.com", "",
        )) {
            assertFalse("'$bad' matched", web(github, bad))
        }
    }

    @Test fun aSavedAddressForADifferentSubdomainDoesNotMatchTheBareDomain() {
        assertFalse(web(github.copy(url = "https://login.github.com"), "github.com"))
        assertTrue(web(github.copy(url = "https://login.github.com"), "login.github.com"))
    }

    @Test fun plainHttpPagesAreNeverFilled() {
        assertFalse(web(github, "github.com", scheme = "http"))
        assertFalse(web(github, "github.com", scheme = null))
        assertFalse(web(github, "github.com", scheme = "file"))
        assertFalse(web(github, "github.com", scheme = "javascript"))
    }

    @Test fun unknownBrowsersAndOtherAppsAreNeverTrustedWithAWebAddress() {
        assertFalse(web(github, "github.com", pkg = "com.evil.browser"))
        assertFalse(web(github, "github.com", pkg = "com.android.chrome.evil"))
        assertFalse(web(github, "github.com", pkg = null))
        for (b in AutofillMatcher.TRUSTED_BROWSERS) assertTrue(b, web(github, "github.com", pkg = b))
    }

    @Test fun aLoginWithoutAWebsiteNeverMatchesAWebPage() {
        assertFalse(web(github.copy(url = ""), "github.com"))
        assertFalse(web(github.copy(url = "   "), "github.com"))
    }

    // ---- native apps -----------------------------------------------------------------------

    @Test fun exactPackageMatchesANativeApp() {
        assertTrue(AutofillMatcher.matches(app, "com.netflix.mediaclient", null, null))
    }

    @Test fun nearbyPackagesNeverMatch() {
        for (bad in listOf("com.netflix.mediaclient.evil", "com.netflix", "com.netflix.mediaclien", "COM.NETFLIX.MEDIACLIENT", "evil.com.netflix.mediaclient", "")) {
            assertFalse("'$bad' matched", AutofillMatcher.matches(app, bad, null, null))
        }
        assertFalse(AutofillMatcher.matches(app, null, null, null))
    }

    @Test fun aLoginWithNoPackageNeverMatchesAnApp() {
        assertFalse(AutofillMatcher.matches(github, "com.netflix.mediaclient", null, null))
        assertFalse(AutofillMatcher.matches(app.copy(appId = ""), "", null, null))
        assertFalse(AutofillMatcher.matches(app.copy(appId = "   "), "   ", null, null))
    }

    @Test fun aBrowserPackageCannotBeUsedAsAnAppMatch() {
        // Otherwise a login saved with the Chrome package would be offered on every page.
        val sneaky = Entry(title = "x", password = "pw", appId = chrome)
        assertFalse(AutofillMatcher.matches(sneaky, chrome, null, null))
    }

    @Test fun anAppLoginIsNotOfferedOnAWebPageEvenInTheSamePackage() {
        assertFalse(web(app, "netflix.com", pkg = "com.netflix.mediaclient"))
    }

    @Test fun entriesWithoutAPasswordAreNeverOffered() {
        assertFalse(web(github.copy(password = ""), "github.com"))
        assertFalse(AutofillMatcher.matches(app.copy(password = ""), "com.netflix.mediaclient", null, null))
    }
}
