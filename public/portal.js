(() => {
  'use strict';

  const DEFAULT_SB_URL = 'https://hckbvflqimqanmmohxyc.supabase.co';
  const DEFAULT_SB_KEY = 'sb_publishable_2LVEwSPh-tzxDyBy1KPXJQ_b_2qUIs3';

  const STORAGE_KEYS = {
    clientOrders: 'konab_web_client_orders_v1',
    customerInfo: 'konab_web_customer_info_v1',
    seenReceipts: 'konab_web_seen_receipts_v1'
  };

  const MIRROR_TYPES = {
    table: '__CLOUD_RESTO_TABLE__',
    menuDay: '__CLOUD_RESTO_MENU_DAY__'
  };

  const PRESET_IMAGE_MAP = {
    img_prod_resto: '/assets/presets/img_prod_resto.jpg',
    img_prod_boisson: '/assets/presets/img_prod_boisson.jpg',
    img_prod_epicerie: '/assets/presets/img_prod_epicerie.jpg',
    img_prod_mode: '/assets/presets/img_prod_mode.jpg',
    img_prod_quincaillerie: '/assets/presets/img_prod_quincaillerie.jpg',
    burkina_ouaga_market: '/assets/presets/burkina_ouaga_market.jpg',
    burkina_commerce_faso: '/assets/presets/burkina_commerce_faso.jpg',
    burkina_bobo_dioulasso: '/assets/presets/burkina_bobo_dioulasso.jpg'
  };

  const RESTAURANT_KEYWORDS = [
    'restaurant', 'restaur', 'cantine', 'maquis', 'bistro', 'grill',
    'café', 'cafe', 'snack', 'fast food', 'fast-food', 'kiosque', 'traiteur', 'bar'
  ];

  const VALIDATED_STATUSES = ['SENT_TO_KITCHEN', 'IN_PREPARATION', 'READY', 'SERVED', 'PAID'];

  const ORDER_STATUS_META = {
    PENDING_MANAGER: {
      label: 'En attente de validation',
      badge: 'TRANSMISE',
      hint: 'Votre commande a bien été envoyée au restaurant. En attente de validation...'
    },
    SENT_TO_KITCHEN: {
      label: 'Validée & transmise en cuisine',
      badge: 'VALIDÉE',
      hint: 'Le restaurant a validé votre commande ! Votre reçu officiel est disponible.'
    },
    IN_PREPARATION: {
      label: 'En cours de préparation',
      badge: 'EN CUISINE',
      hint: 'Le chef prépare votre commande en ce moment.'
    },
    READY: {
      label: 'Prête à servir !',
      badge: 'PRÊTE',
      hint: 'Votre commande est prête ! Le serveur arrive à votre table.'
    },
    SERVED: {
      label: 'Servie à table',
      badge: 'SERVIE',
      hint: 'Bon appétit ! Merci de votre confiance.'
    },
    PAID: {
      label: 'Encaissée & Clôturée',
      badge: 'PAYÉE',
      hint: 'Paiement confirmé. Merci et à très bientôt !'
    },
    CANCELLED: {
      label: 'Annulée',
      badge: 'ANNULÉE',
      hint: 'Cette commande a été annulée par le restaurant.'
    }
  };

  const state = {
    activeTab: 'menu', // 'menu' | 'orders' | 'directory'
    sbUrl: DEFAULT_SB_URL,
    sbKey: DEFAULT_SB_KEY,
    shopId: 0,
    shopNameParam: '',
    tableName: 'Table 1',
    bundle: null,
    menuScope: 'day', // 'day' | 'all'
    menuCategory: 'Tous',
    menuSearch: '',
    cart: {}, // { [productIdStr]: { product, qty, note } }
    detailDish: null,
    detailQty: 1,
    trackedOrders: [],
    shopsById: {},
    activeReceiptOrder: null,
    directory: [],
    dirSearch: '',
    pollTimer: null
  };

  // --- UTILITIES ---

  function esc(val) {
    return String(val == null ? '' : val)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatMoney(amount, currency = 'FCFA') {
    const num = Math.round(Number(amount) || 0);
    return `${num.toLocaleString('fr-FR')} ${currency || 'FCFA'}`;
  }

  function formatDateTime(ts) {
    try {
      const d = new Date(Number(ts) || Date.now());
      return d.toLocaleString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch (_) {
      return '';
    }
  }

  function normalizeShopName(raw) {
    return String(raw || '')
      .trim()
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]/g, '');
  }

  function isRestaurantBusinessType(type) {
    const clean = String(type || '').trim().toLowerCase();
    if (!clean) return false;
    return RESTAURANT_KEYWORDS.some((kw) => clean.includes(kw));
  }

  function computeMonogram(name) {
    const words = String(name || '').trim().split(/\s+/).filter(Boolean);
    if (words.length >= 2) return (words[0][0] + words[1][0]).toUpperCase();
    if (words.length === 1 && words[0].length >= 2) return words[0].slice(0, 2).toUpperCase();
    if (words.length === 1) return words[0][0].toUpperCase();
    return 'R';
  }

  function resolveSingleImageUrl(rawUri) {
    const clean = String(rawUri || '').trim();
    if (!clean || clean === 'null' || clean === 'undefined') return '';

    const strippedPreset = clean
      .replace(/^drawable:/i, '')
      .replace(/\.(jpg|jpeg|png|webp)$/i, '')
      .trim()
      .toLowerCase();
    if (PRESET_IMAGE_MAP[strippedPreset]) {
      return PRESET_IMAGE_MAP[strippedPreset];
    }

    if (clean.startsWith('https://') || clean.startsWith('http://') || clean.startsWith('data:image/')) {
      return clean;
    }

    const base = state.sbUrl.replace(/\/+$/, '');
    if (clean.startsWith('/storage/v1/')) return `${base}${clean}`;
    if (clean.startsWith('storage/v1/')) return `${base}/${clean}`;
    if (
      clean.startsWith('shop-logos/') ||
      clean.startsWith('shop-ads/') ||
      clean.startsWith('product-images/') ||
      clean.startsWith('couture/')
    ) {
      return `${base}/storage/v1/object/public/${clean}`;
    }

    return '';
  }

  function resolveCategoryFallbackImage(category, name, isRestaurantContext = true) {
    const combined = `${name || ''} ${category || ''}`.toLowerCase();
    const words = combined.split(/[\s,.-]+/).filter(Boolean);

    if (
      words.includes('eau') ||
      ['boisson', 'jus', 'bissap', 'café', 'cafe', 'thé', 'the', 'bière', 'biere', 'soda', 'gingembre', 'gnamakoudji', 'cocktail', 'bouteille', 'canette', 'sucrerie', 'vin'].some((k) => combined.includes(k))
    ) {
      return PRESET_IMAGE_MAP.img_prod_boisson;
    }
    if (
      ['plat', 'grillade', 'poulet', 'alloco', 'tô', 'brochette', 'omelette', 'pain', 'sandwich', 'riz gras', 'restaurant', 'boeuf', 'viande', 'repas', 'beignet', 'baguette', 'croissant', 'gâteau', 'gateau', 'boulangerie', 'pâtisserie', 'boucherie', 'mouton', 'saucisse', 'poisson', 'carpe', 'capitaine', 'tilapia', 'crevette', 'burger', 'pizza', 'tacos', 'chawarma', 'salade', 'soupe', 'attieke', 'attiéké'].some((k) => combined.includes(k))
    ) {
      return PRESET_IMAGE_MAP.img_prod_resto;
    }
    if (
      ['vêtement', 'chaussure', 'mode', 't-shirt', 'chemise', 'pantalon', 'robe', 'pagne', 'sac', 'couture', 'bijou'].some((k) => combined.includes(k))
    ) {
      return PRESET_IMAGE_MAP.img_prod_mode;
    }
    if (
      ['quincaillerie', 'outil', 'ciment', 'ampoule', 'smartphone', 'téléphone', 'chargeur', 'moto', 'voiture'].some((k) => combined.includes(k))
    ) {
      return PRESET_IMAGE_MAP.img_prod_quincaillerie;
    }
    if (
      ['alimentation', 'épicerie', 'riz', 'huile', 'savon', 'sucre', 'lait', 'pâtes', 'farine', 'tomate'].some((k) => combined.includes(k))
    ) {
      return PRESET_IMAGE_MAP.img_prod_epicerie;
    }

    return isRestaurantContext ? PRESET_IMAGE_MAP.img_prod_resto : PRESET_IMAGE_MAP.img_prod_epicerie;
  }

  function resolveWebImageUrl(imageUri, galleryJson, fallbackCategory = '', fallbackName = '', allowFallback = false) {
    const primary = resolveSingleImageUrl(imageUri);
    if (primary) return primary;
    if (galleryJson) {
      const arr = parseJsonSafe(galleryJson, []);
      if (Array.isArray(arr)) {
        for (const item of arr) {
          const hit = resolveSingleImageUrl(item);
          if (hit) return hit;
        }
      }
    }
    return allowFallback ? resolveCategoryFallbackImage(fallbackCategory, fallbackName, true) : '';
  }

  function resolveDishGalleryUrls(product) {
    const list = [];
    const seen = new Set();
    const pushUrl = (raw) => {
      const resolved = resolveSingleImageUrl(raw);
      if (resolved && !seen.has(resolved)) {
        seen.add(resolved);
        list.push(resolved);
      }
    };
    pushUrl(product.image_uri);
    const arr = parseJsonSafe(product.gallery_images_json, []);
    if (Array.isArray(arr)) arr.forEach(pushUrl);
    if (!list.length) {
      list.push(resolveCategoryFallbackImage(product.category, product.name, true));
    }
    return list;
  }

  function showToast(msg, type = 'info') {
    const el = document.getElementById('toast');
    if (!el) return;
    el.textContent = msg;
    el.className = `toast no-print ${type}`;
    el.classList.remove('hidden');
    clearTimeout(showToast._timer);
    showToast._timer = setTimeout(() => el.classList.add('hidden'), 3800);
  }

  function elapsedLabel(ts) {
    const diffMin = Math.max(0, Math.floor((Date.now() - (Number(ts) || Date.now())) / 60000));
    if (diffMin < 1) return "À l'instant";
    if (diffMin < 60) return `Il y a ${diffMin} min`;
    const h = Math.floor(diffMin / 60);
    const m = String(diffMin % 60).padStart(2, '0');
    return `Il y a ${h}h${m}`;
  }

  function parseJsonSafe(raw, fallback) {
    if (raw == null || raw === '') return fallback;
    if (typeof raw === 'object') return raw;
    try {
      return JSON.parse(raw);
    } catch (_) {
      return fallback;
    }
  }

  function parseOrderItems(row) {
    const raw = row.items_json ?? row.itemsJson ?? row.items;
    const arr = parseJsonSafe(raw, []);
    if (!Array.isArray(arr)) return [];
    return arr.map((it) => ({
      product_id: Number(it.product_id ?? it.productId ?? 0),
      product_name: String(it.product_name ?? it.productName ?? 'Plat').trim(),
      unit_price: Number(it.unit_price ?? it.unitPrice ?? 0),
      quantity: Math.max(1, Number(it.quantity ?? 1)),
      note: String(it.note ?? '').trim()
    }));
  }

  // BigInt-safe scopedCloudId match
  function scopedCloudIdBigInt(shopId, localId) {
    try {
      const sid = BigInt(shopId || 0);
      const lid = BigInt(localId || 0);
      if (lid <= 0n || sid <= 1n || lid >= 1000000000000n) return lid;
      const absSid = sid < 0n ? -sid : sid;
      const prefix = (absSid % 8000000000n) || 1n;
      return prefix * 1000000n + lid;
    } catch (_) {
      return 0n;
    }
  }

  function productMatchesMenuDay(productId, menuIdsSet, shopIds) {
    const pStr = String(productId);
    if (menuIdsSet.has(pStr)) return true;
    try {
      const pidBig = BigInt(productId);
      for (const sid of shopIds) {
        if (Number(sid) > 1 && menuIdsSet.has(String(scopedCloudIdBigInt(sid, pidBig)))) {
          return true;
        }
      }
      if (pidBig >= 1000000000000n) {
        const raw = pidBig % 1000000n;
        if (menuIdsSet.has(String(raw))) {
          for (const sid of shopIds) {
            if (Number(sid) > 1 && scopedCloudIdBigInt(sid, raw) === pidBig) return true;
          }
        }
      }
    } catch (_) {}
    return false;
  }

  // --- SUPABASE PUBLIC CLIENT (STRICTLY READ PUBLIC MENUS + CLIENT ORDERS) ---

  async function sbFetch(path, options = {}) {
    const url = `${state.sbUrl}/rest/v1/${path}`;
    const headers = {
      apikey: state.sbKey,
      Authorization: `Bearer ${state.sbKey}`,
      Accept: 'application/json',
      ...(options.headers || {})
    };
    if (options.body && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }
    const resp = await fetch(url, { ...options, headers });
    const text = await resp.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch (_) {
      json = text;
    }
    return { ok: resp.ok, status: resp.status, data: json, raw: text };
  }

  async function sbGet(path) {
    const res = await sbFetch(path, { method: 'GET' });
    if (!res.ok) {
      const msg = res.data?.message || res.raw || `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return Array.isArray(res.data) ? res.data : [];
  }

  async function sbUpsert(table, rows, onConflict = 'id') {
    const payload = Array.isArray(rows) ? rows : [rows];
    const res = await sbFetch(`${table}?on_conflict=${encodeURIComponent(onConflict)}`, {
      method: 'POST',
      headers: { Prefer: 'resolution=merge-duplicates,return=representation' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      const msg = res.data?.message || res.raw || `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return Array.isArray(res.data) ? res.data : payload;
  }

  async function sbSafeGet(path) {
    try {
      return await sbGet(path);
    } catch (_) {
      return [];
    }
  }

  // --- SHOP SCOPING & MENU FETCHING (PUBLIC FIELDS ONLY — NO PINS / NO SECRETS) ---

  const PUBLIC_SHOP_SELECT = 'id,name,business_type,slogan,city,address,phone,currency,logo_uri,updated_at';

  async function resolveShopScope(shopId, fallbackName) {
    const allShops = await sbSafeGet(
      `shop_profile?select=${PUBLIC_SHOP_SELECT}&order=updated_at.desc&limit=200`
    );
    allShops.forEach((s) => {
      if (s && s.id != null) state.shopsById[String(s.id)] = s;
    });

    let primaryShop = null;
    const relatedIds = new Set();
    const numId = Number(shopId) || 0;
    if (numId > 1) relatedIds.add(numId);

    if (numId > 0) {
      primaryShop = allShops.find((s) => Number(s.id) === numId) || null;
    }
    const targetNorm = normalizeShopName(primaryShop?.name || fallbackName || '');
    if (targetNorm) {
      for (const s of allShops) {
        if (normalizeShopName(s.name) === targetNorm) {
          if (!primaryShop) primaryShop = s;
          if (Number(s.id) > 1) relatedIds.add(Number(s.id));
        }
      }
    }
    const distinctNames = new Set(allShops.map((s) => normalizeShopName(s.name) || `id_${s.id}`));
    const canonicalId = Number(primaryShop?.id) || (relatedIds.size ? Array.from(relatedIds)[0] : numId);
    const includeLegacy = distinctNames.size <= 1 || numId === 1;

    return {
      shop: primaryShop,
      canonicalId,
      relatedIds: Array.from(relatedIds),
      includeLegacy,
      allShops
    };
  }

  async function fetchScopedRows(table, scope, selectCols = '*', orderClause = null, limit = 600) {
    const orderPart = orderClause ? `&order=${orderClause}` : '';
    const ids = scope.relatedIds;
    if (!ids.length) {
      return await sbSafeGet(`${table}?select=${selectCols}${orderPart}&limit=${limit}`);
    }
    const inList = ids.join(',');
    const rows = await sbSafeGet(`${table}?shop_id=in.(${inList})&select=${selectCols}${orderPart}&limit=${limit}`);
    if (rows.length > 0 || !scope.includeLegacy) return rows;
    const all = await sbSafeGet(`${table}?select=${selectCols}${orderPart}&limit=${limit}`);
    return all.filter((r) => {
      const rSid = Number(r.shop_id || 0);
      return ids.includes(rSid) || rSid === 0 || rSid === 1;
    });
  }

  async function fetchMirrorPayloads(scope, markerType) {
    const encType = encodeURIComponent(markerType);
    const ids = scope.relatedIds;
    let rows = [];
    if (ids.length && !scope.includeLegacy) {
      rows = await sbSafeGet(`app_notifications?shop_id=in.(${ids.join(',')})&type=eq.${encType}&select=message&limit=400`);
    } else {
      rows = await sbSafeGet(`app_notifications?type=eq.${encType}&select=message&limit=400`);
    }
    const out = [];
    for (const row of rows) {
      const msg = String(row.message || '').trim();
      if (msg.startsWith('{')) {
        const parsed = parseJsonSafe(msg, null);
        if (parsed) out.push(parsed);
      }
    }
    return out;
  }

  function extractMenuDayIds(rows) {
    const out = new Set();
    const addVal = (v) => {
      if (v == null) return;
      if (typeof v === 'object') {
        const idv = v.product_id ?? v.id;
        if (Number(idv) > 0) out.add(String(idv));
        return;
      }
      const s = String(v).trim();
      if (/^\d+$/.test(s) && Number(s) > 0) out.add(s);
    };
    for (const row of rows) {
      for (const field of ['product_ids_json', 'menu_item_ids', 'dish_ids']) {
        const val = row[field];
        if (Array.isArray(val)) val.forEach(addVal);
        else if (typeof val === 'string' && val.trim()) {
          const parsed = parseJsonSafe(val, null);
          if (Array.isArray(parsed)) parsed.forEach(addVal);
          else val.split(',').forEach(addVal);
        }
      }
      if (Number(row.product_id) > 0) out.add(String(row.product_id));
      if (Number(row.menu_item_id) > 0) out.add(String(row.menu_item_id));
    }
    return out;
  }

  async function loadPublicRestaurantMenu(shopId, shopName) {
    const scope = await resolveShopScope(shopId, shopName);
    if (!scope.shop && scope.canonicalId <= 1) {
      throw new Error('Restaurant introuvable.');
    }

    const [productsRaw, menuDayRaw, menuItemsRaw, tablesRaw] = await Promise.all([
      fetchScopedRows('products', scope, 'id,shop_id,name,category,sell_price,is_available,image_uri,gallery_images_json', 'name.asc', 600),
      fetchScopedRows('restaurant_menu_day', scope, '*', null, 200),
      fetchScopedRows('restaurant_menu_items', scope, '*', 'id.asc', 400),
      fetchScopedRows('restaurant_tables', scope, 'id,shop_id,table_number,is_active', 'table_number.asc', 200)
    ]);

    let tables = tablesRaw;
    if (!tables.length) {
      tables = await fetchMirrorPayloads(scope, MIRROR_TYPES.table);
    }

    const menuDayIds = extractMenuDayIds(menuDayRaw);
    if (menuDayIds.size === 0) {
      const mirroredDay = await fetchMirrorPayloads(scope, MIRROR_TYPES.menuDay);
      extractMenuDayIds(mirroredDay).forEach((id) => menuDayIds.add(id));
    }

    const byName = new Map();
    for (const p of productsRaw) {
      const key = String(p.name || '').trim().toLowerCase();
      if (key && p.is_available !== false) byName.set(key, p);
    }
    for (let i = 0; i < menuItemsRaw.length; i++) {
      const mi = menuItemsRaw[i];
      if (Number(mi.product_id) > 0) {
        menuDayIds.add(String(mi.product_id));
      } else if (mi.name && mi.is_available !== false) {
        const key = String(mi.name).trim().toLowerCase();
        const itemId = Number(mi.id) || (1000000 + i);
        menuDayIds.add(String(itemId));
        if (!byName.has(key)) {
          byName.set(key, {
            id: itemId,
            name: String(mi.name).trim(),
            category: mi.category || 'Plats',
            sell_price: Number(mi.sell_price ?? mi.price ?? 0),
            is_available: true,
            image_uri: mi.image_uri || '',
            gallery_images_json: '[]'
          });
        }
      }
    }

    const allProducts = Array.from(byName.values());
    const shopIds = scope.relatedIds.length ? scope.relatedIds : [scope.canonicalId];
    const menuDayProducts = menuDayIds.size > 0
      ? allProducts.filter((p) => productMatchesMenuDay(p.id, menuDayIds, shopIds))
      : [];

    state.bundle = {
      shop: scope.shop || { id: scope.canonicalId, name: shopName || 'Restaurant', currency: 'FCFA' },
      shopId: scope.canonicalId,
      relatedIds: shopIds,
      allProducts,
      menuDayProducts: menuDayProducts.length ? menuDayProducts : allProducts,
      hasExplicitMenuDay: menuDayProducts.length > 0,
      tables
    };
    state.menuScope = menuDayProducts.length > 0 ? 'day' : 'all';
    state.menuCategory = 'Tous';
  }

  // --- TRACKED CLIENT ORDERS & AUTOMATIC RECEIPT DELIVERY ---

  function getStoredOrderIds() {
    return parseJsonSafe(localStorage.getItem(STORAGE_KEYS.clientOrders), []);
  }

  function saveStoredOrderId(order) {
    const list = getStoredOrderIds().filter((x) => x.id !== order.id);
    list.unshift({
      id: order.id,
      shopId: order.shop_id,
      createdAt: order.created_at
    });
    const cutoff = Date.now() - 24 * 3600 * 1000;
    const fresh = list.filter((x) => (x.createdAt || 0) > cutoff).slice(0, 12);
    localStorage.setItem(STORAGE_KEYS.clientOrders, JSON.stringify(fresh));
  }

  function getSeenReceiptMap() {
    return parseJsonSafe(localStorage.getItem(STORAGE_KEYS.seenReceipts), {});
  }

  function markReceiptAutoDelivered(orderId, status) {
    const map = getSeenReceiptMap();
    map[orderId] = status;
    localStorage.setItem(STORAGE_KEYS.seenReceipts, JSON.stringify(map));
  }

  function isOrderValidated(status) {
    return VALIDATED_STATUSES.includes(String(status || '').toUpperCase());
  }

  function playValidationChime() {
    try {
      if (navigator.vibrate) navigator.vibrate([120, 60, 160]);
    } catch (_) {}
  }

  async function refreshTrackedOrders(opts = {}) {
    const { showAutoReceipt = true } = opts;
    const saved = getStoredOrderIds();
    if (!saved.length) {
      state.trackedOrders = [];
      updateOrdersNavBadge();
      renderTrackedOrders();
      return;
    }

    const idsFilter = saved.map((x) => `"${String(x.id).replace(/"/g, '')}"`).join(',');
    const rows = await sbSafeGet(
      `restaurant_orders?id=in.(${encodeURIComponent(idsFilter)})&select=*&order=created_at.desc`
    );

    const prevMap = new Map(state.trackedOrders.map((o) => [o.id, String(o.status || '')]));
    const seenMap = getSeenReceiptMap();
    state.trackedOrders = rows;
    updateOrdersNavBadge();
    renderTrackedOrders();

    // Detect newly validated or paid orders -> notify client & auto-send official receipt
    for (const order of rows) {
      const st = String(order.status || 'PENDING_MANAGER').toUpperCase();
      const prevSt = prevMap.get(order.id);

      if (isOrderValidated(st) && !seenMap[order.id]) {
        markReceiptAutoDelivered(order.id, st);
        playValidationChime();
        showToast(`Commande #${order.order_number} validée ! Votre reçu officiel est prêt.`, 'success');
        if (showAutoReceipt) {
          openReceiptModal(order);
        }
        break;
      } else if (prevSt && prevSt !== st) {
        const meta = ORDER_STATUS_META[st];
        if (meta) {
          showToast(`#${order.order_number} : ${meta.label}`, st === 'CANCELLED' ? 'error' : 'success');
        }
        if (state.activeReceiptOrder && state.activeReceiptOrder.id === order.id) {
          openReceiptModal(order);
        }
      }
    }
  }

  function updateOrdersNavBadge() {
    const badge = document.getElementById('nav-orders-badge');
    if (!badge) return;
    const activeCount = state.trackedOrders.filter((o) => !['PAID', 'CANCELLED'].includes(String(o.status || '').toUpperCase())).length;
    const totalCount = state.trackedOrders.length;
    if (totalCount > 0) {
      badge.textContent = String(activeCount > 0 ? activeCount : totalCount);
      badge.classList.remove('hidden');
    } else {
      badge.classList.add('hidden');
    }
  }

  function buildOrderTrackerCardHtml(order, currency) {
    const st = String(order.status || 'PENDING_MANAGER').toUpperCase();
    const meta = ORDER_STATUS_META[st] || ORDER_STATUS_META.PENDING_MANAGER;
    const stepOrder = ['PENDING_MANAGER', 'SENT_TO_KITCHEN', 'IN_PREPARATION', 'READY', 'SERVED'];
    const labels = ['Reçue', 'Validée', 'Prépa.', 'Prête', 'Servie'];
    const curIdx = st === 'PAID' ? 5 : stepOrder.indexOf(st);
    const items = parseOrderItems(order);
    const validated = isOrderValidated(st);
    const shopInfo = state.shopsById[String(order.shop_id)] || state.bundle?.shop;
    const shopLabel = shopInfo?.name || 'Restaurant';

    return `
      <div class="tracker-card ${st === 'PENDING_MANAGER' ? 'is-pending' : ''}">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; gap:8px;">
          <div>
            <div class="tracker-live-pill">
              <span class="live-dot"></span>
              <span>Suivi en direct &bull; ${esc(shopLabel)} &bull; ${esc(order.table_name)}</span>
            </div>
            <div style="font-size:17px; font-weight:900; color:var(--navy-deep); margin-top:2px;">
              #${esc(order.order_number)} &bull; ${esc(formatMoney(order.total_amount, currency))}
            </div>
            <div style="font-size:11.5px; color:var(--text-muted); margin-top:2px;">
              ${esc(order.customer_name || 'Client')} &bull; ${esc(elapsedLabel(order.created_at))}
            </div>
          </div>
          <span class="status-badge status-${esc(st)}">${esc(meta.badge)}</span>
        </div>

        <div style="font-size:12.5px; color:var(--text); margin-top:8px; font-weight:600;">
          ${esc(items.map((i) => `${i.quantity}× ${i.product_name}`).join(', '))}
        </div>
        <div style="font-size:12px; color:#065F46; margin-top:4px; font-weight:700;">
          ${esc(meta.hint)}
        </div>

        ${st === 'CANCELLED'
          ? ''
          : `<div class="steps-bar">
              ${labels.map((lbl, idx) => {
                const cls = idx < curIdx ? 'done' : (idx === curIdx ? 'current' : '');
                return `<div class="step-dot ${cls}"><div class="step-pill"></div>${lbl}</div>`;
              }).join('')}
            </div>`
        }

        <div class="tracker-footer-actions">
          ${validated
            ? `<button type="button" class="btn btn-emerald" style="flex:1; min-height:42px; font-size:13px;" data-open-receipt="${esc(order.id)}">
                &#129534; Voir / Télécharger mon Reçu
              </button>`
            : `<button type="button" class="btn btn-outline" style="flex:1; min-height:40px; font-size:12.5px;" data-poll-now="1">
                &#8635; Actualiser le statut
              </button>`}
        </div>
      </div>
    `;
  }

  function bindTrackerCardActions(rootEl) {
    if (!rootEl) return;
    rootEl.querySelectorAll('[data-open-receipt]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const order = state.trackedOrders.find((x) => x.id === btn.dataset.openReceipt);
        if (order) openReceiptModal(order);
      });
    });
    rootEl.querySelectorAll('[data-poll-now]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        await refreshTrackedOrders({ showAutoReceipt: true });
        showToast('Statut actualisé.', 'info');
      });
    });
  }

  function renderTrackedOrders() {
    const inlineContainer = document.getElementById('active-order-tracker');
    const fullContainer = document.getElementById('orders-full-list');
    const currency = state.bundle?.shop?.currency || 'FCFA';

    const menuOrders = state.shopId
      ? state.trackedOrders.filter((o) => Number(o.shop_id) === Number(state.shopId))
      : state.trackedOrders;

    if (inlineContainer) {
      if (!menuOrders.length) {
        inlineContainer.innerHTML = '';
      } else {
        inlineContainer.innerHTML = buildOrderTrackerCardHtml(menuOrders[0], currency);
        bindTrackerCardActions(inlineContainer);
      }
    }

    if (fullContainer) {
      if (!state.trackedOrders.length) {
        fullContainer.innerHTML = `
          <div class="empty-state">
            <p style="margin:0 0 12px; font-weight:700;">Aucune commande passée depuis cet appareil pour l'instant.</p>
            <button type="button" class="btn btn-primary" id="btn-orders-go-menu">Consulter le Menu</button>
          </div>
        `;
        const goBtn = document.getElementById('btn-orders-go-menu');
        if (goBtn) goBtn.addEventListener('click', () => switchTab('menu'));
      } else {
        fullContainer.innerHTML = state.trackedOrders
          .map((ord) => buildOrderTrackerCardHtml(ord, currency))
          .join('');
        bindTrackerCardActions(fullContainer);
      }
    }
  }

  // --- OFFICIAL DIGITAL RECEIPT MODAL + PNG DOWNLOAD ---

  function openReceiptModal(order) {
    state.activeReceiptOrder = order;
    const shop = state.shopsById[String(order.shop_id)] || state.bundle?.shop || { name: 'Restaurant', currency: 'FCFA' };
    const currency = shop.currency || 'FCFA';
    const items = parseOrderItems(order);
    const st = String(order.status || 'SENT_TO_KITCHEN').toUpperCase();
    const logoUrl = resolveWebImageUrl(shop.logo_uri);
    const badgeText = st === 'PAID'
      ? '✓ REÇU OFFICIEL • PAYÉ'
      : '✓ COMMANDE VALIDÉE PAR LE RESTAURANT';

    const paper = document.getElementById('receipt-paper');
    paper.innerHTML = `
      <div class="receipt-header">
        ${logoUrl ? `<img src="${esc(logoUrl)}" class="receipt-logo" alt="${esc(shop.name)}" onerror="this.style.display='none'">` : ''}
        <h4 class="receipt-shop-name">${esc(shop.name || 'Restaurant')}</h4>
        ${shop.slogan ? `<div style="font-size:11.5px; color:var(--text-muted);">${esc(shop.slogan)}</div>` : ''}
        ${[shop.city, shop.address, shop.phone].filter(Boolean).length
          ? `<div style="font-size:11px; color:var(--text-light); margin-top:2px;">${esc([shop.city, shop.address, shop.phone].filter(Boolean).join(' • '))}</div>`
          : ''}
        <div class="receipt-validated-pill">${badgeText}</div>
      </div>

      <div class="receipt-meta-grid">
        <div><span>N° Commande</span><strong>#${esc(order.order_number)}</strong></div>
        <div><span>Table</span><strong>${esc(order.table_name)}</strong></div>
        <div><span>Client</span><strong>${esc(order.customer_name || 'Client')}</strong></div>
        <div><span>Date / Heure</span><strong>${esc(formatDateTime(order.updated_at || order.created_at))}</strong></div>
        ${order.assigned_to_kitchen_by ? `<div><span>Validé par</span><strong>${esc(order.assigned_to_kitchen_by)}</strong></div>` : ''}
        <div><span>Statut</span><strong>${esc((ORDER_STATUS_META[st] || {}).label || st)}</strong></div>
      </div>

      <table class="receipt-items-table">
        <tbody>
          ${items.map((it) => `
            <tr>
              <td>
                <strong>${it.quantity}&times;</strong> ${esc(it.product_name)}
                ${it.note ? `<div style="font-size:11px; color:var(--terracotta-dark);">${esc(it.note)}</div>` : ''}
              </td>
              <td>${esc(formatMoney(it.unit_price * it.quantity, currency))}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>

      ${order.notes ? `<div style="font-size:11.5px; background:#FFF7ED; padding:6px 9px; border-radius:8px; margin-bottom:10px; color:#9A3412;">Note : ${esc(order.notes)}</div>` : ''}

      <div class="receipt-total-row">
        <span>TOTAL</span>
        <span style="color:#065F46;">${esc(formatMoney(order.total_amount, currency))}</span>
      </div>

      <div class="receipt-foot">
        Merci pour votre visite ! &bull; Reçu généré par Konab POS
      </div>
    `;

    document.getElementById('modal-receipt').classList.remove('hidden');
  }

  function downloadReceiptAsPng() {
    const order = state.activeReceiptOrder;
    if (!order) return;
    const shop = state.shopsById[String(order.shop_id)] || state.bundle?.shop || { name: 'Restaurant', currency: 'FCFA' };
    const currency = shop.currency || 'FCFA';
    const items = parseOrderItems(order);

    const width = 760;
    const lineHeight = 42;
    const height = 430 + items.length * lineHeight + (order.notes ? 48 : 0);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');

    ctx.fillStyle = '#FFFDF8';
    ctx.fillRect(0, 0, width, height);

    ctx.fillStyle = '#0B132B';
    ctx.fillRect(0, 0, width, 118);

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 30px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(String(shop.name || 'Restaurant'), width / 2, 54);

    ctx.fillStyle = '#FDE68A';
    ctx.font = 'bold 18px sans-serif';
    const badgeTxt = String(order.status || '').toUpperCase() === 'PAID'
      ? 'REÇU OFFICIEL • PAYÉ'
      : 'COMMANDE VALIDÉE PAR LE RESTAURANT';
    ctx.fillText(badgeTxt, width / 2, 90);

    ctx.textAlign = 'left';
    ctx.fillStyle = '#334155';
    ctx.font = '18px sans-serif';
    let y = 160;
    ctx.fillText(`Commande : #${order.order_number}   |   Table : ${order.table_name}`, 40, y);
    y += 30;
    ctx.fillText(`Client : ${order.customer_name || 'Client'}   |   ${formatDateTime(order.updated_at || order.created_at)}`, 40, y);

    y += 22;
    ctx.strokeStyle = '#CBD5E1';
    ctx.setLineDash([8, 6]);
    ctx.beginPath();
    ctx.moveTo(40, y);
    ctx.lineTo(width - 40, y);
    ctx.stroke();
    ctx.setLineDash([]);

    y += 36;
    ctx.fillStyle = '#0F172A';
    for (const it of items) {
      ctx.font = 'bold 20px sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(`${it.quantity}x ${it.product_name}`.slice(0, 38), 40, y);
      ctx.textAlign = 'right';
      ctx.fillText(formatMoney(it.unit_price * it.quantity, currency), width - 40, y);
      y += lineHeight;
    }

    if (order.notes) {
      ctx.textAlign = 'left';
      ctx.fillStyle = '#9A3412';
      ctx.font = '16px sans-serif';
      ctx.fillText(`Note : ${order.notes}`.slice(0, 62), 40, y);
      y += 36;
    }

    ctx.strokeStyle = '#0F172A';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.moveTo(40, y);
    ctx.lineTo(width - 40, y);
    ctx.stroke();

    y += 44;
    ctx.fillStyle = '#065F46';
    ctx.font = 'bold 28px sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('TOTAL', 40, y);
    ctx.textAlign = 'right';
    ctx.fillText(formatMoney(order.total_amount, currency), width - 40, y);

    ctx.textAlign = 'center';
    ctx.fillStyle = '#64748B';
    ctx.font = '16px sans-serif';
    ctx.fillText('Merci pour votre visite ! • Konab POS', width / 2, height - 30);

    const link = document.createElement('a');
    link.download = `Recu-${order.order_number || 'Konab'}.png`;
    link.href = canvas.toDataURL('image/png');
    link.click();
  }

  // --- RENDER: CUSTOMER MENU ---

  function renderMenuHero() {
    const container = document.getElementById('menu-hero-container');
    if (!container || !state.bundle) return;
    const shop = state.bundle.shop;
    const name = shop.name || state.shopNameParam || 'Restaurant';
    const logoUrl = resolveWebImageUrl(shop.logo_uri);
    const meta = [shop.city, shop.address, shop.phone].filter(Boolean).join(' • ') || 'Commande directe à table sans attente';
    const countLabel = `${state.bundle.allProducts.length} plat${state.bundle.allProducts.length > 1 ? 's' : ''} disponibles`;

    const navSub = document.getElementById('nav-sub-label');
    if (navSub) navSub.textContent = name;

    container.innerHTML = `
      <div class="resto-hero">
        <img src="${PRESET_IMAGE_MAP.img_prod_resto}" class="resto-hero-bg" alt="" />
        <div class="resto-hero-scrim"></div>
        <div class="resto-hero-inner">
          <div class="resto-hero-body">
            ${logoUrl
              ? `<img src="${esc(logoUrl)}" class="resto-logo" alt="${esc(name)}" onerror="this.onerror=null; this.src='${PRESET_IMAGE_MAP.img_prod_resto}';">`
              : `<div class="resto-monogram">${esc(computeMonogram(name))}</div>`}
            <div class="resto-hero-info">
              <span class="resto-eyebrow">&#9679; Menu Digital Live</span>
              <h1>${esc(name)}</h1>
              ${shop.slogan ? `<p class="resto-slogan">${esc(shop.slogan)}</p>` : ''}
              <p class="resto-meta">${esc(meta)}</p>
            </div>
          </div>
          <div class="resto-hero-bar">
            <div class="hero-badges-row">
              <span class="hero-count-pill">${esc(countLabel)}</span>
            </div>
            <button type="button" class="table-select-chip" id="btn-change-table">
              <span>&#127869; ${esc(state.tableName)}</span>
              <span>&#9662;</span>
            </button>
          </div>
        </div>
      </div>
    `;
    const changeBtn = document.getElementById('btn-change-table');
    if (changeBtn) {
      changeBtn.addEventListener('click', openTablePickerModal);
    }
  }

  function renderMenuControlsAndGrid() {
    if (!state.bundle) return;
    const scopeRow = document.getElementById('menu-scope-chips');
    const catRow = document.getElementById('menu-category-chips');
    const grid = document.getElementById('menu-grid');
    const currency = state.bundle.shop.currency || 'FCFA';

    if (scopeRow) {
      if (state.bundle.hasExplicitMenuDay) {
        scopeRow.classList.remove('hidden');
        scopeRow.querySelectorAll('[data-scope]').forEach((btn) => {
          btn.classList.toggle('active', btn.dataset.scope === state.menuScope);
        });
      } else {
        scopeRow.classList.add('hidden');
      }
    }

    const baseList = state.menuScope === 'day'
      ? state.bundle.menuDayProducts
      : state.bundle.allProducts;

    const categories = ['Tous', ...Array.from(new Set(baseList.map((p) => (p.category || 'Plats').trim()))).sort()];
    if (!categories.includes(state.menuCategory)) state.menuCategory = 'Tous';

    if (catRow) {
      catRow.innerHTML = categories.map((cat) => `
        <button type="button" class="chip ${cat === state.menuCategory ? 'active' : ''}" data-cat="${esc(cat)}">${esc(cat)}</button>
      `).join('');
      catRow.querySelectorAll('[data-cat]').forEach((btn) => {
        btn.addEventListener('click', () => {
          state.menuCategory = btn.dataset.cat;
          renderMenuControlsAndGrid();
        });
      });
    }

    const q = state.menuSearch.trim().toLowerCase();
    const filtered = baseList.filter((p) => {
      const catOk = state.menuCategory === 'Tous' || (p.category || 'Plats').trim() === state.menuCategory;
      const qOk = !q || String(p.name || '').toLowerCase().includes(q) || String(p.category || '').toLowerCase().includes(q);
      return catOk && qOk;
    });

    if (!filtered.length) {
      grid.innerHTML = `<div class="empty-state" style="grid-column:1/-1;">Aucun plat disponible pour cette sélection.</div>`;
    } else {
      grid.innerHTML = filtered.map((p) => {
        const pid = String(p.id);
        const cartEntry = state.cart[pid];
        const qty = cartEntry?.qty || 0;
        const note = cartEntry?.note || '';
        const fallbackImg = resolveCategoryFallbackImage(p.category, p.name, true);
        const imgUrl = resolveWebImageUrl(p.image_uri, p.gallery_images_json, p.category, p.name, true) || fallbackImg;
        const galleryCount = resolveDishGalleryUrls(p).length;
        const isDay = state.bundle.menuDayProducts.some((m) => String(m.id) === pid);
        const badgeText = galleryCount > 1
          ? `${galleryCount} photos`
          : (isDay && state.bundle.hasExplicitMenuDay ? 'Menu du Jour' : '');

        return `
          <article class="dish-card ${qty > 0 ? 'in-cart' : ''}">
            <div class="dish-media" data-open-dish="${esc(pid)}">
              <img src="${esc(imgUrl)}" alt="${esc(p.name)}" loading="lazy" onerror="this.onerror=null; this.src='${esc(fallbackImg)}';" />
              <div class="dish-media-scrim"></div>
              ${badgeText ? `<span class="dish-badge">${esc(badgeText)}</span>` : ''}
              ${qty > 0 ? `<span class="dish-qty-bubble">${qty}</span>` : ''}
              <span class="dish-price-pill">${esc(formatMoney(p.sell_price, currency))}</span>
            </div>
            <div class="dish-body">
              <span class="dish-cat">${esc(p.category || 'Menu du jour')}</span>
              <h3 class="dish-name" data-open-dish="${esc(pid)}">${esc(p.name)}</h3>
              ${note ? `<div class="dish-note-preview">Note : ${esc(note)}</div>` : ''}
              <div class="dish-footer">
                ${qty > 0
                  ? `<div class="stepper">
                      <button type="button" data-dec="${esc(pid)}" aria-label="Retirer">&minus;</button>
                      <span>${qty}</span>
                      <button type="button" data-inc="${esc(pid)}" aria-label="Ajouter">+</button>
                    </div>`
                  : `<button type="button" class="add-btn" data-inc="${esc(pid)}">+ Ajouter</button>`}
              </div>
            </div>
          </article>
        `;
      }).join('');

      grid.querySelectorAll('[data-open-dish]').forEach((el) => {
        el.addEventListener('click', () => {
          const dish = baseList.find((x) => String(x.id) === el.dataset.openDish);
          if (dish) openDishDetailModal(dish);
        });
      });
      grid.querySelectorAll('[data-inc]').forEach((btn) => {
        btn.addEventListener('click', (ev) => {
          ev.stopPropagation();
          updateCartQty(btn.dataset.inc, 1, baseList);
        });
      });
      grid.querySelectorAll('[data-dec]').forEach((btn) => {
        btn.addEventListener('click', (ev) => {
          ev.stopPropagation();
          updateCartQty(btn.dataset.dec, -1, baseList);
        });
      });
    }
    renderStickyCartBar();
  }

  function openDishDetailModal(dish) {
    state.detailDish = dish;
    const pid = String(dish.id);
    const existing = state.cart[pid];
    state.detailQty = existing?.qty || 1;
    const currency = state.bundle?.shop?.currency || 'FCFA';
    const gallery = resolveDishGalleryUrls(dish);
    const fallbackImg = resolveCategoryFallbackImage(dish.category, dish.name, true);

    const mainImg = document.getElementById('detail-main-img');
    mainImg.src = gallery[0] || fallbackImg;
    mainImg.alt = dish.name || 'Plat';
    mainImg.onerror = () => { mainImg.onerror = null; mainImg.src = fallbackImg; };

    document.getElementById('detail-price-pill').textContent = formatMoney(dish.sell_price, currency);
    document.getElementById('detail-category').textContent = dish.category || 'Menu du jour';
    document.getElementById('detail-title').textContent = dish.name || 'Plat';
    document.getElementById('detail-note-input').value = existing?.note || '';

    const strip = document.getElementById('detail-gallery-strip');
    if (gallery.length > 1) {
      strip.classList.remove('hidden');
      strip.innerHTML = gallery.map((url, idx) => `
        <img src="${esc(url)}" class="detail-thumb ${idx === 0 ? 'active' : ''}" data-thumb-idx="${idx}" alt="" onerror="this.onerror=null; this.src='${esc(fallbackImg)}';" />
      `).join('');
      strip.querySelectorAll('.detail-thumb').forEach((thumb) => {
        thumb.addEventListener('click', () => {
          const i = Number(thumb.dataset.thumbIdx);
          mainImg.src = gallery[i] || fallbackImg;
          strip.querySelectorAll('.detail-thumb').forEach((t) => t.classList.remove('active'));
          thumb.classList.add('active');
        });
      });
    } else {
      strip.classList.add('hidden');
      strip.innerHTML = '';
    }

    syncDetailModalCta();
    document.getElementById('modal-dish-detail').classList.remove('hidden');
  }

  function syncDetailModalCta() {
    if (!state.detailDish) return;
    const currency = state.bundle?.shop?.currency || 'FCFA';
    document.getElementById('detail-qty-val').textContent = String(state.detailQty);
    const total = state.detailQty * (Number(state.detailDish.sell_price) || 0);
    document.getElementById('detail-confirm-btn').textContent = `Ajouter • ${formatMoney(total, currency)}`;
  }

  function updateCartQty(pid, delta, sourceList) {
    const product = (sourceList || []).find((p) => String(p.id) === String(pid)) || state.cart[pid]?.product;
    if (!product) return;
    const prev = state.cart[pid]?.qty || 0;
    const next = prev + delta;
    if (next <= 0) {
      delete state.cart[pid];
    } else {
      state.cart[pid] = {
        product,
        qty: next,
        note: state.cart[pid]?.note || ''
      };
    }
    renderMenuControlsAndGrid();
  }

  function renderStickyCartBar() {
    const bar = document.getElementById('sticky-cart-bar');
    if (!bar) return;
    const items = Object.values(state.cart);
    if (state.activeTab !== 'menu' || !items.length) {
      bar.classList.add('hidden');
      return;
    }
    const totalCount = items.reduce((s, it) => s + it.qty, 0);
    const totalAmount = items.reduce((s, it) => s + it.qty * (Number(it.product.sell_price) || 0), 0);
    const currency = state.bundle?.shop?.currency || 'FCFA';
    document.getElementById('cart-bar-count').textContent = `${totalCount}`;
    document.getElementById('cart-bar-total').textContent = formatMoney(totalAmount, currency);
    bar.classList.remove('hidden');
  }

  function openTablePickerModal() {
    const modal = document.getElementById('modal-table-picker');
    const listEl = document.getElementById('table-picker-list');
    const configured = (state.bundle?.tables || []).map((t) => t.table_number || t.tableNumber).filter(Boolean);
    const tableOptions = configured.length ? configured : ['Table 1', 'Table 2', 'Table 3', 'Table 4', 'Terrasse 1', 'À emporter'];
    if (!tableOptions.includes(state.tableName)) tableOptions.unshift(state.tableName);

    listEl.innerHTML = tableOptions.map((t) => `
      <button type="button" class="btn ${t === state.tableName ? 'btn-primary' : 'btn-outline'}" data-pick-table="${esc(t)}">
        ${esc(t)}
      </button>
    `).join('');
    listEl.querySelectorAll('[data-pick-table]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.tableName = btn.dataset.pickTable;
        modal.classList.add('hidden');
        renderMenuHero();
      });
    });
    modal.classList.remove('hidden');
  }

  function openCartModal() {
    const modal = document.getElementById('modal-cart');
    const listEl = document.getElementById('cart-items-list');
    const currency = state.bundle?.shop?.currency || 'FCFA';
    const entries = Object.values(state.cart);
    document.getElementById('cart-table-label').textContent = state.tableName;

    const total = entries.reduce((s, e) => s + e.qty * (Number(e.product.sell_price) || 0), 0);
    listEl.innerHTML = `
      ${entries.map((e) => `
        <div style="display:flex; justify-content:space-between; align-items:center; padding:9px 0; border-bottom:1px solid var(--border); gap:10px;">
          <div style="flex:1;">
            <div style="font-weight:800; font-size:14.5px;">${esc(e.product.name)}</div>
            <div style="font-size:12px; color:var(--text-muted);">${esc(formatMoney(e.product.sell_price, currency))} &times; ${e.qty}${e.note ? ` &bull; <em>${esc(e.note)}</em>` : ''}</div>
          </div>
          <div style="font-weight:900; font-size:14.5px; color:var(--terracotta-dark);">
            ${esc(formatMoney(e.qty * (Number(e.product.sell_price) || 0), currency))}
          </div>
        </div>
      `).join('')}
      <div style="display:flex; justify-content:space-between; padding:12px 0 4px; font-weight:900; font-size:17px;">
        <span>Total à payer</span>
        <span style="color:var(--emerald);">${esc(formatMoney(total, currency))}</span>
      </div>
    `;

    const savedCust = parseJsonSafe(localStorage.getItem(STORAGE_KEYS.customerInfo), {});
    if (savedCust.name && !document.getElementById('cust-name').value) {
      document.getElementById('cust-name').value = savedCust.name;
    }
    if (savedCust.phone && !document.getElementById('cust-phone').value) {
      document.getElementById('cust-phone').value = savedCust.phone;
    }
    modal.classList.remove('hidden');
  }

  async function submitCustomerOrder(e) {
    e.preventDefault();
    const entries = Object.values(state.cart);
    if (!entries.length || !state.bundle) return;

    const custName = document.getElementById('cust-name').value.trim() || 'Client Table';
    const custPhone = document.getElementById('cust-phone').value.trim();
    const notes = document.getElementById('cust-notes').value.trim();
    localStorage.setItem(STORAGE_KEYS.customerInfo, JSON.stringify({ name: custName, phone: custPhone }));

    const btn = document.getElementById('btn-submit-order');
    btn.disabled = true;
    btn.textContent = 'Envoi en cours...';

    try {
      const now = Date.now();
      const rand = Math.floor(10 + Math.random() * 89);
      const orderItems = entries.map((it) => ({
        product_id: Number(it.product.id) || 0,
        product_name: String(it.product.name || 'Plat').trim(),
        unit_price: Number(it.product.sell_price) || 0,
        quantity: it.qty,
        note: it.note || ''
      }));
      const totalAmount = orderItems.reduce((s, it) => s + it.unit_price * it.quantity, 0);
      const orderNum = `WEB-${String(now % 1000).padStart(3, '0')}`;
      const record = {
        id: `ORD-${state.bundle.shopId}-${now}-${rand}`,
        shop_id: Number(state.bundle.shopId),
        order_number: orderNum,
        table_name: state.tableName,
        customer_name: custName,
        customer_phone: custPhone,
        source: 'QR_TABLE',
        status: 'PENDING_MANAGER',
        items_json: JSON.stringify(orderItems),
        total_amount: totalAmount,
        notes,
        created_by_role: 'CLIENT_QR',
        created_by_name: custName,
        assigned_to_kitchen_by: '',
        created_at: now,
        updated_at: now
      };

      await sbUpsert('restaurant_orders', record, 'id');
      saveStoredOrderId(record);
      state.cart = {};
      document.getElementById('modal-cart').classList.add('hidden');
      document.getElementById('cust-notes').value = '';
      showToast(`Commande #${orderNum} envoyée ! Suivi en direct activé.`, 'success');
      renderMenuControlsAndGrid();
      await refreshTrackedOrders({ showAutoReceipt: false });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (err) {
      showToast(`Erreur d'envoi : ${err.message}`, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Envoyer ma commande au restaurant';
    }
  }

  // --- DIRECTORY ---

  async function loadDirectory(options = {}) {
    const { autoEnterSingle = false } = options;
    const grid = document.getElementById('dir-grid');
    if (grid) {
      grid.innerHTML = '<div class="empty-state" style="grid-column:1/-1;">Chargement des restaurants...</div>';
    }
    const shops = await sbSafeGet(
      `shop_profile?select=${PUBLIC_SHOP_SELECT}&order=updated_at.desc&limit=150`
    );
    shops.forEach((s) => {
      if (s && s.id != null) state.shopsById[String(s.id)] = s;
    });

    const restoByNorm = new Map();
    const allByNorm = new Map();
    for (const s of shops) {
      if (!s.name) continue;
      const key = normalizeShopName(s.name);
      if (!allByNorm.has(key)) allByNorm.set(key, s);
      if (isRestaurantBusinessType(s.business_type) && !restoByNorm.has(key)) {
        restoByNorm.set(key, s);
      }
    }
    state.directory = restoByNorm.size ? Array.from(restoByNorm.values()) : Array.from(allByNorm.values());
    renderDirectory();

    if (autoEnterSingle && state.directory.length === 1) {
      const only = state.directory[0];
      await openRestaurantMenuById(Number(only.id), only.name, { pushHistory: false });
      return true;
    }
    return false;
  }

  function renderDirectory() {
    const grid = document.getElementById('dir-grid');
    if (!grid) return;
    const q = state.dirSearch.trim().toLowerCase();
    const list = state.directory.filter((r) => {
      if (!q) return true;
      return [r.name, r.city, r.slogan, r.address].some((v) => String(v || '').toLowerCase().includes(q));
    });
    if (!list.length) {
      grid.innerHTML = '<div class="empty-state" style="grid-column:1/-1;">Aucun restaurant trouvé pour cette recherche.</div>';
      return;
    }
    grid.innerHTML = list.map((r) => {
      const logoUrl = resolveWebImageUrl(r.logo_uri);
      const coverUrl = resolveCategoryFallbackImage(r.business_type, r.name, isRestaurantBusinessType(r.business_type));
      return `
        <article class="card dir-card" data-open-resto="${esc(r.id)}" data-open-name="${esc(r.name)}">
          <div class="dir-cover">
            <img src="${esc(coverUrl)}" alt="${esc(r.name)}" loading="lazy" />
            <div class="dir-cover-scrim"></div>
            <span class="dir-sector-tag">${esc(r.business_type || 'Restaurant')}</span>
          </div>
          <div class="dir-card-body">
            <div class="dir-top">
              ${logoUrl
                ? `<img src="${esc(logoUrl)}" class="dir-logo" alt="${esc(r.name)}" onerror="this.onerror=null; this.src='${esc(coverUrl)}';">`
                : `<div class="dir-mono">${esc(computeMonogram(r.name))}</div>`}
              <div style="flex:1; min-width:0; padding-top:26px;">
                <h3 style="margin:0; font-size:17px; font-weight:900; line-height:1.2;">${esc(r.name)}</h3>
                <div style="font-size:12px; color:var(--text-muted); margin-top:2px;">${esc([r.city, r.phone].filter(Boolean).join(' • ') || r.business_type)}</div>
              </div>
            </div>
            ${r.slogan ? `<div style="font-size:12.5px; color:var(--terracotta-dark); font-weight:600;">${esc(r.slogan)}</div>` : ''}
            <div style="display:flex; gap:8px; margin-top:auto;">
              <button type="button" class="btn btn-primary btn-full" style="min-height:42px; font-size:13.5px;" data-open-menu-btn="${esc(r.id)}">
                Voir le Menu du Jour
              </button>
            </div>
          </div>
        </article>
      `;
    }).join('');

    grid.querySelectorAll('[data-open-menu-btn]').forEach((btn) => {
      btn.addEventListener('click', (ev) => {
        ev.stopPropagation();
        const card = btn.closest('[data-open-resto]');
        openRestaurantMenuById(Number(card.dataset.openResto), card.dataset.openName);
      });
    });
    grid.querySelectorAll('[data-open-resto]').forEach((card) => {
      card.addEventListener('click', () => {
        openRestaurantMenuById(Number(card.dataset.openResto), card.dataset.openName);
      });
    });
  }

  async function openRestaurantMenuById(shopId, shopName, opts = {}) {
    const { pushHistory = true } = opts;
    state.shopId = Number(shopId) || 0;
    state.shopNameParam = shopName || '';
    state.tableName = 'Table 1';
    state.cart = {};
    switchTab('menu');
    if (pushHistory) {
      const url = new URL(window.location.href);
      url.searchParams.set('s', String(state.shopId));
      url.searchParams.set('t', state.tableName);
      url.searchParams.delete('mode');
      window.history.replaceState({}, '', url.toString());
    }
    await bootMenuView();
  }

  // --- NAVIGATION & REAL-TIME POLLING ---

  function switchTab(tab) {
    state.activeTab = tab;
    document.getElementById('view-menu').classList.toggle('hidden', tab !== 'menu');
    document.getElementById('view-orders').classList.toggle('hidden', tab !== 'orders');
    document.getElementById('view-directory').classList.toggle('hidden', tab !== 'directory');

    document.getElementById('tab-btn-menu').classList.toggle('active', tab === 'menu');
    document.getElementById('tab-btn-orders').classList.toggle('active', tab === 'orders');
    document.getElementById('tab-btn-directory').classList.toggle('active', tab === 'directory');
    renderStickyCartBar();
  }

  async function bootMenuView() {
    const grid = document.getElementById('menu-grid');
    grid.innerHTML = '<div class="empty-state" style="grid-column:1/-1;">Chargement du Menu du Jour...</div>';
    try {
      await loadPublicRestaurantMenu(state.shopId, state.shopNameParam);
      renderMenuHero();
      renderMenuControlsAndGrid();
      await refreshTrackedOrders({ showAutoReceipt: true });
    } catch (err) {
      grid.innerHTML = `<div class="empty-state" style="grid-column:1/-1;">${esc(err.message)}<br><br><button type="button" class="btn btn-primary" id="btn-fallback-dir">Voir tous les restaurants</button></div>`;
      const fb = document.getElementById('btn-fallback-dir');
      if (fb) fb.addEventListener('click', () => switchTab('directory'));
    }
  }

  function startLivePolling() {
    clearInterval(state.pollTimer);
    state.pollTimer = setInterval(async () => {
      if (document.hidden) return;
      if (getStoredOrderIds().length) {
        await refreshTrackedOrders({ showAutoReceipt: true });
      }
    }, 4000);
  }

  async function bootApp() {
    const params = new URLSearchParams(window.location.search);
    const paramShopId = Number(params.get('s') || params.get('shopId') || 0);
    const paramShopName = (params.get('n') || params.get('shop') || '').trim();
    const paramTable = (params.get('t') || params.get('table') || 'Table 1').trim() || 'Table 1';
    const mode = (params.get('mode') || '').trim().toLowerCase();

    state.shopId = paramShopId;
    state.shopNameParam = paramShopName;
    state.tableName = paramTable;

    // Bind navigation
    document.getElementById('tab-btn-menu').addEventListener('click', async () => {
      if (state.bundle) {
        switchTab('menu');
      } else if (state.shopId > 0 || state.shopNameParam) {
        switchTab('menu');
        await bootMenuView();
      } else if (state.directory.length === 1) {
        const only = state.directory[0];
        await openRestaurantMenuById(Number(only.id), only.name, { pushHistory: false });
      } else {
        switchTab('directory');
        showToast('Choisissez d’abord un restaurant pour afficher son menu.');
      }
    });
    document.getElementById('tab-btn-orders').addEventListener('click', async () => {
      switchTab('orders');
      await refreshTrackedOrders({ showAutoReceipt: false });
    });
    document.getElementById('tab-btn-directory').addEventListener('click', async () => {
      switchTab('directory');
      if (!state.directory.length) await loadDirectory();
    });

    // Menu search & scope
    document.getElementById('menu-search').addEventListener('input', (ev) => {
      state.menuSearch = ev.target.value;
      renderMenuControlsAndGrid();
    });
    document.querySelectorAll('#menu-scope-chips [data-scope]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.menuScope = btn.dataset.scope;
        renderMenuControlsAndGrid();
      });
    });

    // Dish Detail Modal bindings
    document.getElementById('detail-qty-minus').addEventListener('click', () => {
      if (state.detailQty > 1) {
        state.detailQty -= 1;
        syncDetailModalCta();
      }
    });
    document.getElementById('detail-qty-plus').addEventListener('click', () => {
      state.detailQty += 1;
      syncDetailModalCta();
    });
    document.querySelectorAll('[data-quick-note]').forEach((chip) => {
      chip.addEventListener('click', () => {
        const inp = document.getElementById('detail-note-input');
        const txt = chip.dataset.quickNote;
        const cur = inp.value.trim();
        inp.value = cur ? `${cur}, ${txt}` : txt;
      });
    });
    document.getElementById('detail-confirm-btn').addEventListener('click', () => {
      if (!state.detailDish) return;
      const pid = String(state.detailDish.id);
      const note = document.getElementById('detail-note-input').value.trim();
      state.cart[pid] = {
        product: state.detailDish,
        qty: state.detailQty,
        note
      };
      document.getElementById('modal-dish-detail').classList.add('hidden');
      renderMenuControlsAndGrid();
      showToast(`${state.detailQty}× ${state.detailDish.name} ajouté au panier`, 'success');
    });

    // Orders, Directory & Receipt actions
    document.getElementById('dir-search').addEventListener('input', (ev) => {
      state.dirSearch = ev.target.value;
      renderDirectory();
    });
    document.getElementById('btn-refresh-directory').addEventListener('click', () => loadDirectory());
    document.getElementById('btn-refresh-orders').addEventListener('click', async () => {
      await refreshTrackedOrders({ showAutoReceipt: true });
      showToast('Commandes actualisées.', 'info');
    });
    document.getElementById('btn-download-receipt-png').addEventListener('click', downloadReceiptAsPng);
    document.getElementById('btn-print-receipt').addEventListener('click', () => window.print());

    // Cart modal
    document.getElementById('btn-open-cart').addEventListener('click', openCartModal);
    document.getElementById('cart-submit-form').addEventListener('submit', submitCustomerOrder);

    document.querySelectorAll('[data-close]').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.getElementById(btn.dataset.close)?.classList.add('hidden');
      });
    });
    document.querySelectorAll('.modal-backdrop').forEach((backdrop) => {
      backdrop.addEventListener('click', (ev) => {
        if (ev.target === backdrop) backdrop.classList.add('hidden');
      });
    });

    if (mode === 'orders') {
      switchTab('orders');
      await refreshTrackedOrders({ showAutoReceipt: false });
    } else if (paramShopId > 0 || paramShopName) {
      switchTab('menu');
      await bootMenuView();
    } else {
      switchTab('directory');
      await loadDirectory({ autoEnterSingle: mode !== 'directory' });
      await refreshTrackedOrders({ showAutoReceipt: true });
    }

    startLivePolling();
  }

  document.addEventListener('DOMContentLoaded', bootApp);
})();
