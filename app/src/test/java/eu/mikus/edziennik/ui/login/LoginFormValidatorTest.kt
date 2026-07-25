/*
 * Copyright (c) Mikolaj Olszewski 2026-7-25.
 */

package eu.mikus.edziennik.ui.login

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.mikus.edziennik.R
import eu.mikus.edziennik.ui.login.LoginFormValidator.validate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LoginFormValidatorTest {

    private fun field(
        validationRegex: String = ".*",
        isRequired: Boolean = true,
        hideText: Boolean = false,
        caseMode: LoginInfo.FormField.CaseMode = LoginInfo.FormField.CaseMode.UNCHANGED,
        stripTextRegex: String? = null,
    ) = LoginInfo.FormField(
        keyName = "k",
        name = R.string.login_hint_email,
        icon = CommunityMaterial.Icon.cmd_at,
        emptyText = R.string.login_error_no_email,
        invalidText = R.string.login_error_incorrect_email,
        errorCodes = emptyMap(),
        isRequired = isRequired,
        validationRegex = validationRegex,
        caseMode = caseMode,
        hideText = hideText,
        stripTextRegex = stripTextRegex,
    )

    @Test fun `trims when not hideText`() {
        val r = validate(field(), "  abc  ")
        assertEquals("abc", r.cleaned)
        assertNull(r.errorRes)
    }

    @Test fun `does not trim when hideText (password verbatim)`() {
        val r = validate(field(hideText = true), "  abc  ")
        assertEquals("  abc  ", r.cleaned)
        assertNull(r.errorRes)
    }

    @Test fun `lower-cases`() {
        assertEquals("abc", validate(field(caseMode = LoginInfo.FormField.CaseMode.LOWER_CASE), "ABC").cleaned)
    }

    @Test fun `upper-cases`() {
        assertEquals("ABC", validate(field(caseMode = LoginInfo.FormField.CaseMode.UPPER_CASE), "abc").cleaned)
    }

    @Test fun `strips matching chars`() {
        assertEquals("123", validate(field(stripTextRegex = "[^0-9]"), "1-2 3").cleaned)
    }

    @Test fun `required + blank after cleaning is emptyText, cleaned is blank`() {
        val r = validate(field(isRequired = true), "   ")
        assertEquals("", r.cleaned)
        assertEquals(R.string.login_error_no_email, r.errorRes)
    }

    @Test fun `regex mismatch is invalidText, cleaned still normalized`() {
        val emailRegex = "([\\w.\\-_+]+)?\\w+@[\\w-_]+(\\.\\w+)+"
        val r = validate(field(validationRegex = emailRegex, caseMode = LoginInfo.FormField.CaseMode.LOWER_CASE), "  Foo@X ")
        assertEquals("foo@x", r.cleaned)
        assertEquals(R.string.login_error_incorrect_email, r.errorRes)
    }

    @Test fun `valid email is normalized and error-free`() {
        val emailRegex = "([\\w.\\-_+]+)?\\w+@[\\w-_]+(\\.\\w+)+"
        val r = validate(field(validationRegex = emailRegex, caseMode = LoginInfo.FormField.CaseMode.LOWER_CASE), "  Foo@Bar.Com ")
        assertEquals("foo@bar.com", r.cleaned)
        assertNull(r.errorRes)
    }

    @Test fun `password any-value passes verbatim`() {
        val r = validate(field(validationRegex = ".*", hideText = true), " s3cret ")
        assertEquals(" s3cret ", r.cleaned)
        assertNull(r.errorRes)
    }

    @Test fun `not required + blank passes when regex accepts empty`() {
        val r = validate(field(isRequired = false, validationRegex = ".*"), "   ")
        assertEquals("", r.cleaned)
        assertNull(r.errorRes)
    }
}
