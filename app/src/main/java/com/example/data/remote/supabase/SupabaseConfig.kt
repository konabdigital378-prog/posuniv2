package com.example.data.remote.supabase

import android.content.Context
import com.example.BuildConfig

object SupabaseConfig {

    @Volatile
    private var customUrlOverride: String? = null

    @Volatile
    private var customKeyOverride: String? = null

    const val DEFAULT_SUPABASE_URL = "https://ttvjrkqkbnktyvpbmwdg.supabase.co"
    const val DEFAULT_SUPABASE_ANON_KEY = "sb_publishable_siNdueRXP4ge6gvopEB3AA_yw0gGReo"

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE)
            val u = prefs.getString("custom_supabase_url", null)
            val k = prefs.getString("custom_supabase_anon_key", null)
            if (!u.isNullOrBlank() && u.startsWith("http") && !u.contains("your-project") && !u.contains("alcqwqtmdvxbjjwvnpkv")) {
                customUrlOverride = u.trim()
            } else {
                customUrlOverride = null
                prefs.edit().remove("custom_supabase_url").apply()
            }
            if (!k.isNullOrBlank() && !k.contains("your-supabase") && k.length > 20) {
                customKeyOverride = k.trim()
            } else {
                customKeyOverride = null
                prefs.edit().remove("custom_supabase_anon_key").apply()
            }
        } catch (_: Exception) {}
    }

    fun saveCustomConfig(context: Context, url: String, anonKey: String) {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        try {
            val prefs = context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("custom_supabase_url", cleanUrl)
                .putString("custom_supabase_anon_key", cleanKey)
                .apply()
        } catch (_: Exception) {}
        customUrlOverride = cleanUrl
        customKeyOverride = cleanKey
    }

    fun clearCustomConfig(context: Context) {
        try {
            val prefs = context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("custom_supabase_url")
                .remove("custom_supabase_anon_key")
                .apply()
        } catch (_: Exception) {}
        customUrlOverride = null
        customKeyOverride = null
    }

    fun getMaskedAnonKey(): String {
        val key = rawAnonKey
        if (key.length <= 10) return key
        return key.take(6) + "..." + key.takeLast(4)
    }

    val rawUrl: String get() {
        val override = customUrlOverride
        if (!override.isNullOrBlank() && override.startsWith("http") && !override.contains("your-project") && !override.contains("your_project") && !override.contains("alcqwqtmdvxbjjwvnpkv")) {
            return override
        }
        val buildVal = try {
            BuildConfig.SUPABASE_URL.trim()
        } catch (e: Exception) {
            ""
        }
        if (buildVal.isNotBlank() && buildVal.startsWith("http") && !buildVal.contains("your-project") && !buildVal.contains("your_project") && !buildVal.contains("alcqwqtmdvxbjjwvnpkv")) {
            return buildVal
        }
        return DEFAULT_SUPABASE_URL
    }

    val rawAnonKey: String get() {
        val override = customKeyOverride
        if (!override.isNullOrBlank() && !override.contains("your-supabase") && !override.contains("your_supabase") && !override.contains("placeholder") && override.length > 20) {
            return override
        }
        val buildVal = try {
            BuildConfig.SUPABASE_ANON_KEY.trim()
        } catch (e: Exception) {
            ""
        }
        if (buildVal.isNotBlank() && !buildVal.contains("your-supabase") && !buildVal.contains("your_supabase") && !buildVal.contains("placeholder") && buildVal.length > 20) {
            return buildVal
        }
        return DEFAULT_SUPABASE_ANON_KEY
    }

    val isConfigured: Boolean
        get() = rawUrl.isNotBlank() && 
                rawAnonKey.isNotBlank() && 
                rawUrl.startsWith("http") && 
                !rawUrl.contains("your-project")

    val restBaseUrl: String
        get() {
            val base = rawUrl.trimEnd('/')
            return "$base/rest/v1"
        }

    /**
     * Generates standard PostgreSQL / Supabase SQL schema script
     * with tables, indexes, and Row Level Security (RLS) for Konab POS.
     */
    fun generateSqlSchema(): String {
        return """
-- ========================================================
-- SCHEMA SQL POUR KONAB POS SUR SUPABASE
-- A exécuter dans l'éditeur SQL de votre projet Supabase
-- (SQL Editor -> New Query -> Run)
-- ========================================================

-- 1. Table des Boutiques (Shop Profile)
CREATE TABLE IF NOT EXISTS public.shop_profile (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    business_type TEXT DEFAULT 'Boutique',
    phone TEXT,
    address TEXT,
    city TEXT,
    currency TEXT DEFAULT 'FCFA',
    slogan TEXT,
    receipt_footer TEXT,
    subscription_tier TEXT DEFAULT 'Premium',
    deployer_id TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Migration automatique si la table existe déjà sans deployer_id
ALTER TABLE public.shop_profile ADD COLUMN IF NOT EXISTS deployer_id TEXT;
CREATE INDEX IF NOT EXISTS idx_shop_profile_deployer_id ON public.shop_profile(deployer_id);

-- 2. Table des Produits & Stock
CREATE TABLE IF NOT EXISTS public.products (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT DEFAULT 'Général',
    sell_price BIGINT NOT NULL DEFAULT 0,
    buy_price BIGINT NOT NULL DEFAULT 0,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    min_stock_alert INTEGER NOT NULL DEFAULT 5,
    barcode TEXT,
    unit TEXT DEFAULT 'pièce',
    icon_name TEXT DEFAULT 'default',
    is_available BOOLEAN DEFAULT true,
    image_uri TEXT
);

CREATE INDEX IF NOT EXISTS idx_products_barcode ON public.products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_category ON public.products(category);

-- 3. Table des Clients & Dettes
CREATE TABLE IF NOT EXISTS public.customers (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    address TEXT,
    debt_amount BIGINT DEFAULT 0,
    total_spent BIGINT DEFAULT 0,
    total_sales_count INTEGER DEFAULT 0,
    status TEXT DEFAULT 'Nouveau',
    created_at BIGINT DEFAULT 0
);

-- 4. Table des Ventes (Transactions)
CREATE TABLE IF NOT EXISTS public.sales (
    id BIGINT PRIMARY KEY,
    transaction_number TEXT UNIQUE NOT NULL,
    timestamp BIGINT NOT NULL,
    customer_name TEXT DEFAULT 'Client comptant',
    subtotal BIGINT NOT NULL,
    discount_amount BIGINT DEFAULT 0,
    total_amount BIGINT NOT NULL,
    payment_method TEXT DEFAULT 'ESPECES',
    payment_status TEXT DEFAULT 'PAYE',
    cashier_name TEXT DEFAULT 'Admin',
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_sales_timestamp ON public.sales(timestamp);
CREATE INDEX IF NOT EXISTS idx_sales_transaction_number ON public.sales(transaction_number);

-- 5. Table des Articles Vendus (Détails des tickets)
CREATE TABLE IF NOT EXISTS public.sale_items (
    id BIGINT PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price BIGINT NOT NULL,
    total_price BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON public.sale_items(sale_id);

-- 6. Table des Fournisseurs (Suppliers)
CREATE TABLE IF NOT EXISTS public.suppliers (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    address TEXT,
    debt_amount BIGINT DEFAULT 0,
    total_purchased BIGINT DEFAULT 0,
    created_at BIGINT DEFAULT 0
);

-- 7. Table des Réapprovisionnements & Achats Fournisseurs (Purchases)
CREATE TABLE IF NOT EXISTS public.purchases (
    id BIGINT PRIMARY KEY,
    invoice_number TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    supplier_name TEXT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_cost BIGINT NOT NULL,
    total_cost BIGINT NOT NULL,
    is_paid_from_cash BOOLEAN DEFAULT true,
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON public.purchases(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchases_timestamp ON public.purchases(timestamp);

-- 8. Table des Sessions de Caisse (Clôtures & Écarts de Caisse)
CREATE TABLE IF NOT EXISTS public.cash_sessions (
    id BIGINT PRIMARY KEY,
    opened_at BIGINT NOT NULL,
    closed_at BIGINT,
    opening_balance BIGINT DEFAULT 0,
    closing_expected_balance BIGINT DEFAULT 0,
    closing_actual_balance BIGINT DEFAULT 0,
    difference BIGINT DEFAULT 0,
    status TEXT DEFAULT 'OPEN',
    opened_by TEXT DEFAULT 'Gérant',
    closed_by TEXT,
    closing_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_cash_sessions_opened_at ON public.cash_sessions(opened_at);

-- 9. Table des Mouvements de Caisse & Dépenses (Cash Movements)
CREATE TABLE IF NOT EXISTS public.cash_movements (
    id BIGINT PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    type TEXT NOT NULL,
    amount BIGINT NOT NULL,
    reason TEXT NOT NULL,
    payment_method TEXT DEFAULT 'Espèces',
    operator_name TEXT DEFAULT 'Gérant',
    related_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cash_movements_timestamp ON public.cash_movements(timestamp);
CREATE INDEX IF NOT EXISTS idx_cash_movements_type ON public.cash_movements(type);

-- 10. Table des Notifications & Alertes Magasin (App Notifications)
CREATE TABLE IF NOT EXISTS public.app_notifications (
    id BIGINT PRIMARY KEY,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    type TEXT NOT NULL,
    priority TEXT DEFAULT 'NORMAL',
    timestamp BIGINT NOT NULL,
    is_read BOOLEAN DEFAULT false,
    action_route TEXT,
    reference_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_app_notifications_timestamp ON public.app_notifications(timestamp);
CREATE INDEX IF NOT EXISTS idx_app_notifications_type ON public.app_notifications(type);

-- 11. Table des Catégories de Produits (Categories)
CREATE TABLE IF NOT EXISTS public.categories (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    icon TEXT DEFAULT 'category',
    color_hex TEXT DEFAULT '#059669',
    description TEXT,
    sort_order INTEGER DEFAULT 0
);

-- 12. Table des Agents Déployeurs (Agents)
CREATE TABLE IF NOT EXISTS public.agents (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    zone TEXT DEFAULT 'National',
    pin TEXT NOT NULL DEFAULT '1234',
    role TEXT DEFAULT 'Agent Déployeur',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 13. Table des Déploiements & Boutiques Créées par les Agents (Agent Deployments)
CREATE TABLE IF NOT EXISTS public.agent_deployments (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    agent_name TEXT NOT NULL,
    agent_phone TEXT,
    shop_name TEXT NOT NULL,
    shop_phone TEXT,
    shop_city TEXT,
    tier TEXT DEFAULT 'Professionnel',
    duration_days INTEGER DEFAULT 365,
    license_key TEXT,
    notes TEXT,
    timestamp BIGINT NOT NULL,
    is_synced BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_deployments_agent_id ON public.agent_deployments(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_deployments_timestamp ON public.agent_deployments(timestamp);

-- Activation de RLS (Row Level Security) avec accès lecture/écriture pour la clé anon
ALTER TABLE public.shop_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sale_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cash_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cash_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agent_deployments ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if they already exist to prevent "policy already exists" errors
DROP POLICY IF EXISTS "Allow anon read/write for shop_profile" ON public.shop_profile;
DROP POLICY IF EXISTS "Allow anon read/write for products" ON public.products;
DROP POLICY IF EXISTS "Allow anon read/write for customers" ON public.customers;
DROP POLICY IF EXISTS "Allow anon read/write for sales" ON public.sales;
DROP POLICY IF EXISTS "Allow anon read/write for sale_items" ON public.sale_items;
DROP POLICY IF EXISTS "Allow anon read/write for suppliers" ON public.suppliers;
DROP POLICY IF EXISTS "Allow anon read/write for purchases" ON public.purchases;
DROP POLICY IF EXISTS "Allow anon read/write for cash_sessions" ON public.cash_sessions;
DROP POLICY IF EXISTS "Allow anon read/write for cash_movements" ON public.cash_movements;
DROP POLICY IF EXISTS "Allow anon read/write for app_notifications" ON public.app_notifications;
DROP POLICY IF EXISTS "Allow anon read/write for categories" ON public.categories;
DROP POLICY IF EXISTS "Allow anon read/write for agents" ON public.agents;
DROP POLICY IF EXISTS "Allow anon read/write for agent_deployments" ON public.agent_deployments;

CREATE POLICY "Allow anon read/write for shop_profile" ON public.shop_profile FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for products" ON public.products FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for customers" ON public.customers FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for sales" ON public.sales FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for sale_items" ON public.sale_items FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for suppliers" ON public.suppliers FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for purchases" ON public.purchases FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for cash_sessions" ON public.cash_sessions FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for cash_movements" ON public.cash_movements FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for app_notifications" ON public.app_notifications FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for categories" ON public.categories FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for agents" ON public.agents FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for agent_deployments" ON public.agent_deployments FOR ALL USING (true) WITH CHECK (true);
        """.trimIndent()
    }
}
