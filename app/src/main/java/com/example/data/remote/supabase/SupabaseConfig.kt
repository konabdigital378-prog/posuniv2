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

-- 1. Table des Boutiques (Shop Profile + Identifiants Gérant Multiboutique)
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
    logo_uri TEXT,
    admin_name TEXT DEFAULT 'Gérant',
    admin_phone TEXT,
    admin_pin TEXT DEFAULT '1234',
    cashier_pin TEXT DEFAULT '0000',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Migration automatique si la table existe déjà
ALTER TABLE public.shop_profile ADD COLUMN IF NOT EXISTS deployer_id TEXT;
ALTER TABLE public.shop_profile ADD COLUMN IF NOT EXISTS logo_uri TEXT;
ALTER TABLE public.shop_profile ADD COLUMN IF NOT EXISTS admin_name TEXT DEFAULT 'Gérant';
ALTER TABLE public.shop_profile ADD COLUMN IF NOT EXISTS admin_phone TEXT;
ALTER TABLE public.shop_profile ADD COLUMN IF NOT EXISTS admin_pin TEXT DEFAULT '1234';
ALTER TABLE public.shop_profile ADD COLUMN IF NOT EXISTS cashier_pin TEXT DEFAULT '0000';
CREATE INDEX IF NOT EXISTS idx_shop_profile_deployer_id ON public.shop_profile(deployer_id);
CREATE INDEX IF NOT EXISTS idx_shop_profile_admin_phone ON public.shop_profile(admin_phone);

-- Nettoyage automatique d'éventuels anciens doublons de boutiques avant l'index unique
DELETE FROM public.shop_profile
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY LOWER(BTRIM(name))
                   ORDER BY updated_at DESC, id ASC
               ) AS rn
        FROM public.shop_profile
    ) dup
    WHERE dup.rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_shop_profile_unique_name ON public.shop_profile (LOWER(BTRIM(name)));

-- 2. Table des Produits & Stock (Isolée par shop_id pour le Multiboutique)
CREATE TABLE IF NOT EXISTS public.products (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
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
    image_uri TEXT,
    gallery_images_json TEXT DEFAULT '[]'
);

ALTER TABLE public.products ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS gallery_images_json TEXT DEFAULT '[]';
CREATE INDEX IF NOT EXISTS idx_products_shop_id ON public.products(shop_id);
CREATE INDEX IF NOT EXISTS idx_products_barcode ON public.products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_category ON public.products(category);

-- 3. Table des Clients & Dettes
CREATE TABLE IF NOT EXISTS public.customers (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    name TEXT NOT NULL,
    phone TEXT,
    address TEXT,
    debt_amount BIGINT DEFAULT 0,
    total_spent BIGINT DEFAULT 0,
    total_sales_count INTEGER DEFAULT 0,
    status TEXT DEFAULT 'Nouveau',
    created_at BIGINT DEFAULT 0
);

ALTER TABLE public.customers ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_customers_shop_id ON public.customers(shop_id);

-- 4. Table des Ventes (Transactions multiboutique)
CREATE TABLE IF NOT EXISTS public.sales (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    transaction_number TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    customer_id BIGINT,
    customer_name TEXT DEFAULT 'Client comptant',
    subtotal BIGINT NOT NULL,
    discount_amount BIGINT DEFAULT 0,
    total_amount BIGINT NOT NULL,
    total_cost_price BIGINT DEFAULT 0,
    payment_method TEXT DEFAULT 'ESPECES',
    payment_status TEXT DEFAULT 'PAYE',
    amount_paid BIGINT DEFAULT 0,
    cashier_name TEXT DEFAULT 'Admin',
    notes TEXT,
    is_cancelled BOOLEAN DEFAULT false,
    cancelled_at BIGINT,
    cancellation_reason TEXT,
    cancelled_by TEXT
);

ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS total_cost_price BIGINT DEFAULT 0;
ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS amount_paid BIGINT DEFAULT 0;
ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS is_cancelled BOOLEAN DEFAULT false;
ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS cancelled_at BIGINT;
ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;
ALTER TABLE public.sales ADD COLUMN IF NOT EXISTS cancelled_by TEXT;
ALTER TABLE public.sales DROP CONSTRAINT IF EXISTS sales_transaction_number_key;
CREATE INDEX IF NOT EXISTS idx_sales_shop_id ON public.sales(shop_id);
CREATE INDEX IF NOT EXISTS idx_sales_timestamp ON public.sales(timestamp);
CREATE INDEX IF NOT EXISTS idx_sales_transaction_number ON public.sales(transaction_number);

-- Nettoyage automatique d'éventuels anciens doublons de tickets par boutique avant l'index unique
DELETE FROM public.sales
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY shop_id, transaction_number
                   ORDER BY timestamp DESC, id ASC
               ) AS rn
        FROM public.sales
    ) dup
    WHERE dup.rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sales_shop_transaction_number ON public.sales(shop_id, transaction_number);

-- 5. Table des Articles Vendus (Détails des tickets)
CREATE TABLE IF NOT EXISTS public.sale_items (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price BIGINT NOT NULL,
    unit_buy_price BIGINT NOT NULL DEFAULT 0,
    total_price BIGINT NOT NULL
);

ALTER TABLE public.sale_items ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.sale_items ADD COLUMN IF NOT EXISTS unit_buy_price BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_sale_items_shop_id ON public.sale_items(shop_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON public.sale_items(sale_id);

-- 6. Table des Fournisseurs (Suppliers)
CREATE TABLE IF NOT EXISTS public.suppliers (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    name TEXT NOT NULL,
    phone TEXT,
    address TEXT,
    debt_amount BIGINT DEFAULT 0,
    total_purchased BIGINT DEFAULT 0,
    created_at BIGINT DEFAULT 0
);

ALTER TABLE public.suppliers ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_suppliers_shop_id ON public.suppliers(shop_id);

-- 7. Table des Réapprovisionnements & Achats Fournisseurs (Purchases)
CREATE TABLE IF NOT EXISTS public.purchases (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
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

ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_purchases_shop_id ON public.purchases(shop_id);
CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON public.purchases(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchases_timestamp ON public.purchases(timestamp);

-- 8. Table des Sessions de Caisse (Clôtures & Écarts de Caisse)
CREATE TABLE IF NOT EXISTS public.cash_sessions (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
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

ALTER TABLE public.cash_sessions ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_cash_sessions_shop_id ON public.cash_sessions(shop_id);
CREATE INDEX IF NOT EXISTS idx_cash_sessions_opened_at ON public.cash_sessions(opened_at);

-- 9. Table des Mouvements de Caisse & Dépenses (Cash Movements)
CREATE TABLE IF NOT EXISTS public.cash_movements (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    timestamp BIGINT NOT NULL,
    type TEXT NOT NULL,
    amount BIGINT NOT NULL,
    reason TEXT NOT NULL,
    payment_method TEXT DEFAULT 'Espèces',
    operator_name TEXT DEFAULT 'Gérant',
    related_id BIGINT
);

ALTER TABLE public.cash_movements ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_cash_movements_shop_id ON public.cash_movements(shop_id);
CREATE INDEX IF NOT EXISTS idx_cash_movements_timestamp ON public.cash_movements(timestamp);
CREATE INDEX IF NOT EXISTS idx_cash_movements_type ON public.cash_movements(type);

-- 10. Table des Notifications & Alertes Magasin (App Notifications)
CREATE TABLE IF NOT EXISTS public.app_notifications (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    type TEXT NOT NULL,
    priority TEXT DEFAULT 'NORMAL',
    timestamp BIGINT NOT NULL,
    is_read BOOLEAN DEFAULT false,
    action_route TEXT,
    reference_id BIGINT
);

ALTER TABLE public.app_notifications ADD COLUMN IF NOT EXISTS shop_id BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_app_notifications_shop_id ON public.app_notifications(shop_id);
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

-- Nettoyage automatique des anciens déploiements en double avant l'index unique
DELETE FROM public.agent_deployments
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY LOWER(BTRIM(shop_name))
                   ORDER BY timestamp DESC, id ASC
               ) AS rn
        FROM public.agent_deployments
    ) dup
    WHERE dup.rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_deployments_unique_shop_name ON public.agent_deployments (LOWER(BTRIM(shop_name)));

-- 14. Table des Publicités Vidéo & Vitrine des Boutiques (Shop Ads)
CREATE TABLE IF NOT EXISTS public.shop_ads (
    id TEXT PRIMARY KEY,
    shop_id BIGINT DEFAULT 0,
    shop_name TEXT NOT NULL,
    business_type TEXT DEFAULT 'Boutique',
    city TEXT DEFAULT '',
    phone TEXT DEFAULT '',
    whatsapp_phone TEXT DEFAULT '',
    logo_uri TEXT DEFAULT '',
    ad_title TEXT NOT NULL,
    ad_description TEXT DEFAULT '',
    video_uri TEXT DEFAULT '',
    video_duration_sec INTEGER DEFAULT 0,
    cta_label TEXT DEFAULT 'Contacter',
    is_active BOOLEAN DEFAULT true,
    deployer_id TEXT DEFAULT '',
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_shop_ads_updated_at ON public.shop_ads(updated_at);
CREATE INDEX IF NOT EXISTS idx_shop_ads_is_active ON public.shop_ads(is_active);

-- 15. Table des Comptes Équipe Restaurant (Gérant, Cuisine, Serveur, Caissier)
CREATE TABLE IF NOT EXISTS public.restaurant_staff (
    id TEXT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    name TEXT NOT NULL,
    phone TEXT DEFAULT '',
    role TEXT NOT NULL DEFAULT 'MANAGER',
    pin TEXT NOT NULL DEFAULT '1234',
    is_active BOOLEAN DEFAULT true,
    updated_at BIGINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_restaurant_staff_shop_id ON public.restaurant_staff(shop_id);

-- 16. Table des Tables & QR Codes Restaurant
CREATE TABLE IF NOT EXISTS public.restaurant_tables (
    id TEXT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    table_number TEXT NOT NULL,
    zone TEXT DEFAULT 'Salle Principale',
    seats INTEGER DEFAULT 4,
    is_active BOOLEAN DEFAULT true,
    updated_at BIGINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_restaurant_tables_shop_id ON public.restaurant_tables(shop_id);

-- 17. Table des Commandes Restaurant & Cuisine Synchronisées
CREATE TABLE IF NOT EXISTS public.restaurant_orders (
    id TEXT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    order_number TEXT NOT NULL,
    table_name TEXT NOT NULL,
    customer_name TEXT DEFAULT 'Client Table',
    customer_phone TEXT DEFAULT '',
    source TEXT DEFAULT 'QR_TABLE',
    status TEXT NOT NULL DEFAULT 'PENDING_MANAGER',
    items_json TEXT NOT NULL DEFAULT '[]',
    total_amount BIGINT DEFAULT 0,
    notes TEXT DEFAULT '',
    created_by_role TEXT DEFAULT 'CLIENT_QR',
    created_by_name TEXT DEFAULT 'Client',
    assigned_to_kitchen_by TEXT DEFAULT '',
    sale_id BIGINT,
    created_at BIGINT DEFAULT 0,
    updated_at BIGINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_shop_id ON public.restaurant_orders(shop_id);
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_status ON public.restaurant_orders(status);
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_created_at ON public.restaurant_orders(created_at);

-- 18. Table du Menu du Jour Restaurant
CREATE TABLE IF NOT EXISTS public.restaurant_menu_day (
    shop_id BIGINT PRIMARY KEY,
    product_ids_json TEXT NOT NULL DEFAULT '[]',
    updated_at BIGINT DEFAULT 0
);

-- 19. Table des Modèles & Catalogues Couture
CREATE TABLE IF NOT EXISTS public.couture_models (
    id TEXT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    shop_name TEXT DEFAULT '',
    title TEXT NOT NULL,
    category TEXT DEFAULT 'Tenue sur-mesure',
    description TEXT DEFAULT '',
    price BIGINT DEFAULT 0,
    estimated_days INTEGER DEFAULT 5,
    image_uri TEXT DEFAULT 'img_prod_mode',
    gallery_images_json TEXT DEFAULT '[]',
    is_featured BOOLEAN DEFAULT true,
    is_available BOOLEAN DEFAULT true,
    updated_at BIGINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_couture_models_shop_id ON public.couture_models(shop_id);

-- 20. Table des Fiches Mesures, Photos Tissus, Rendez-vous & Suivi Couture
CREATE TABLE IF NOT EXISTS public.couture_orders (
    id TEXT PRIMARY KEY,
    shop_id BIGINT NOT NULL DEFAULT 0,
    shop_name TEXT DEFAULT '',
    order_number TEXT NOT NULL,
    customer_first_name TEXT DEFAULT '',
    customer_last_name TEXT DEFAULT '',
    customer_name TEXT DEFAULT 'Client Atelier',
    customer_phone TEXT DEFAULT '',
    model_id TEXT DEFAULT '',
    model_title TEXT DEFAULT 'Tenue sur-mesure',
    model_image_uri TEXT DEFAULT 'img_prod_mode',
    is_custom_client_model BOOLEAN DEFAULT false,
    fabric_photo_uri TEXT DEFAULT '',
    fabric_notes TEXT DEFAULT '',
    measurements_json TEXT NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'MESURES_PRISES',
    appointment_at BIGINT DEFAULT 0,
    appointment_note TEXT DEFAULT '',
    total_price BIGINT DEFAULT 0,
    advance_paid BIGINT DEFAULT 0,
    sale_id BIGINT,
    tailor_notes TEXT DEFAULT '',
    created_at BIGINT DEFAULT 0,
    updated_at BIGINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_couture_orders_shop_id ON public.couture_orders(shop_id);
CREATE INDEX IF NOT EXISTS idx_couture_orders_phone ON public.couture_orders(customer_phone);

-- 21. Table des Comptes Clients Couture (Suivi Tenue)
CREATE TABLE IF NOT EXISTS public.couture_clients (
    id TEXT PRIMARY KEY,
    first_name TEXT DEFAULT '',
    last_name TEXT DEFAULT '',
    phone TEXT NOT NULL,
    city TEXT DEFAULT '',
    updated_at BIGINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_couture_clients_phone ON public.couture_clients(phone);

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
ALTER TABLE public.shop_ads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_tables ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.restaurant_menu_day ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.couture_models ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.couture_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.couture_clients ENABLE ROW LEVEL SECURITY;

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
DROP POLICY IF EXISTS "Allow anon read/write for shop_ads" ON public.shop_ads;
DROP POLICY IF EXISTS "Allow anon read/write for restaurant_staff" ON public.restaurant_staff;
DROP POLICY IF EXISTS "Allow anon read/write for restaurant_tables" ON public.restaurant_tables;
DROP POLICY IF EXISTS "Allow anon read/write for restaurant_orders" ON public.restaurant_orders;
DROP POLICY IF EXISTS "Allow anon read/write for restaurant_menu_day" ON public.restaurant_menu_day;
DROP POLICY IF EXISTS "Allow anon read/write for couture_models" ON public.couture_models;
DROP POLICY IF EXISTS "Allow anon read/write for couture_orders" ON public.couture_orders;
DROP POLICY IF EXISTS "Allow anon read/write for couture_clients" ON public.couture_clients;

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
CREATE POLICY "Allow anon read/write for shop_ads" ON public.shop_ads FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for restaurant_staff" ON public.restaurant_staff FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for restaurant_tables" ON public.restaurant_tables FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for restaurant_orders" ON public.restaurant_orders FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for restaurant_menu_day" ON public.restaurant_menu_day FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for couture_models" ON public.couture_models FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for couture_orders" ON public.couture_orders FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon read/write for couture_clients" ON public.couture_clients FOR ALL USING (true) WITH CHECK (true);

-- Bucket Storage public pour héberger les logos et vidéos publicitaires (60s)
INSERT INTO storage.buckets (id, name, public)
VALUES ('shop-ads', 'shop-ads', true)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "Allow public read shop-ads" ON storage.objects;
DROP POLICY IF EXISTS "Allow anon upload shop-ads" ON storage.objects;
CREATE POLICY "Allow public read shop-ads" ON storage.objects FOR SELECT USING (bucket_id = 'shop-ads');
CREATE POLICY "Allow anon upload shop-ads" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'shop-ads');
        """.trimIndent()
    }
}
