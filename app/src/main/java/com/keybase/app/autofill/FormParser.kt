package com.keybase.app.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/** Finds the password field (and a username field next to it) in a screen's view tree. */
object FormParser {
    class Form(
        val pkg: String?,
        /** Domain of the frame that actually contains the password field, not of the whole page. */
        val webDomain: String?,
        val webScheme: String?,
        val userId: AutofillId?,
        val passId: AutofillId?,
    )

    private class State {
        var userId: AutofillId? = null
        var lastText: AutofillId? = null
        var passId: AutofillId? = null
        var domain: String? = null
        var scheme: String? = null
    }

    fun parse(s: AssistStructure): Form {
        val st = State()
        for (i in 0 until s.windowNodeCount) walk(s.getWindowNodeAt(i).rootViewNode, null, null, st)
        return Form(s.activityComponent?.packageName, st.domain, st.scheme, st.userId, st.passId)
    }

    private fun walk(n: AssistStructure.ViewNode, parentDomain: String?, parentScheme: String?, st: State) {
        if (n.visibility != View.VISIBLE) return
        // A password field inside an embedded frame belongs to that frame's site, not the page's.
        val domain = n.webDomain?.takeIf { it.isNotEmpty() } ?: parentDomain
        val scheme = n.webScheme?.takeIf { it.isNotEmpty() } ?: parentScheme
        val id = n.autofillId
        if (id != null && n.autofillType == View.AUTOFILL_TYPE_TEXT && st.passId == null) {
            val hints = n.autofillHints?.toList().orEmpty()
            val htmlType = n.htmlInfo?.attributes
                ?.firstOrNull { it.first.equals("type", ignoreCase = true) }?.second?.lowercase()
            val cls = n.inputType and InputType.TYPE_MASK_CLASS
            val variation = n.inputType and InputType.TYPE_MASK_VARIATION
            val isPass = View.AUTOFILL_HINT_PASSWORD in hints || htmlType == "password" ||
                (cls == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            val isUser = View.AUTOFILL_HINT_USERNAME in hints || View.AUTOFILL_HINT_EMAIL_ADDRESS in hints ||
                htmlType == "email"
            when {
                isPass -> {
                    st.passId = id
                    st.domain = domain
                    st.scheme = scheme
                    if (st.userId == null) st.userId = st.lastText
                }
                isUser -> if (st.userId == null) st.userId = id
                cls == InputType.TYPE_CLASS_TEXT -> st.lastText = id
            }
        }
        for (i in 0 until n.childCount) walk(n.getChildAt(i), domain, scheme, st)
    }
}
