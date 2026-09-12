package com.cointerpreter.app.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanguageTest {

    @Test
    fun `Arabic and Urdu and Persian are marked RTL, others are not`() {
        assertThat(Language.ARABIC.isRtl).isTrue()
        assertThat(Language.byCode("ur").isRtl).isTrue()
        assertThat(Language.byCode("fa").isRtl).isTrue()
        assertThat(Language.ENGLISH.isRtl).isFalse()
        assertThat(Language.FRENCH.isRtl).isFalse()
    }

    @Test
    fun `first-class languages are Arabic, English, French in that order`() {
        assertThat(Language.FIRST_CLASS).containsExactly(Language.ARABIC, Language.ENGLISH, Language.FRENCH).inOrder()
    }

    @Test
    fun `byCode falls back to English for unknown codes rather than crashing`() {
        assertThat(Language.byCode("xx-unknown")).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun `extended catalog includes first-class languages`() {
        assertThat(Language.EXTENDED).containsAtLeastElementsIn(Language.FIRST_CLASS)
    }
}
