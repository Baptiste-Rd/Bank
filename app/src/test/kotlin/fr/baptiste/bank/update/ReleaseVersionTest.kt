package fr.baptiste.bank.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun `a ci tag gives its build number`() {
        assertEquals(42, versionCodeFromTag("build-42"))
    }

    @Test
    fun `other tags are ignored`() {
        assertEquals(null, versionCodeFromTag("v1.0"))
        assertEquals(null, versionCodeFromTag("build-"))
        assertEquals(null, versionCodeFromTag("build-abc"))
        assertEquals(null, versionCodeFromTag("build-0"))
    }
}
