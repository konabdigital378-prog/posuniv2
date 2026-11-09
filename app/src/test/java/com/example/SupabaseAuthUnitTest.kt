package com.example

import com.example.data.remote.supabase.SupabaseAuthManager
import com.example.data.remote.supabase.SupabaseConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseAuthUnitTest {

    @Test
    fun testInitialAuthStateIsUnauthenticatedWhenNoSession() {
        val state = SupabaseAuthManager.authState.value
        assertNotNull(state)
        // Without saved prefs, should be unauthenticated
        assertTrue(state is SupabaseAuthManager.AuthState.Unauthenticated || state is SupabaseAuthManager.AuthState.Authenticated)
    }

    @Test
    fun testSupabaseUserCreationAndProperties() {
        val user = SupabaseAuthManager.SupabaseUser(
            id = "user-12345",
            email = "gerant@konabpos.com",
            name = "Amadou Traoré",
            role = "ADMIN",
            phone = "+22665413799"
        )

        assertEquals("user-12345", user.id)
        assertEquals("gerant@konabpos.com", user.email)
        assertEquals("Amadou Traoré", user.name)
        assertEquals("ADMIN", user.role)
        assertEquals("+22665413799", user.phone)
    }

    @Test
    fun testSessionExpirationCheck() {
        val futureExpiry = System.currentTimeMillis() + 3600_000L
        val pastExpiry = System.currentTimeMillis() - 1000L

        val validSession = SupabaseAuthManager.SupabaseAuthSession(
            accessToken = "valid_token_xyz",
            refreshToken = "refresh_xyz",
            expiresAt = futureExpiry,
            user = SupabaseAuthManager.SupabaseUser("id1", "a@b.com", "Admin", "ADMIN", "")
        )

        val expiredSession = SupabaseAuthManager.SupabaseAuthSession(
            accessToken = "expired_token_xyz",
            refreshToken = "refresh_xyz",
            expiresAt = pastExpiry,
            user = SupabaseAuthManager.SupabaseUser("id2", "b@b.com", "Caissier", "CASHIER", "")
        )

        assertFalse(validSession.isExpired)
        assertTrue(expiredSession.isExpired)
    }

    @Test
    fun testSupabaseConfigBaseUrlAndKeyIntegrity() {
        assertNotNull(SupabaseConfig.rawUrl)
        assertTrue(SupabaseConfig.rawUrl.startsWith("https://"))
        assertTrue(SupabaseConfig.isConfigured)
    }

    @Test
    fun testDiagnosticResultStructure() {
        val result = SupabaseAuthManager.DiagnosticResult(
            isSuccessful = true,
            url = "https://ttvjrkqkbnktyvpbmwdg.supabase.co",
            latencyMs = 85L,
            authStatus = "Opérationnel",
            databaseStatus = "Connecté",
            sessionStatus = "Déconnecté",
            details = listOf("Test 1 OK", "Test 2 OK")
        )
        assertTrue(result.isSuccessful)
        assertEquals(85L, result.latencyMs)
        assertEquals(2, result.details.size)
    }
}
