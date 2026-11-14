package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.local.entity.ShopProfileEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class ShopAdRecord(
    val id: String,
    val shopId: Long = 0L,
    val shopName: String,
    val businessType: String = "Boutique",
    val city: String = "",
    val phone: String = "",
    val whatsappPhone: String = "",
    val logoUri: String = "",
    val adTitle: String = "",
    val adDescription: String = "",
    val videoUri: String = "",
    val videoDurationSec: Int = 0,
    val ctaLabel: String = "Contacter",
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val deployerId: String = "",
    val isSynced: Boolean = false,
    val isFallbackShopOnly: Boolean = false
) {
    val hasVideo: Boolean
        get() = videoUri.isNotBlank()

    val effectiveTitle: String
        get() = adTitle.trim().ifBlank { shopName.trim().ifBlank { "Boutique KONAB" } }

    val effectiveDescription: String
        get() = adDescription.trim().ifBlank {
            val parts = listOfNotNull(
                businessType.trim().takeIf { it.isNotBlank() },
                city.trim().takeIf { it.isNotBlank() }
            )
            if (parts.isNotEmpty()) "${parts.joinToString(" • ")} — Découvrez nos offres sur KONAB POS"
            else "Boutique inscrite sur KONAB POS"
        }

    val effectiveWhatsapp: String
        get() = whatsappPhone.trim().ifBlank { phone.trim() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("shop_id", shopId)
        put("shop_name", shopName)
        put("business_type", businessType)
        put("city", city)
        put("phone", phone)
        put("whatsapp_phone", whatsappPhone)
        put("logo_uri", logoUri)
        put("ad_title", adTitle)
        put("ad_description", adDescription)
        put("video_uri", videoUri)
        put("video_duration_sec", videoDurationSec.coerceIn(0, ShopAdStorage.MAX_AD_VIDEO_DURATION_SEC))
        put("cta_label", ctaLabel)
        put("is_active", isActive)
        put("updated_at", updatedAt)
        put("deployer_id", deployerId)
        put("is_synced", isSynced)
        put("is_fallback_shop_only", isFallbackShopOnly)
    }

    companion object {
        fun fromJson(obj: JSONObject): ShopAdRecord {
            val rawDuration = obj.optInt("video_duration_sec", 0)
            return ShopAdRecord(
                id = obj.optString("id", "AD-${System.currentTimeMillis()}"),
                shopId = obj.optLong("shop_id", 0L),
                shopName = obj.optString("shop_name", "").trim(),
                businessType = obj.optString("business_type", "Boutique").ifBlank { "Boutique" },
                city = obj.optString("city", ""),
                phone = obj.optString("phone", ""),
                whatsappPhone = obj.optString("whatsapp_phone", ""),
                logoUri = obj.optString("logo_uri", ""),
                adTitle = obj.optString("ad_title", ""),
                adDescription = obj.optString("ad_description", ""),
                videoUri = obj.optString("video_uri", ""),
                videoDurationSec = rawDuration.coerceIn(0, ShopAdStorage.MAX_AD_VIDEO_DURATION_SEC),
                ctaLabel = obj.optString("cta_label", "Contacter").ifBlank { "Contacter" },
                isActive = obj.optBoolean("is_active", true),
                updatedAt = obj.optLong("updated_at", System.currentTimeMillis()),
                deployerId = obj.optString("deployer_id", ""),
                isSynced = obj.optBoolean("is_synced", false),
                isFallbackShopOnly = obj.optBoolean("is_fallback_shop_only", false)
            )
        }
    }
}

data class PickedAdVideoResult(
    val localUri: String,
    val localFile: File,
    val originalDurationSec: Int,
    val effectiveDurationSec: Int,
    val wasTrimmedTo60s: Boolean
)

data class ShowcaseFeedResult(
    val showcaseAds: List<ShopAdRecord>,
    val registeredShops: List<ShopProfileEntity>,
    val fromCloud: Boolean
)

data class AdCarouselSettings(
    val autoScrollEnabled: Boolean = true,
    val scrollIntervalSeconds: Int = 4,
    val includePartnerLogosInScroll: Boolean = true,
    val prioritizeVideoAds: Boolean = true,
    val autoPlayVideoPreviewInCarousel: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("auto_scroll_enabled", autoScrollEnabled)
        put("scroll_interval_seconds", scrollIntervalSeconds.coerceIn(2, 15))
        put("include_partner_logos_in_scroll", includePartnerLogosInScroll)
        put("prioritize_video_ads", prioritizeVideoAds)
        put("auto_play_video_preview", autoPlayVideoPreviewInCarousel)
    }

    companion object {
        fun fromJson(obj: JSONObject): AdCarouselSettings {
            return AdCarouselSettings(
                autoScrollEnabled = obj.optBoolean("auto_scroll_enabled", true),
                scrollIntervalSeconds = obj.optInt("scroll_interval_seconds", 4).coerceIn(2, 15),
                includePartnerLogosInScroll = obj.optBoolean("include_partner_logos_in_scroll", true),
                prioritizeVideoAds = obj.optBoolean("prioritize_video_ads", true),
                autoPlayVideoPreviewInCarousel = obj.optBoolean("auto_play_video_preview", true)
            )
        }
    }
}

object ShopAdStorage {
    const val MAX_AD_VIDEO_DURATION_SEC = 60
    const val MAX_AD_VIDEO_DURATION_MS = 60_000L

    private const val PREFS_NAME = "konab_shop_ads_prefs"
    private const val KEY_SHOP_ADS = "saved_shop_ads_json"
    private const val KEY_CACHED_SHOPS = "cached_showcase_shops_json"
    private const val KEY_CAROUSEL_SETTINGS = "ad_carousel_settings_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun saveAd(context: Context, ad: ShopAdRecord) {
        val current = getAllAds(context).toMutableList()
        val normShop = AgentDeploymentStorage.normalizeShopName(ad.shopName)
        val existingIdx = current.indexOfFirst {
            it.id == ad.id || (normShop.isNotBlank() && AgentDeploymentStorage.normalizeShopName(it.shopName) == normShop && !it.isFallbackShopOnly)
        }
        val canonicalId = if (existingIdx >= 0) current[existingIdx].id else ad.id
        val normalizedAd = ad.copy(
            id = canonicalId,
            videoDurationSec = clampVideoDurationSec(ad.videoDurationSec, ad.videoUri.isNotBlank()),
            isFallbackShopOnly = false
        )
        current.removeAll {
            it.id == canonicalId || (normShop.isNotBlank() && AgentDeploymentStorage.normalizeShopName(it.shopName) == normShop)
        }
        current.add(0, normalizedAd)
        persistAdsList(context, current)
    }

    @Synchronized
    fun saveCloudAds(context: Context, cloudAds: List<ShopAdRecord>) {
        val localAds = getAllAds(context)
        val merged = (cloudAds.map { it.copy(isSynced = true, isFallbackShopOnly = false) } + localAds)
            .filter { it.shopName.isNotBlank() }
            .sortedWith(
                compareByDescending<ShopAdRecord> { it.updatedAt }
                    .thenByDescending { it.isSynced }
            )

        val deduped = mutableListOf<ShopAdRecord>()
        val seenIds = mutableSetOf<String>()
        val seenShops = mutableSetOf<String>()
        for (ad in merged) {
            val normShop = AgentDeploymentStorage.normalizeShopName(ad.shopName)
            if (ad.id in seenIds) continue
            if (normShop.isNotBlank() && normShop in seenShops) continue
            seenIds.add(ad.id)
            if (normShop.isNotBlank()) seenShops.add(normShop)
            deduped.add(
                ad.copy(
                    videoDurationSec = clampVideoDurationSec(ad.videoDurationSec, ad.videoUri.isNotBlank())
                )
            )
        }
        persistAdsList(context, deduped)
    }

    @Synchronized
    fun deleteAd(context: Context, adId: String) {
        val updated = getAllAds(context).filterNot { it.id == adId }
        persistAdsList(context, updated)
    }

    @Synchronized
    fun getAllAds(context: Context): List<ShopAdRecord> {
        val json = getPrefs(context).getString(KEY_SHOP_ADS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<ShopAdRecord>()
            for (i in 0 until arr.length()) {
                val item = ShopAdRecord.fromJson(arr.getJSONObject(i))
                if (item.shopName.isNotBlank()) {
                    list.add(item)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveCachedShops(context: Context, shops: List<ShopProfileEntity>) {
        val arr = JSONArray()
        shops.filter { it.name.isNotBlank() }.take(200).forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("slogan", s.slogan)
                    put("phone", s.phone)
                    put("address", s.address)
                    put("city", s.city)
                    put("currency", s.currency)
                    put("business_type", s.businessType)
                    put("logo_uri", s.logoUri ?: "")
                    put("deployer_id", s.deployerId ?: "")
                }
            )
        }
        getPrefs(context).edit().putString(KEY_CACHED_SHOPS, arr.toString()).apply()
    }

    @Synchronized
    fun getCachedShops(context: Context): List<ShopProfileEntity> {
        val json = getPrefs(context).getString(KEY_CACHED_SHOPS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<ShopProfileEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name", "").trim()
                if (name.isNotBlank()) {
                    list.add(
                        ShopProfileEntity(
                            id = o.optInt("id", 1),
                            name = name,
                            slogan = o.optString("slogan", ""),
                            phone = o.optString("phone", ""),
                            address = o.optString("address", ""),
                            city = o.optString("city", ""),
                            currency = o.optString("currency", "FCFA"),
                            businessType = o.optString("business_type", "Boutique"),
                            logoUri = o.optString("logo_uri", "").ifBlank { null },
                            deployerId = o.optString("deployer_id", "").ifBlank { null },
                            isConfigured = true
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun persistAdsList(context: Context, ads: List<ShopAdRecord>) {
        val arr = JSONArray()
        ads.take(150).forEach { arr.put(it.toJson()) }
        getPrefs(context).edit().putString(KEY_SHOP_ADS, arr.toString()).apply()
    }

    @Synchronized
    fun saveCarouselSettings(context: Context, settings: AdCarouselSettings) {
        val clean = settings.copy(scrollIntervalSeconds = settings.scrollIntervalSeconds.coerceIn(2, 15))
        getPrefs(context).edit().putString(KEY_CAROUSEL_SETTINGS, clean.toJson().toString()).apply()
    }

    @Synchronized
    fun getCarouselSettings(context: Context): AdCarouselSettings {
        val raw = getPrefs(context).getString(KEY_CAROUSEL_SETTINGS, null) ?: return AdCarouselSettings()
        return try {
            AdCarouselSettings.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            AdCarouselSettings()
        }
    }

    fun clampVideoDurationSec(durationSec: Int, hasVideo: Boolean): Int {
        if (!hasVideo) return 0
        return if (durationSec <= 0) MAX_AD_VIDEO_DURATION_SEC
        else durationSec.coerceIn(1, MAX_AD_VIDEO_DURATION_SEC)
    }

    /**
     * Fusionne les publicités configurées, les boutiques inscrites sur Supabase,
     * la boutique locale et les déploiements enregistrés afin que :
     * 1. Chaque boutique partenaire inscrite (avec son logo) défile dans le cadre d'accueil (sauf si explicitement mise en pause).
     * 2. Les publicités vidéo (max 60s) configurées apparaissent en priorité pour tous les utilisateurs.
     */
    fun mergeShopsAndAds(
        registeredShops: List<ShopProfileEntity>,
        ads: List<ShopAdRecord>,
        localShop: ShopProfileEntity? = null,
        localDeployments: List<AgentDeploymentRecord> = emptyList(),
        settings: AdCarouselSettings = AdCarouselSettings()
    ): List<ShopAdRecord> {
        val adsByNormalizedName = LinkedHashMap<String, ShopAdRecord>()
        ads.filter { it.shopName.isNotBlank() && !it.isFallbackShopOnly }.forEach { ad ->
            val key = AgentDeploymentStorage.normalizeShopName(ad.shopName)
            val existing = adsByNormalizedName[key]
            if (existing == null || ad.updatedAt >= existing.updatedAt) {
                adsByNormalizedName[key] = ad
            }
        }

        val allShopsByName = collectAllPartnerShopsByName(
            registeredShops = registeredShops,
            localShop = localShop,
            localDeployments = localDeployments
        )

        val result = mutableListOf<ShopAdRecord>()
        val seenKeys = mutableSetOf<String>()
        val pausedKeys = adsByNormalizedName.filterValues { !it.isActive }.keys

        // 1. Publicités actives configurées (enrichies avec le logo/téléphone du profil boutique si absent)
        val activeComparator = if (settings.prioritizeVideoAds) {
            compareByDescending<Map.Entry<String, ShopAdRecord>> { it.value.hasVideo }
                .thenByDescending { it.value.updatedAt }
        } else {
            compareByDescending<Map.Entry<String, ShopAdRecord>> { it.value.updatedAt }
        }
        adsByNormalizedName.entries
            .filter { it.value.isActive }
            .sortedWith(activeComparator)
            .forEach { (normName, ad) ->
                val matchingShop = allShopsByName[normName]
                val enriched = ad.copy(
                    logoUri = ad.logoUri.ifBlank { matchingShop?.logoUri.orEmpty() },
                    phone = ad.phone.ifBlank { matchingShop?.phone.orEmpty() },
                    whatsappPhone = ad.whatsappPhone.ifBlank { ad.phone.ifBlank { matchingShop?.phone.orEmpty() } },
                    city = ad.city.ifBlank { matchingShop?.city.orEmpty() },
                    businessType = ad.businessType.ifBlank { matchingShop?.businessType ?: "Boutique" },
                    videoDurationSec = clampVideoDurationSec(ad.videoDurationSec, ad.hasVideo),
                    isFallbackShopOnly = false
                )
                result.add(enriched)
                seenKeys.add(normName)
            }

        // 2. Toutes les autres boutiques partenaires inscrites (avec leur logo) si autorisé et non mises en pause
        if (settings.includePartnerLogosInScroll) {
            allShopsByName.forEach { (normName, shop) ->
                if (normName !in seenKeys && normName !in pausedKeys) {
                    result.add(toPartnerFallbackAd(normName, shop, isActive = true))
                    seenKeys.add(normName)
                }
            }
        }

        return result
    }

    /**
     * Retourne la liste complète des boutiques partenaires gérables dans la régie publicitaire
     * (actives + en pause + inscrites sans publicité vidéo dédiée), afin de gérer leur défilement et vidéo 60s.
     */
    fun buildManageablePartnerShops(
        registeredShops: List<ShopProfileEntity>,
        ads: List<ShopAdRecord>,
        localShop: ShopProfileEntity? = null,
        localDeployments: List<AgentDeploymentRecord> = emptyList()
    ): List<ShopAdRecord> {
        val adsByNormalizedName = LinkedHashMap<String, ShopAdRecord>()
        ads.filter { it.shopName.isNotBlank() && !it.isFallbackShopOnly }.forEach { ad ->
            val key = AgentDeploymentStorage.normalizeShopName(ad.shopName)
            val existing = adsByNormalizedName[key]
            if (existing == null || ad.updatedAt >= existing.updatedAt) {
                adsByNormalizedName[key] = ad
            }
        }
        val allShopsByName = collectAllPartnerShopsByName(
            registeredShops = registeredShops,
            localShop = localShop,
            localDeployments = localDeployments
        )

        val list = mutableListOf<ShopAdRecord>()
        val seenKeys = mutableSetOf<String>()

        adsByNormalizedName.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, ShopAdRecord>> { it.value.isActive }
                    .thenByDescending { it.value.hasVideo }
                    .thenByDescending { it.value.updatedAt }
            )
            .forEach { (normName, ad) ->
                val matchingShop = allShopsByName[normName]
                list.add(
                    ad.copy(
                        logoUri = ad.logoUri.ifBlank { matchingShop?.logoUri.orEmpty() },
                        phone = ad.phone.ifBlank { matchingShop?.phone.orEmpty() },
                        whatsappPhone = ad.whatsappPhone.ifBlank { ad.phone.ifBlank { matchingShop?.phone.orEmpty() } },
                        city = ad.city.ifBlank { matchingShop?.city.orEmpty() },
                        businessType = ad.businessType.ifBlank { matchingShop?.businessType ?: "Boutique" },
                        videoDurationSec = clampVideoDurationSec(ad.videoDurationSec, ad.hasVideo),
                        isFallbackShopOnly = false
                    )
                )
                seenKeys.add(normName)
            }

        allShopsByName.forEach { (normName, shop) ->
            if (normName !in seenKeys) {
                list.add(toPartnerFallbackAd(normName, shop, isActive = true))
                seenKeys.add(normName)
            }
        }
        return list
    }

    private fun collectAllPartnerShopsByName(
        registeredShops: List<ShopProfileEntity>,
        localShop: ShopProfileEntity?,
        localDeployments: List<AgentDeploymentRecord>
    ): LinkedHashMap<String, ShopProfileEntity> {
        val allShopsByName = LinkedHashMap<String, ShopProfileEntity>()
        if (localShop != null && localShop.isConfigured && localShop.name.isNotBlank()) {
            allShopsByName[AgentDeploymentStorage.normalizeShopName(localShop.name)] = localShop
        }
        registeredShops.filter { it.name.isNotBlank() }.forEach { shop ->
            val key = AgentDeploymentStorage.normalizeShopName(shop.name)
            val prev = allShopsByName[key]
            if (prev == null) {
                allShopsByName[key] = shop
            } else if (prev.logoUri.isNullOrBlank() && !shop.logoUri.isNullOrBlank()) {
                allShopsByName[key] = prev.copy(logoUri = shop.logoUri)
            }
        }
        localDeployments.filter { it.shopName.isNotBlank() }.forEach { dep ->
            val key = AgentDeploymentStorage.normalizeShopName(dep.shopName)
            if (!allShopsByName.containsKey(key)) {
                allShopsByName[key] = ShopProfileEntity(
                    id = 1,
                    name = dep.shopName.trim(),
                    slogan = dep.notes.ifBlank { "Boutique partenaire KONAB POS" },
                    phone = dep.shopPhone.ifBlank { dep.agentPhone },
                    city = dep.shopCity,
                    businessType = "Boutique",
                    deployerId = dep.agentId,
                    isConfigured = true
                )
            }
        }
        return allShopsByName
    }

    private fun toPartnerFallbackAd(
        normName: String,
        shop: ShopProfileEntity,
        isActive: Boolean
    ): ShopAdRecord = ShopAdRecord(
        id = "SHOP-$normName".replace(" ", "_"),
        shopId = shop.id.toLong(),
        shopName = shop.name.trim(),
        businessType = shop.businessType.ifBlank { "Boutique" },
        city = shop.city,
        phone = shop.phone,
        whatsappPhone = shop.phone,
        logoUri = shop.logoUri.orEmpty(),
        adTitle = shop.name.trim(),
        adDescription = shop.slogan.ifBlank {
            "${shop.businessType}${if (shop.city.isNotBlank()) " • ${shop.city}" else ""} — Boutique inscrite KONAB POS"
        },
        videoUri = "",
        videoDurationSec = 0,
        ctaLabel = "Contacter",
        isActive = isActive,
        updatedAt = 0L,
        deployerId = shop.deployerId.orEmpty(),
        isSynced = true,
        isFallbackShopOnly = true
    )

    /**
     * Copie la vidéo sélectionnée via Android Photo Picker dans le stockage interne
     * et mesure sa durée réelle. Toute vidéo supérieure à 60s est automatiquement plafonnée à 60s.
     */
    fun copyVideoToInternalStorage(context: Context, sourceUri: Uri): PickedAdVideoResult? {
        return try {
            val adsDir = File(context.filesDir, "shop_ads")
            if (!adsDir.exists()) adsDir.mkdirs()

            val destFile = File(adsDir, "ad_video_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!destFile.exists() || destFile.length() == 0L) return null

            val durationMs = extractVideoDurationMs(destFile.absolutePath)
            val originalSec = ((durationMs + 500L) / 1000L).toInt().coerceAtLeast(1)
            val effectiveSec = originalSec.coerceIn(1, MAX_AD_VIDEO_DURATION_SEC)
            val wasTrimmed = originalSec > MAX_AD_VIDEO_DURATION_SEC

            PickedAdVideoResult(
                localUri = Uri.fromFile(destFile).toString(),
                localFile = destFile,
                originalDurationSec = originalSec,
                effectiveDurationSec = effectiveSec,
                wasTrimmedTo60s = wasTrimmed
            )
        } catch (e: Exception) {
            android.util.Log.e("ShopAdStorage", "Erreur copie vidéo pub: ${e.message}", e)
            null
        }
    }

    fun copyAdLogoToInternalStorage(context: Context, sourceUri: Uri): Pair<String, File>? {
        return try {
            val adsDir = File(context.filesDir, "shop_ads")
            if (!adsDir.exists()) adsDir.mkdirs()
            val destFile = File(adsDir, "ad_logo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (!destFile.exists() || destFile.length() == 0L) return null
            Uri.fromFile(destFile).toString() to destFile
        } catch (e: Exception) {
            android.util.Log.e("ShopAdStorage", "Erreur copie logo pub: ${e.message}", e)
            null
        }
    }

    private fun extractVideoDurationMs(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 30_000L
        } catch (_: Exception) {
            30_000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun formatInternationalWaPhone(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return when {
            digits.startsWith("00226") -> digits.drop(2)
            digits.startsWith("226") && digits.length >= 11 -> digits
            digits.length == 8 -> "226$digits"
            else -> digits
        }
    }

    fun formatSecondsAsTimer(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val m = safe / 60
        val s = safe % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
