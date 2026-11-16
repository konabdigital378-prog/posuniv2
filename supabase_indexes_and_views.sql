-- ============================================================================
-- KONAB POS — SCRIPT SQL STRICT POUR SUPABASE DASHBOARD (INDEX & VUES)
-- ============================================================================
-- Objectifs :
--   1. Accélérer les requêtes PostgREST filtrées par shop_id / business_id
--      et triées (éviter les Seq Scan + Tris coûteux).
--   2. Réduire le Cached Egress via des vues strictes à projection légère
--      (exclusion des PINs, exclusion des colonnes JSON massives et des
--      anciens blobs Base64 "data:image/..." dans les listes).
--   3. Préserver intégralement les tables, utilisateurs, données et RLS :
--      - ZÉRO suppression (aucun DROP TABLE / DELETE / TRUNCATE)
--      - ZÉRO désactivation de RLS
--      - Vues sécurisées avec WITH (security_invoker = true) (PostgreSQL 15+)
--
-- Exécution : Supabase Dashboard -> SQL Editor -> New Query -> Run
-- ============================================================================


-- ============================================================================
-- PARTIE 1 : INDEX COMPOSITES & PARTIELS (100% ADDITIFS & IDEMPOTENTS)
-- ============================================================================

-- 1. shop_profile
CREATE INDEX IF NOT EXISTS idx_shop_profile_updated_at
    ON public.shop_profile (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_shop_profile_deployer_updated
    ON public.shop_profile (deployer_id, updated_at DESC);

-- 2. products (catalogue & tri alphabétique par boutique)
CREATE INDEX IF NOT EXISTS idx_products_shop_name
    ON public.products (shop_id, name ASC);

CREATE INDEX IF NOT EXISTS idx_products_shop_available_name
    ON public.products (shop_id, is_available, name ASC)
    WHERE is_available = true;

-- 3. customers (tri par total_spent DESC dans l'app)
CREATE INDEX IF NOT EXISTS idx_customers_shop_total_spent
    ON public.customers (shop_id, total_spent DESC);

CREATE INDEX IF NOT EXISTS idx_customers_shop_debt
    ON public.customers (shop_id, debt_amount DESC)
    WHERE debt_amount > 0;

-- 4. sales (historique chronologique par boutique + ventes actives)
CREATE INDEX IF NOT EXISTS idx_sales_shop_timestamp
    ON public.sales (shop_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_sales_shop_active_timestamp
    ON public.sales (shop_id, timestamp DESC)
    WHERE is_cancelled = false;

-- 5. sale_items (lecture récente par boutique + jointure sale_id)
CREATE INDEX IF NOT EXISTS idx_sale_items_shop_id_desc
    ON public.sale_items (shop_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_sale_items_sale_product
    ON public.sale_items (sale_id, product_id);

-- 6. suppliers
CREATE INDEX IF NOT EXISTS idx_suppliers_shop_name
    ON public.suppliers (shop_id, name ASC);

-- 7. purchases
CREATE INDEX IF NOT EXISTS idx_purchases_shop_timestamp
    ON public.purchases (shop_id, timestamp DESC);

-- 8. cash_sessions & cash_movements
CREATE INDEX IF NOT EXISTS idx_cash_sessions_shop_opened_at
    ON public.cash_sessions (shop_id, opened_at DESC);

CREATE INDEX IF NOT EXISTS idx_cash_movements_shop_timestamp
    ON public.cash_movements (shop_id, timestamp DESC);

-- 9. app_notifications (flux boutique + miroirs & nettoyage par action_route)
CREATE INDEX IF NOT EXISTS idx_app_notifications_shop_ts
    ON public.app_notifications (shop_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_app_notifications_type_shop_ts
    ON public.app_notifications (type, shop_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_app_notifications_action_type_shop
    ON public.app_notifications (action_route, type, shop_id);

-- 10. agent_deployments
CREATE INDEX IF NOT EXISTS idx_agent_deployments_agent_ts
    ON public.agent_deployments (agent_id, timestamp DESC);

-- 11. shop_ads (vitrine active triée par fraîcheur)
CREATE INDEX IF NOT EXISTS idx_shop_ads_active_updated
    ON public.shop_ads (is_active, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_shop_ads_shop_updated
    ON public.shop_ads (shop_id, updated_at DESC);

-- 12. Restaurant (staff, tables, commandes, menu du jour & articles)
CREATE INDEX IF NOT EXISTS idx_restaurant_staff_shop_updated
    ON public.restaurant_staff (shop_id, updated_at ASC);

CREATE INDEX IF NOT EXISTS idx_restaurant_staff_user_id
    ON public.restaurant_staff (user_id)
    WHERE user_id <> '';

CREATE INDEX IF NOT EXISTS idx_restaurant_tables_shop_num
    ON public.restaurant_tables (shop_id, table_number ASC);

CREATE INDEX IF NOT EXISTS idx_restaurant_orders_shop_created
    ON public.restaurant_orders (shop_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_restaurant_orders_shop_status_updated
    ON public.restaurant_orders (shop_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_restaurant_menu_day_updated
    ON public.restaurant_menu_day (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_restaurant_menu_items_shop_avail
    ON public.restaurant_menu_items (shop_id, is_available, id ASC);

-- 13. Couture (modèles & commandes atelier / suivi client)
CREATE INDEX IF NOT EXISTS idx_couture_models_avail_updated
    ON public.couture_models (is_available, updated_at DESC)
    WHERE is_available = true;

CREATE INDEX IF NOT EXISTS idx_couture_models_shop_updated
    ON public.couture_models (shop_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_couture_orders_shop_updated
    ON public.couture_orders (shop_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_couture_orders_phone_updated
    ON public.couture_orders (customer_phone, updated_at DESC);

-- 14. Portails Poubelles (Waste) & Tontine
CREATE INDEX IF NOT EXISTS idx_waste_businesses_updated
    ON public.waste_businesses (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_waste_clients_biz_updated
    ON public.waste_clients (business_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_tontine_businesses_updated
    ON public.tontine_businesses (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_tontine_groups_biz_updated
    ON public.tontine_groups (business_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_tontine_cards_biz_updated
    ON public.tontine_cards (business_id, updated_at DESC);

-- 15. Utilisateurs publics
CREATE INDEX IF NOT EXISTS idx_app_users_updated_at
    ON public.app_users (updated_at DESC);


-- ============================================================================
-- PARTIE 2 : VUES LÉGÈRES & SÉCURISÉES (REDUCTION D'EGRESS + RESPECT RLS)
-- ============================================================================
-- Toutes les vues utilisent WITH (security_invoker = true) afin d'appliquer
-- strictement les politiques RLS de l'appelant et excluent les champs lourds
-- (galeries JSON, Base64 data:image/..., PINs privés).

-- Vue 1 : Annuaire public des boutiques (sans PINs, sans Base64 inline)
CREATE OR REPLACE VIEW public.v_shop_directory_light
WITH (security_invoker = true) AS
SELECT
    id,
    name,
    business_type,
    phone,
    address,
    city,
    currency,
    slogan,
    subscription_tier,
    deployer_id,
    CASE
        WHEN logo_uri LIKE 'data:image/%' THEN ''
        ELSE COALESCE(logo_uri, '')
    END AS logo_uri,
    (logo_uri LIKE 'data:image/%') AS has_legacy_inline_logo,
    updated_at
FROM public.shop_profile;

GRANT SELECT ON public.v_shop_directory_light TO anon, authenticated;

-- Vue 2 : Catalogue produits léger (sans gallery_images_json / images JSONB / Base64)
CREATE OR REPLACE VIEW public.v_products_catalog_light
WITH (security_invoker = true) AS
SELECT
    id,
    shop_id,
    shop_name,
    name,
    category,
    sell_price,
    buy_price,
    stock_quantity,
    min_stock_alert,
    barcode,
    unit,
    icon_name,
    is_available,
    CASE
        WHEN image_uri LIKE 'data:image/%' THEN ''
        ELSE COALESCE(image_uri, '')
    END AS image_uri,
    (image_uri LIKE 'data:image/%' OR LENGTH(COALESCE(gallery_images_json, '')) > 512) AS has_inline_media_bloat
FROM public.products;

GRANT SELECT ON public.v_products_catalog_light TO anon, authenticated;

-- Vue 3 : Vitrine publicitaire active allégée
CREATE OR REPLACE VIEW public.v_shop_ads_feed_light
WITH (security_invoker = true) AS
SELECT
    id,
    shop_id,
    shop_name,
    business_type,
    city,
    phone,
    whatsapp_phone,
    CASE
        WHEN logo_uri LIKE 'data:image/%' THEN ''
        ELSE COALESCE(logo_uri, '')
    END AS logo_uri,
    ad_title,
    ad_description,
    video_uri,
    video_duration_sec,
    cta_label,
    is_active,
    deployer_id,
    updated_at
FROM public.shop_ads
WHERE is_active = true;

GRANT SELECT ON public.v_shop_ads_feed_light TO anon, authenticated;

-- Vue 4 : Catalogue public Couture léger (sans gallery_images_json ni Base64)
CREATE OR REPLACE VIEW public.v_couture_models_public_light
WITH (security_invoker = true) AS
SELECT
    id,
    shop_id,
    shop_name,
    title,
    category,
    description,
    price,
    estimated_days,
    CASE
        WHEN image_uri LIKE 'data:image/%' THEN 'img_prod_mode'
        ELSE COALESCE(NULLIF(image_uri, ''), 'img_prod_mode')
    END AS image_uri,
    is_featured,
    is_available,
    updated_at
FROM public.couture_models
WHERE is_available = true;

GRANT SELECT ON public.v_couture_models_public_light TO anon, authenticated;

-- Vue 5 : Commandes Restaurant en direct (projection compacte pour cuisine & salle)
CREATE OR REPLACE VIEW public.v_restaurant_orders_live
WITH (security_invoker = true) AS
SELECT
    id,
    shop_id,
    order_number,
    table_name,
    customer_name,
    source,
    status,
    items_json,
    total_amount,
    notes,
    assigned_to_kitchen_by,
    sale_id,
    created_at,
    updated_at
FROM public.restaurant_orders;

GRANT SELECT ON public.v_restaurant_orders_live TO anon, authenticated;

-- Vue 6 : Agrégats journaliers de ventes par boutique (évite de tirer N tickets pour les KPIs)
CREATE OR REPLACE VIEW public.v_shop_daily_sales_stats
WITH (security_invoker = true) AS
SELECT
    shop_id,
    (to_timestamp(timestamp / 1000.0) AT TIME ZONE 'UTC')::date AS sale_date_utc,
    COUNT(*)::INTEGER AS sales_count,
    COALESCE(SUM(total_amount), 0)::BIGINT AS revenue_total,
    COALESCE(SUM(discount_amount), 0)::BIGINT AS discount_total,
    COALESCE(SUM(total_cost_price), 0)::BIGINT AS cost_total,
    COALESCE(SUM(total_amount - total_cost_price), 0)::BIGINT AS gross_margin_total,
    COALESCE(SUM(amount_paid), 0)::BIGINT AS paid_total
FROM public.sales
WHERE is_cancelled = false
GROUP BY shop_id, (to_timestamp(timestamp / 1000.0) AT TIME ZONE 'UTC')::date;

GRANT SELECT ON public.v_shop_daily_sales_stats TO anon, authenticated;

-- Vue 7 : Profils utilisateurs publics (sans PIN et sans Base64 lourd)
CREATE OR REPLACE VIEW public.v_app_users_public_safe
WITH (security_invoker = true) AS
SELECT
    id,
    full_name,
    phone,
    city,
    neighborhood,
    CASE
        WHEN photo_uri LIKE 'data:image/%' THEN ''
        ELSE COALESCE(photo_uri, '')
    END AS photo_uri,
    preferred_activity,
    is_seeking_accompaniment,
    accompaniment_details,
    is_shop_member,
    member_shop_name,
    member_shop_role,
    created_at,
    updated_at
FROM public.app_users;

GRANT SELECT ON public.v_app_users_public_safe TO anon, authenticated;


-- ============================================================================
-- PARTIE 3 : VUE & REQUÊTES D'AUDIT EGRESS (USAGE INTERNE DASHBOARD UNIQUEMENT)
-- ============================================================================
-- Réservée aux rôles SQL Editor / service_role (révoquée pour anon/authenticated).

CREATE OR REPLACE VIEW public.v_audit_inline_base64_bloat
WITH (security_invoker = true) AS
SELECT 'shop_profile.logo_uri'::TEXT AS column_source,
       COUNT(*)::BIGINT AS rows_with_base64,
       ROUND(COALESCE(SUM(OCTET_LENGTH(logo_uri)), 0) / 1048576.0, 2) AS estimated_mb
FROM public.shop_profile
WHERE logo_uri LIKE 'data:image/%'
UNION ALL
SELECT 'products.image_uri'::TEXT,
       COUNT(*)::BIGINT,
       ROUND(COALESCE(SUM(OCTET_LENGTH(image_uri)), 0) / 1048576.0, 2)
FROM public.products
WHERE image_uri LIKE 'data:image/%'
UNION ALL
SELECT 'products.gallery_images_json'::TEXT,
       COUNT(*)::BIGINT,
       ROUND(COALESCE(SUM(OCTET_LENGTH(gallery_images_json)), 0) / 1048576.0, 2)
FROM public.products
WHERE gallery_images_json LIKE '%data:image/%'
UNION ALL
SELECT 'shop_ads.logo_uri'::TEXT,
       COUNT(*)::BIGINT,
       ROUND(COALESCE(SUM(OCTET_LENGTH(logo_uri)), 0) / 1048576.0, 2)
FROM public.shop_ads
WHERE logo_uri LIKE 'data:image/%'
UNION ALL
SELECT 'couture_models.image_uri'::TEXT,
       COUNT(*)::BIGINT,
       ROUND(COALESCE(SUM(OCTET_LENGTH(image_uri)), 0) / 1048576.0, 2)
FROM public.couture_models
WHERE image_uri LIKE 'data:image/%'
UNION ALL
SELECT 'couture_orders.fabric_photo_uri'::TEXT,
       COUNT(*)::BIGINT,
       ROUND(COALESCE(SUM(OCTET_LENGTH(fabric_photo_uri)), 0) / 1048576.0, 2)
FROM public.couture_orders
WHERE fabric_photo_uri LIKE 'data:image/%'
UNION ALL
SELECT 'app_users.photo_uri'::TEXT,
       COUNT(*)::BIGINT,
       ROUND(COALESCE(SUM(OCTET_LENGTH(photo_uri)), 0) / 1048576.0, 2)
FROM public.app_users
WHERE photo_uri LIKE 'data:image/%';

REVOKE ALL ON public.v_audit_inline_base64_bloat FROM PUBLIC, anon, authenticated;

-- Rafraîchir les statistiques du planificateur PostgreSQL après création des index
ANALYZE public.shop_profile;
ANALYZE public.products;
ANALYZE public.customers;
ANALYZE public.sales;
ANALYZE public.sale_items;
ANALYZE public.suppliers;
ANALYZE public.purchases;
ANALYZE public.cash_sessions;
ANALYZE public.cash_movements;
ANALYZE public.app_notifications;
ANALYZE public.agent_deployments;
ANALYZE public.shop_ads;
ANALYZE public.restaurant_staff;
ANALYZE public.restaurant_tables;
ANALYZE public.restaurant_orders;
ANALYZE public.restaurant_menu_day;
ANALYZE public.restaurant_menu_items;
ANALYZE public.couture_models;
ANALYZE public.couture_orders;
ANALYZE public.waste_businesses;
ANALYZE public.waste_clients;
ANALYZE public.tontine_businesses;
ANALYZE public.tontine_groups;
ANALYZE public.tontine_cards;
ANALYZE public.app_users;
