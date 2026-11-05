package com.example

import com.example.data.remote.supabase.SupabaseConfig
import org.junit.Assert.*
import org.junit.Test

class SupabaseConfigUnitTest {

    @Test
    fun testSqlSchemaGenerationContainsAllTables() {
        val schema = SupabaseConfig.generateSqlSchema()
        assertNotNull(schema)
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.shop_profile"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.products"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.customers"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.sales"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.sale_items"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.suppliers"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.purchases"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.cash_sessions"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.cash_movements"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.app_notifications"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS public.categories"))
    }

    @Test
    fun testSqlSchemaContainsIndexesAndRlsPolicies() {
        val schema = SupabaseConfig.generateSqlSchema()
        assertTrue(schema.contains("ROW LEVEL SECURITY"))
        assertTrue(schema.contains("idx_products_barcode"))
        assertTrue(schema.contains("idx_sales_timestamp"))
        assertTrue(schema.contains("idx_sale_items_sale_id"))
        assertTrue(schema.contains("Allow anon read/write for products"))
    }

    @Test
    fun testRestBaseUrlConstruction() {
        val url = SupabaseConfig.restBaseUrl
        assertTrue(url.endsWith("/rest/v1"))
    }
}
