-- ============================================================================
-- KONAB POS — SCRIPT SQL STRICT & ADAPTATIF POUR SUPABASE DASHBOARD
-- (INDEX COMPOSITES/PARTIELS + VUES LÉGÈRES SECURITY_INVOKER + AUDIT EGRESS)
-- ============================================================================
-- Caractéristiques :
--   1. 100 % additif, idempotent et tolérant aux schémas partiels :
--      chaque index et chaque vue vérifie l'existence réelle de la table et
--      des colonnes avant création (aucun rollback si une table optionnelle
--      n'est pas encore créée sur votre projet).
--   2. Zéro suppression (aucun DROP TABLE / DELETE / TRUNCATE), zéro
--      modification de RLS (vues avec WITH (security_invoker = true)).
--   3. Exécuter TOUT ce script d'abord dans :
--      Supabase Dashboard -> SQL Editor -> New Query -> Run
-- ============================================================================

DO $konab$
DECLARE
    v_parts TEXT[] := ARRAY[]::TEXT[];
    v_has_table BOOLEAN;
    v_has_cols BOOLEAN;
    v_tbl TEXT;
    v_tables TEXT[] := ARRAY[
        'shop_profile', 'products', 'customers', 'sales', 'sale_items',
        'suppliers', 'purchases', 'cash_sessions', 'cash_movements',
        'app_notifications', 'agent_deployments', 'shop_ads',
        'restaurant_staff', 'restaurant_tables', 'restaurant_orders',
        'restaurant_menu_day', 'restaurant_menu_items',
        'couture_models', 'couture_orders',
        'waste_businesses', 'waste_clients',
        'tontine_businesses', 'tontine_groups', 'tontine_cards',
        'app_users'
    ];
BEGIN
    -- ========================================================================
    -- PARTIE 1 : INDEX COMPOSITES & PARTIELS (ADDITIFS)
    -- ========================================================================

    -- 1. shop_profile
    IF to_regclass('public.shop_profile') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_profile' AND column_name='updated_at') THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_shop_profile_updated_at ON public.shop_profile (updated_at DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_profile' AND column_name IN ('deployer_id','updated_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_shop_profile_deployer_updated ON public.shop_profile (deployer_id, updated_at DESC)';
        END IF;
    END IF;

    -- 2. products
    IF to_regclass('public.products') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name IN ('shop_id','name')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_products_shop_name ON public.products (shop_id, name ASC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name IN ('shop_id','is_available','name')) = 3 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_products_shop_available_name ON public.products (shop_id, is_available, name ASC) WHERE is_available = true';
        END IF;
    END IF;

    -- 3. customers
    IF to_regclass('public.customers') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='customers' AND column_name IN ('shop_id','total_spent')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_customers_shop_total_spent ON public.customers (shop_id, total_spent DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='customers' AND column_name IN ('shop_id','debt_amount')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_customers_shop_debt ON public.customers (shop_id, debt_amount DESC) WHERE debt_amount > 0';
        END IF;
    END IF;

    -- 4. sales
    IF to_regclass('public.sales') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='sales' AND column_name IN ('shop_id','timestamp')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_sales_shop_timestamp ON public.sales (shop_id, timestamp DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='sales' AND column_name IN ('shop_id','timestamp','is_cancelled')) = 3 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_sales_shop_active_timestamp ON public.sales (shop_id, timestamp DESC) WHERE is_cancelled = false';
        END IF;
    END IF;

    -- 5. sale_items
    IF to_regclass('public.sale_items') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='sale_items' AND column_name IN ('shop_id','id')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_sale_items_shop_id_desc ON public.sale_items (shop_id, id DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='sale_items' AND column_name IN ('sale_id','product_id')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_sale_items_sale_product ON public.sale_items (sale_id, product_id)';
        END IF;
    END IF;

    -- 6. suppliers
    IF to_regclass('public.suppliers') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='suppliers' AND column_name IN ('shop_id','name')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_suppliers_shop_name ON public.suppliers (shop_id, name ASC)';
    END IF;

    -- 7. purchases
    IF to_regclass('public.purchases') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='purchases' AND column_name IN ('shop_id','timestamp')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_purchases_shop_timestamp ON public.purchases (shop_id, timestamp DESC)';
    END IF;

    -- 8. cash_sessions & cash_movements
    IF to_regclass('public.cash_sessions') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='cash_sessions' AND column_name IN ('shop_id','opened_at')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_cash_sessions_shop_opened_at ON public.cash_sessions (shop_id, opened_at DESC)';
    END IF;

    IF to_regclass('public.cash_movements') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='cash_movements' AND column_name IN ('shop_id','timestamp')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_cash_movements_shop_timestamp ON public.cash_movements (shop_id, timestamp DESC)';
    END IF;

    -- 9. app_notifications
    IF to_regclass('public.app_notifications') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='app_notifications' AND column_name IN ('shop_id','timestamp')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_app_notifications_shop_ts ON public.app_notifications (shop_id, timestamp DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='app_notifications' AND column_name IN ('type','shop_id','timestamp')) = 3 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_app_notifications_type_shop_ts ON public.app_notifications (type, shop_id, timestamp DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='app_notifications' AND column_name IN ('action_route','type','shop_id')) = 3 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_app_notifications_action_type_shop ON public.app_notifications (action_route, type, shop_id)';
        END IF;
    END IF;

    -- 10. agent_deployments
    IF to_regclass('public.agent_deployments') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='agent_deployments' AND column_name IN ('agent_id','timestamp')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_agent_deployments_agent_ts ON public.agent_deployments (agent_id, timestamp DESC)';
    END IF;

    -- 11. shop_ads
    IF to_regclass('public.shop_ads') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_ads' AND column_name IN ('is_active','updated_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_shop_ads_active_updated ON public.shop_ads (is_active, updated_at DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_ads' AND column_name IN ('shop_id','updated_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_shop_ads_shop_updated ON public.shop_ads (shop_id, updated_at DESC)';
        END IF;
    END IF;

    -- 12. Restaurant
    IF to_regclass('public.restaurant_staff') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='restaurant_staff' AND column_name IN ('shop_id','updated_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_restaurant_staff_shop_updated ON public.restaurant_staff (shop_id, updated_at ASC)';
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='restaurant_staff' AND column_name='user_id') THEN
            EXECUTE $sql$CREATE INDEX IF NOT EXISTS idx_restaurant_staff_user_id ON public.restaurant_staff (user_id) WHERE user_id <> ''$sql$;
        END IF;
    END IF;

    IF to_regclass('public.restaurant_tables') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='restaurant_tables' AND column_name IN ('shop_id','table_number')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_restaurant_tables_shop_num ON public.restaurant_tables (shop_id, table_number ASC)';
    END IF;

    IF to_regclass('public.restaurant_orders') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='restaurant_orders' AND column_name IN ('shop_id','created_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_restaurant_orders_shop_created ON public.restaurant_orders (shop_id, created_at DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='restaurant_orders' AND column_name IN ('shop_id','status','updated_at')) = 3 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_restaurant_orders_shop_status_updated ON public.restaurant_orders (shop_id, status, updated_at DESC)';
        END IF;
    END IF;

    IF to_regclass('public.restaurant_menu_day') IS NOT NULL AND
       EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='restaurant_menu_day' AND column_name='updated_at') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_restaurant_menu_day_updated ON public.restaurant_menu_day (updated_at DESC)';
    END IF;

    IF to_regclass('public.restaurant_menu_items') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='restaurant_menu_items' AND column_name IN ('shop_id','is_available','id')) = 3 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_restaurant_menu_items_shop_avail ON public.restaurant_menu_items (shop_id, is_available, id ASC)';
    END IF;

    -- 13. Couture
    IF to_regclass('public.couture_models') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='couture_models' AND column_name IN ('is_available','updated_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_couture_models_avail_updated ON public.couture_models (is_available, updated_at DESC) WHERE is_available = true';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='couture_models' AND column_name IN ('shop_id','updated_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_couture_models_shop_updated ON public.couture_models (shop_id, updated_at DESC)';
        END IF;
    END IF;

    IF to_regclass('public.couture_orders') IS NOT NULL THEN
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='couture_orders' AND column_name IN ('shop_id','updated_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_couture_orders_shop_updated ON public.couture_orders (shop_id, updated_at DESC)';
        END IF;
        IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='couture_orders' AND column_name IN ('customer_phone','updated_at')) = 2 THEN
            EXECUTE 'CREATE INDEX IF NOT EXISTS idx_couture_orders_phone_updated ON public.couture_orders (customer_phone, updated_at DESC)';
        END IF;
    END IF;

    -- 14. Portails Poubelles & Tontine
    IF to_regclass('public.waste_businesses') IS NOT NULL AND
       EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='waste_businesses' AND column_name='updated_at') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_waste_businesses_updated ON public.waste_businesses (updated_at DESC)';
    END IF;

    IF to_regclass('public.waste_clients') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='waste_clients' AND column_name IN ('business_id','updated_at')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_waste_clients_biz_updated ON public.waste_clients (business_id, updated_at DESC)';
    END IF;

    IF to_regclass('public.tontine_businesses') IS NOT NULL AND
       EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='tontine_businesses' AND column_name='updated_at') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_tontine_businesses_updated ON public.tontine_businesses (updated_at DESC)';
    END IF;

    IF to_regclass('public.tontine_groups') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='tontine_groups' AND column_name IN ('business_id','updated_at')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_tontine_groups_biz_updated ON public.tontine_groups (business_id, updated_at DESC)';
    END IF;

    IF to_regclass('public.tontine_cards') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='tontine_cards' AND column_name IN ('business_id','updated_at')) = 2 THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_tontine_cards_biz_updated ON public.tontine_cards (business_id, updated_at DESC)';
    END IF;

    -- 15. app_users
    IF to_regclass('public.app_users') IS NOT NULL AND
       EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='updated_at') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_app_users_updated_at ON public.app_users (updated_at DESC)';
    END IF;


    -- ========================================================================
    -- PARTIE 2 : VUES LÉGÈRES & SÉCURISÉES (WITH security_invoker = true)
    -- ========================================================================

    -- Vue 1 : Annuaire public boutiques (sans PINs ni Base64 inline)
    IF to_regclass('public.shop_profile') IS NOT NULL THEN
        EXECUTE format($fmt$
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
                %s AS deployer_id,
                %s AS logo_uri,
                %s AS has_legacy_inline_logo,
                updated_at
            FROM public.shop_profile
        $fmt$,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_profile' AND column_name='deployer_id') THEN 'deployer_id' ELSE '''''::TEXT' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_profile' AND column_name='logo_uri') THEN $c$CASE WHEN logo_uri LIKE 'data:image/%' THEN '' ELSE COALESCE(logo_uri, '') END$c$ ELSE '''''::TEXT' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_profile' AND column_name='logo_uri') THEN $c$(logo_uri LIKE 'data:image/%')$c$ ELSE 'false' END
        );
        EXECUTE 'GRANT SELECT ON public.v_shop_directory_light TO anon, authenticated';
    END IF;

    -- Vue 2 : Catalogue produits léger (sans gallery_images_json / images JSONB / Base64)
    IF to_regclass('public.products') IS NOT NULL THEN
        EXECUTE format($fmt$
            CREATE OR REPLACE VIEW public.v_products_catalog_light
            WITH (security_invoker = true) AS
            SELECT
                id,
                shop_id,
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
                %s AS image_uri,
                %s AS has_inline_media_bloat
            FROM public.products
        $fmt$,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='image_uri') THEN $c$CASE WHEN image_uri LIKE 'data:image/%' THEN '' ELSE COALESCE(image_uri, '') END$c$ ELSE '''''::TEXT' END,
            CASE
                WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='image_uri')
                 AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='gallery_images_json')
                THEN $c$(image_uri LIKE 'data:image/%' OR LENGTH(COALESCE(gallery_images_json, '')) > 512)$c$
                WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='image_uri')
                THEN $c$(image_uri LIKE 'data:image/%')$c$
                ELSE 'false'
            END
        );
        EXECUTE 'GRANT SELECT ON public.v_products_catalog_light TO anon, authenticated';
    END IF;

    -- Vue 3 : Vitrine publicitaire active allégée
    IF to_regclass('public.shop_ads') IS NOT NULL THEN
        EXECUTE $sql$
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
            WHERE is_active = true
        $sql$;
        EXECUTE 'GRANT SELECT ON public.v_shop_ads_feed_light TO anon, authenticated';
    END IF;

    -- Vue 4 : Catalogue public Couture léger
    IF to_regclass('public.couture_models') IS NOT NULL THEN
        EXECUTE $sql$
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
            WHERE is_available = true
        $sql$;
        EXECUTE 'GRANT SELECT ON public.v_couture_models_public_light TO anon, authenticated';
    END IF;

    -- Vue 5 : Commandes Restaurant en direct
    IF to_regclass('public.restaurant_orders') IS NOT NULL THEN
        EXECUTE $sql$
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
            FROM public.restaurant_orders
        $sql$;
        EXECUTE 'GRANT SELECT ON public.v_restaurant_orders_live TO anon, authenticated';
    END IF;

    -- Vue 6 : Agrégats journaliers de ventes par boutique (KPIs & courbes Dashboard)
    IF to_regclass('public.sales') IS NOT NULL AND
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='sales' AND column_name IN ('shop_id','timestamp','total_amount','is_cancelled')) = 4 THEN
        EXECUTE $sql$
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
            GROUP BY shop_id, (to_timestamp(timestamp / 1000.0) AT TIME ZONE 'UTC')::date
        $sql$;
        EXECUTE 'GRANT SELECT ON public.v_shop_daily_sales_stats TO anon, authenticated';
    END IF;

    -- Vue 7 : Profils utilisateurs publics sans PIN ni Base64 lourd
    IF to_regclass('public.app_users') IS NOT NULL THEN
        EXECUTE format($fmt$
            CREATE OR REPLACE VIEW public.v_app_users_public_safe
            WITH (security_invoker = true) AS
            SELECT
                id,
                full_name,
                phone,
                city,
                neighborhood,
                CASE
                    WHEN photo_uri LIKE 'data:image/%%' THEN ''
                    ELSE COALESCE(photo_uri, '')
                END AS photo_uri,
                preferred_activity,
                %s AS is_seeking_accompaniment,
                %s AS accompaniment_details,
                %s AS is_shop_member,
                %s AS member_shop_name,
                %s AS member_shop_role,
                created_at,
                updated_at
            FROM public.app_users
        $fmt$,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='is_seeking_accompaniment') THEN 'is_seeking_accompaniment' ELSE 'false' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='accompaniment_details') THEN 'accompaniment_details' ELSE '''''::TEXT' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='is_shop_member') THEN 'is_shop_member' ELSE 'false' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='member_shop_name') THEN 'member_shop_name' ELSE '''''::TEXT' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='member_shop_role') THEN 'member_shop_role' ELSE '''''::TEXT' END
        );
        EXECUTE 'GRANT SELECT ON public.v_app_users_public_safe TO anon, authenticated';
    END IF;


    -- ========================================================================
    -- PARTIE 3 : VUE D'AUDIT BASE64 DYNAMIQUE (DASHBOARD INTERNE UNIQUEMENT)
    -- ========================================================================
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_profile' AND column_name='logo_uri') THEN
        v_parts := array_append(v_parts, $sql$SELECT 'shop_profile.logo_uri'::TEXT AS column_source, COUNT(*)::BIGINT AS rows_with_base64, ROUND(COALESCE(SUM(OCTET_LENGTH(logo_uri)), 0) / 1048576.0, 2) AS estimated_mb FROM public.shop_profile WHERE logo_uri LIKE 'data:image/%'$sql$);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='image_uri') THEN
        v_parts := array_append(v_parts, $sql$SELECT 'products.image_uri'::TEXT AS column_source, COUNT(*)::BIGINT AS rows_with_base64, ROUND(COALESCE(SUM(OCTET_LENGTH(image_uri)), 0) / 1048576.0, 2) AS estimated_mb FROM public.products WHERE image_uri LIKE 'data:image/%'$sql$);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='gallery_images_json') THEN
        v_parts := array_append(v_parts, $sql$SELECT 'products.gallery_images_json'::TEXT AS column_source, COUNT(*)::BIGINT AS rows_with_base64, ROUND(COALESCE(SUM(OCTET_LENGTH(gallery_images_json)), 0) / 1048576.0, 2) AS estimated_mb FROM public.products WHERE gallery_images_json LIKE '%data:image/%'$sql$);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shop_ads' AND column_name='logo_uri') THEN
        v_parts := array_append(v_parts, $sql$SELECT 'shop_ads.logo_uri'::TEXT AS column_source, COUNT(*)::BIGINT AS rows_with_base64, ROUND(COALESCE(SUM(OCTET_LENGTH(logo_uri)), 0) / 1048576.0, 2) AS estimated_mb FROM public.shop_ads WHERE logo_uri LIKE 'data:image/%'$sql$);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='couture_models' AND column_name='image_uri') THEN
        v_parts := array_append(v_parts, $sql$SELECT 'couture_models.image_uri'::TEXT AS column_source, COUNT(*)::BIGINT AS rows_with_base64, ROUND(COALESCE(SUM(OCTET_LENGTH(image_uri)), 0) / 1048576.0, 2) AS estimated_mb FROM public.couture_models WHERE image_uri LIKE 'data:image/%'$sql$);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='couture_orders' AND column_name='fabric_photo_uri') THEN
        v_parts := array_append(v_parts, $sql$SELECT 'couture_orders.fabric_photo_uri'::TEXT AS column_source, COUNT(*)::BIGINT AS rows_with_base64, ROUND(COALESCE(SUM(OCTET_LENGTH(fabric_photo_uri)), 0) / 1048576.0, 2) AS estimated_mb FROM public.couture_orders WHERE fabric_photo_uri LIKE 'data:image/%'$sql$);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='photo_uri') THEN
        v_parts := array_append(v_parts, $sql$SELECT 'app_users.photo_uri'::TEXT AS column_source, COUNT(*)::BIGINT AS rows_with_base64, ROUND(COALESCE(SUM(OCTET_LENGTH(photo_uri)), 0) / 1048576.0, 2) AS estimated_mb FROM public.app_users WHERE photo_uri LIKE 'data:image/%'$sql$);
    END IF;

    IF COALESCE(array_length(v_parts, 1), 0) > 0 THEN
        EXECUTE 'CREATE OR REPLACE VIEW public.v_audit_inline_base64_bloat WITH (security_invoker = true) AS ' || array_to_string(v_parts, ' UNION ALL ');
        EXECUTE 'REVOKE ALL ON public.v_audit_inline_base64_bloat FROM PUBLIC, anon, authenticated';
    END IF;

    -- ANALYZE sur toutes les tables existantes
    FOREACH v_tbl IN ARRAY v_tables LOOP
        IF to_regclass('public.' || v_tbl) IS NOT NULL THEN
            EXECUTE 'ANALYZE public.' || quote_ident(v_tbl);
        END IF;
    END LOOP;
END
$konab$;
