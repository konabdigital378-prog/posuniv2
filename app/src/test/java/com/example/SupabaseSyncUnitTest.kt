package com.example

import com.example.data.remote.supabase.SupabaseApiClient
import com.example.data.remote.supabase.SupabaseConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseSyncUnitTest {

    @Test
    fun testSupabaseConfigurationIsValid() {
        assertTrue("Supabase doit être configuré", SupabaseConfig.isConfigured)
        assertEquals("https://ttvjrkqkbnktyvpbmwdg.supabase.co", SupabaseConfig.rawUrl)
        assertTrue("La clé anon doit être présente", SupabaseConfig.rawAnonKey.isNotBlank())
        assertEquals("https://ttvjrkqkbnktyvpbmwdg.supabase.co/rest/v1", SupabaseConfig.restBaseUrl)
    }

    @Test
    fun testSupabaseLiveConnection() = runBlocking {
        val client = SupabaseApiClient()
        val result = client.testConnection()
        assertTrue(
            "Le test de connexion Supabase doit réussir : ${result.exceptionOrNull()?.message}",
            result.isSuccess
        )
        val message = result.getOrNull()
        assertNotNull(message)
        assertTrue("Le message doit indiquer le succès de connexion", message!!.contains("réussie") || message.contains("connecté"))
    }

    @Test
    fun testSupabaseFetchShopProfile() = runBlocking {
        val client = SupabaseApiClient()
        val result = client.fetchShopProfile()
        assertTrue(
            "La récupération du profil boutique depuis Supabase doit réussir : ${result.exceptionOrNull()?.message}",
            result.isSuccess
        )
        val profile = result.getOrNull()
        assertNotNull("Le profil boutique ne doit pas être nul", profile)
        assertTrue("Le nom de la boutique ne doit pas être vide", profile!!.name.isNotBlank())
    }

    @Test
    fun testSupabaseSqlSchemaCompleteness() {
        val sql = SupabaseConfig.generateSqlSchema()
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.shop_profile"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.products"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.customers"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.sales"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.sale_items"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.cash_sessions"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.cash_movements"))
    }
}
