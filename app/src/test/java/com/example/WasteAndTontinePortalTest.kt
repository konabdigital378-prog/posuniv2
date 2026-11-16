package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.SupabaseConfig
import com.example.data.remote.supabase.TontineBusinessProfile
import com.example.data.remote.supabase.TontineCardStatus
import com.example.data.remote.supabase.TontineCheckMark
import com.example.data.remote.supabase.TontineGroup
import com.example.data.remote.supabase.TontineMemberCard
import com.example.data.remote.supabase.TontineQrParser
import com.example.data.remote.supabase.TontineStorage
import com.example.data.remote.supabase.WasteBusinessProfile
import com.example.data.remote.supabase.WasteClientStatus
import com.example.data.remote.supabase.WasteCollectorStorage
import com.example.data.remote.supabase.WasteCyclePayment
import com.example.data.remote.supabase.WasteHouseholdClient
import com.example.data.remote.supabase.WastePickupLog
import com.example.data.remote.supabase.WasteQrParser
import com.example.ui.screens.AgentDirectory
import com.example.ui.viewmodel.KonabViewModel
import com.example.util.ShopLogoHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WasteAndTontinePortalTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("konab_waste_collector_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("konab_tontine_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `shop logo helper detects local vs published URIs and round-trips portable Data URI`() {
        assertTrue(ShopLogoHelper.isLocalUnpublishedLogoUri("file:///data/user/0/com.example/files/logos/shop.jpg"))
        assertTrue(ShopLogoHelper.isLocalUnpublishedLogoUri("content://media/external/images/12"))
        assertFalse(ShopLogoHelper.isLocalUnpublishedLogoUri("https://cdn.example.com/shop_1.jpg"))
        assertFalse(ShopLogoHelper.isLocalUnpublishedLogoUri("data:image/jpeg;base64,/9j/4AAQ"))
        assertFalse(ShopLogoHelper.isLocalUnpublishedLogoUri("drawable:ic_konab_logo"))
        assertFalse(ShopLogoHelper.isLocalUnpublishedLogoUri(""))

        // Save a real bitmap to a temp file and convert -> portable data:image/jpeg;base64 -> decode
        val tmpFile = File(context.cacheDir, "test_shop_logo.png")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(255, 119, 0))
        }
        FileOutputStream(tmpFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()

        val savedInternalUri = ShopLogoHelper.saveLogoToInternalStorage(context, Uri.fromFile(tmpFile))
        assertNotNull(savedInternalUri)
        assertTrue(savedInternalUri!!.startsWith("file://"))
        assertNotNull(ShopLogoHelper.getUploadableLogoFile(context, savedInternalUri))

        val dataUri = ShopLogoHelper.encodeLogoToPortableDataUri(context, savedInternalUri)
        assertNotNull(dataUri)
        assertTrue(dataUri!!.startsWith("data:image/jpeg;base64,"))

        val decoded = ShopLogoHelper.decodeDataUriBitmap(dataUri)
        assertNotNull(decoded)
        assertEquals(64, decoded!!.width)
        assertEquals(64, decoded.height)
    }

    @Test
    fun `waste collector workflow supports business, prospect, family card, pickups, WhatsApp billing and LWW sync`() {
        val biz = WasteCollectorStorage.upsertBusiness(
            context,
            WasteBusinessProfile(
                id = "WB-1",
                businessName = "Propre Ouaga Service",
                managerName = "Kaboré Salif",
                phone = "+226 70 11 22 33",
                city = "Ouagadougou",
                defaultMonthlyFee = 3000L,
                defaultPassagesPerCycle = 4
            )
        )
        assertEquals("Propre Ouaga Service", biz.businessName)

        // Prospect client
        val prospect = WasteHouseholdClient(
            id = "FAM-PROSPECT-1",
            businessId = biz.id,
            businessName = biz.businessName,
            businessPhone = biz.phone,
            familyName = "Famille Sawadogo",
            contactName = "Mme Sawadogo",
            phone = "76 44 55 66",
            neighborhood = "Patte d'Oie",
            status = WasteClientStatus.PROSPECT,
            pickupDaysLabel = "Mar • Ven",
            monthlyFee = 3000L,
            targetPassages = 4
        )
        WasteCollectorStorage.upsertClient(context, prospect)

        // Check off 4 passages -> card becomes full
        val baseTs = System.currentTimeMillis()
        val logs = (1..4).map { idx ->
            WastePickupLog(
                id = "PK-$idx",
                timestamp = baseTs + idx * 86_400_000L,
                collectorName = "Équipe A"
            )
        }
        val activeFullFamily = prospect.copy(
            status = WasteClientStatus.CLIENT_ACTIF,
            passagesLogs = logs,
            updatedAt = baseTs + 500_000_000L
        )
        WasteCollectorStorage.upsertClient(context, activeFullFamily)

        assertTrue(activeFullFamily.isCardFull)
        assertEquals(4, activeFullFamily.currentCheckedCount)

        val whatsappMsg = activeFullFamily.buildBillingWhatsAppMessage()
        assertTrue(whatsappMsg.contains("Famille Sawadogo"))
        assertTrue(whatsappMsg.contains("Propre Ouaga Service"))
        assertTrue(whatsappMsg.contains("4 / 4 passages"))
        assertTrue(whatsappMsg.contains("REMPLIE"))

        // Family lookup works by normalized 8-digit phone, code, or family name
        val allClients = WasteCollectorStorage.getClients(context)
        assertEquals(1, WasteCollectorStorage.findClientsForFamily(allClients, "+22676445566").size)
        assertEquals(1, WasteCollectorStorage.findClientsForFamily(allClients, "FAM-PROSPECT-1").size)
        assertEquals(1, WasteCollectorStorage.findClientsForFamily(allClients, "Sawadogo").size)

        // QR roundtrip
        val qrUri = activeFullFamily.buildQrUri()
        val parsed = WasteQrParser.parseWasteQrUri(qrUri)
        assertNotNull(parsed)
        assertEquals("WB-1", parsed!!.businessId)
        assertEquals("FAM-PROSPECT-1", parsed.clientId)

        // Recording cycle payment resets current passages and stores cumulative history
        val payment = WasteCyclePayment(
            id = "PAY-1",
            amount = 3000L,
            paidAt = baseTs + 600_000_000L,
            passagesCompleted = 4,
            cycleTarget = 4,
            pickupTimestamps = logs.map { it.timestamp }
        )
        val afterPay = activeFullFamily.copy(
            passagesLogs = emptyList(),
            paymentHistory = listOf(payment),
            updatedAt = baseTs + 600_000_000L
        )
        WasteCollectorStorage.upsertClient(context, afterPay)
        assertEquals(0, afterPay.currentCheckedCount)
        assertEquals(4, afterPay.totalHistoricalPickups)

        // LWW cloud merge skips stale update and applies newer update
        val staleRemote = afterPay.copy(familyName = "Old Name", updatedAt = baseTs + 100L)
        WasteCollectorStorage.mergeClientsFromCloud(context, listOf(staleRemote))
        assertEquals("Famille Sawadogo", WasteCollectorStorage.getClients(context).first().familyName)

        val newerRemote = afterPay.copy(neighborhood = "Ouaga 2000", updatedAt = baseTs + 900_000_000L)
        WasteCollectorStorage.mergeClientsFromCloud(context, listOf(newerRemote))
        assertEquals("Ouaga 2000", WasteCollectorStorage.getClients(context).first().neighborhood)
    }

    @Test
    fun `tontine 30-day carnet tracks business, groups, real-time checkmarks, settled message and QR lookup`() {
        val biz = TontineStorage.upsertBusiness(
            context,
            TontineBusinessProfile(
                id = "TBZ-1",
                businessName = "Association Solidarité Ouaga",
                organizerName = "Tantie Mariam",
                phone = "70 99 88 77"
            )
        )
        val group = TontineStorage.upsertGroup(
            context,
            TontineGroup(
                id = "TGR-1",
                businessId = biz.id,
                businessName = biz.businessName,
                businessPhone = biz.phone,
                title = "Tontine Commerce 1000F",
                dailyAmount = 1000L,
                totalDays = 30
            )
        )
        assertEquals(30_000L, group.totalTargetAmount)

        val marks = (1..30).map { day ->
            TontineCheckMark(
                dayNumber = day,
                checkedAt = 1_710_000_000_000L + day * 3_600_000L,
                amount = 1000L,
                checkedBy = "Tantie Mariam"
            )
        }
        val card = TontineStorage.upsertCard(
            context,
            TontineMemberCard(
                id = "TC-1001",
                businessId = biz.id,
                businessName = biz.businessName,
                businessPhone = biz.phone,
                groupId = group.id,
                groupTitle = group.title,
                memberName = "Awa Zongo",
                memberPhone = "78 12 34 56",
                dailyAmount = 1000L,
                totalDays = 30,
                payoutTurnNumber = 2,
                checkMarks = marks,
                status = TontineCardStatus.SOLDEE_A_PAYER,
                validatedAt = 1_710_200_000_000L
            ).let { it.copy(validationMessage = it.buildSettledMessage()) }
        )

        assertTrue(card.isFullCard)
        assertEquals(30, card.checkedCount)
        assertEquals(0, card.remainingDays)
        assertEquals(30_000L, card.totalCollected)
        assertNull(card.nextUncheckedDay())
        assertTrue(card.isDayChecked(15))
        assertTrue(card.validationMessage.contains("SOLDÉE"))
        assertTrue(card.validationMessage.contains("Association Solidarité Ouaga"))
        assertTrue(card.buildWhatsAppSettledMessage().contains("Awa Zongo"))

        // Member lookup finds card by normalized phone and exposes organizing business name
        val found = TontineStorage.findCardsForMember(TontineStorage.getCards(context), "+22678123456")
        assertEquals(1, found.size)
        assertEquals("Association Solidarité Ouaga", found.first().businessName)

        // QR roundtrip
        val parsed = TontineQrParser.parseTontineQrUri(card.buildQrUri())
        assertNotNull(parsed)
        assertEquals("TBZ-1", parsed!!.businessId)
        assertEquals("TC-1001", parsed.cardId)
    }

    @Test
    fun `supabase sql schema defines all waste collector and tontine tables and RLS policies`() {
        val sql = SupabaseConfig.generateSqlSchema()
        listOf(
            "CREATE TABLE IF NOT EXISTS public.waste_businesses",
            "CREATE TABLE IF NOT EXISTS public.waste_clients",
            "CREATE TABLE IF NOT EXISTS public.tontine_businesses",
            "CREATE TABLE IF NOT EXISTS public.tontine_groups",
            "CREATE TABLE IF NOT EXISTS public.tontine_cards",
            "Allow anon read/write for waste_businesses",
            "Allow anon read/write for waste_clients",
            "Allow anon read/write for tontine_businesses",
            "Allow anon read/write for tontine_groups",
            "Allow anon read/write for tontine_cards"
        ).forEach { expected ->
            assertTrue("Missing SQL definition: $expected", sql.contains(expected))
        }
    }

    @Test
    fun `konabViewModel handles waste and tontine actions and QR routing cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AgentDeploymentStorage.saveAgentSession(app, AgentDirectory.PRESET_AGENTS.first())
        val vm = KonabViewModel(app)

        var createdWasteClient: WasteHouseholdClient? = null
        vm.saveWasteBusiness("EcoSalubrité", "Oumar", "70000001", "Bobo", "1234", 2000L, 4) { biz ->
            vm.saveWasteClient(
                business = biz,
                familyName = "Famille Traoré",
                contactName = "Ali",
                phone = "70112299",
                neighborhood = "Sarfalao",
                status = WasteClientStatus.CLIENT_ACTIF,
                pickupDaysLabel = "Lun • Jeu",
                monthlyFee = 2000L,
                targetPassages = 4
            ) { client ->
                createdWasteClient = client
            }
        }
        assertNotNull(createdWasteClient)
        assertTrue(vm.openAnyQrScan(createdWasteClient!!.buildQrUri()))
        assertEquals(createdWasteClient!!.id, vm.pendingWasteQrTarget.value?.clientId)
        vm.dismissWasteQrTarget()

        var createdCard: TontineMemberCard? = null
        vm.saveTontineBusiness("Caisse Femmes Actives", "Bibata", "71000002", "Ouaga", "1234") { tbiz ->
            vm.saveTontineGroup(tbiz, "Tontine 500F", 500L, 30) { grp ->
                vm.saveTontineMemberCard(grp, "Fatimata", "79556677", 1) { card ->
                    createdCard = card
                }
            }
        }
        assertNotNull(createdCard)
        vm.checkNextTontineDay(createdCard!!, "Bibata") { updated ->
            assertEquals(1, updated.checkedCount)
            assertTrue(updated.isDayChecked(1))
        }
        assertTrue(vm.openAnyQrScan(createdCard!!.buildQrUri()))
        assertEquals(createdCard!!.id, vm.pendingTontineQrTarget.value?.cardId)
    }
}
