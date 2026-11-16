package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.CoutureStorage
import com.example.data.remote.supabase.PublicUserStorage
import com.example.data.remote.supabase.RestaurantDirectoryHelper
import com.example.data.remote.supabase.SupabaseConfig
import com.example.data.remote.supabase.TontineStorage
import com.example.data.remote.supabase.WasteCollectorStorage
import com.example.ui.components.ActivityCardBitmapHelper
import com.example.ui.components.CardActivityTheme
import com.example.ui.screens.AgentDirectory
import com.example.ui.viewmodel.KonabViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserAuthAndDeployerRulesTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        listOf(
            "konab_agent_deployments_prefs",
            "konab_public_user_prefs",
            "konab_waste_collector_prefs",
            "konab_tontine_prefs",
            "konab_couture_prefs"
        ).forEach { prefs ->
            app.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit()
        }
        AgentDeploymentStorage.clearAgentSession(app)
        SupabaseConfig.saveCustomConfig(app, "http://127.0.0.1:1", "local-test-anon-key-1234567890")
    }

    @After
    fun tearDown() {
        SupabaseConfig.clearCustomConfig(app)
    }

    private fun awaitMainCallback(block: (done: () -> Unit) -> Unit) {
        val latch = CountDownLatch(1)
        block { latch.countDown() }
        val deadline = System.currentTimeMillis() + 8_000L
        while (latch.count > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            latch.await(25, TimeUnit.MILLISECONDS)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Async callback did not complete in time", 0L, latch.count)
    }

    @Test
    fun `strict deployer rule blocks waste and tontine organization creation without active agent`() {
        val vm = KonabViewModel(app)

        var wasteError = ""
        var wasteCreated = false
        vm.saveWasteBusiness(
            businessName = "Salubrité Sans Agent",
            managerName = "Kaba",
            phone = "622112233",
            city = "Conakry",
            pin = "1234",
            defaultFee = 50000L,
            defaultPassages = 8,
            onError = { wasteError = it },
            onResult = { wasteCreated = true }
        )
        assertFalse("Waste creation must be blocked without agent", wasteCreated)
        assertTrue(wasteError.contains("Agent Déployeur", ignoreCase = true))
        assertTrue(vm.wasteBusinesses.value.isEmpty())

        var tontineError = ""
        var tontineCreated = false
        vm.saveTontineBusiness(
            businessName = "Tontine Sans Agent",
            organizerName = "Aissatou",
            phone = "622445566",
            city = "Conakry",
            pin = "1234",
            onError = { tontineError = it },
            onResult = { tontineCreated = true }
        )
        assertFalse("Tontine creation must be blocked without agent", tontineCreated)
        assertTrue(tontineError.contains("Agent Déployeur", ignoreCase = true))
        assertTrue(vm.tontineBusinesses.value.isEmpty())

        // Now log in an Agent Déployeur and verify creation succeeds and stamps deployerId
        val agent = AgentDirectory.PRESET_AGENTS.first()
        AgentDeploymentStorage.saveAgentSession(app, agent)

        var createdWasteDeployerId = ""
        vm.saveWasteBusiness(
            businessName = "EcoConakry",
            managerName = "Moussa",
            phone = "620111222",
            city = "Conakry",
            pin = "1234",
            defaultFee = 40000L,
            defaultPassages = 8
        ) { saved ->
            createdWasteDeployerId = saved.deployerId
        }
        assertEquals(agent.id, createdWasteDeployerId)
        assertEquals(1, vm.wasteBusinesses.value.size)

        var createdTontineDeployerId = ""
        vm.saveTontineBusiness(
            businessName = "Caisse Solidarité",
            organizerName = "Mariama",
            phone = "621333444",
            city = "Conakry",
            pin = "1234"
        ) { saved ->
            createdTontineDeployerId = saved.deployerId
        }
        assertEquals(agent.id, createdTontineDeployerId)
        assertEquals(1, vm.tontineBusinesses.value.size)
    }

    @Test
    fun `public user registration propagates context and enforces pin login`() {
        val vm = KonabViewModel(app)
        var regSuccess = false
        var regMsg = ""

        awaitMainCallback { done ->
            vm.registerPublicUser(
                fullName = "Mamadou Diallo",
                phone = "+224 622 88 99 00",
                city = "Conakry",
                neighborhood = "Kipé",
                pin = "2468",
                preferredActivity = "RESTAURANT_CANTINE"
            ) { ok, msg ->
                regSuccess = ok
                regMsg = msg
                done()
            }
        }

        assertTrue("Expected registration success: $regMsg", regSuccess)
        val activeUser = vm.currentPublicUser.value
        assertNotNull(activeUser)
        assertEquals("Mamadou Diallo", activeUser!!.fullName)
        assertEquals("RESTAURANT_CANTINE", activeUser.preferredActivity)
        assertTrue(activeUser.buildQrUri().startsWith("konabpos://user?"))

        // Context automatically propagated to Waste, Tontine & Couture portals
        assertEquals("+224 622 88 99 00", WasteCollectorStorage.getSavedFamilyQuery(app))
        assertEquals("+224 622 88 99 00", TontineStorage.getSavedMemberPhone(app))
        val coutureClient = CoutureStorage.getClientAccount(app)
        assertNotNull(coutureClient)
        assertEquals("Mamadou", coutureClient!!.firstName)
        assertEquals("Diallo", coutureClient.lastName)

        // Logout clears active session, login verifies PIN & normalizes phone
        vm.logoutPublicUser()
        assertNull(vm.currentPublicUser.value)

        var badPinOk = true
        awaitMainCallback { done ->
            vm.loginPublicUser("622889900", "9999") { ok, _ ->
                badPinOk = ok
                done()
            }
        }
        assertFalse(badPinOk)
        assertNull(vm.currentPublicUser.value)

        var goodPinOk = false
        awaitMainCallback { done ->
            vm.loginPublicUser("622889900", "2468") { ok, _ ->
                goodPinOk = ok
                done()
            }
        }
        assertTrue(goodPinOk)
        assertEquals("Mamadou Diallo", vm.currentPublicUser.value?.fullName)
        assertNotNull(PublicUserStorage.findByPhoneOrId(PublicUserStorage.getAllUsers(app), activeUser.id))
    }

    @Test
    fun `activity digital card bitmap renders with Conakry Web branding for all activity themes`() {
        CardActivityTheme.entries.forEach { theme ->
            val bmp = ActivityCardBitmapHelper.renderCardBitmap(
                context = app,
                theme = theme,
                holderName = "Fatoumata Camara",
                codeId = "CONAKRY-${theme.name}-01",
                organizationTitle = "Conakry Web • ${theme.title}",
                phone = "622001122",
                zoneOrAddress = "Ratoma, Conakry",
                qrPayload = "konabpos://card/${theme.name}/01",
                photoUri = null,
                statusBadge = "Vérifiée",
                statsLine = "10/30 validés"
            )
            assertEquals(1011, bmp.width)
            assertEquals(638, bmp.height)
            assertFalse(bmp.isRecycled)
            bmp.recycle()
        }
    }

    @Test
    fun `supabase schema and cantine keyword support are configured cleanly`() {
        val sql = SupabaseConfig.generateSqlSchema()
        listOf(
            "CREATE TABLE IF NOT EXISTS public.app_users",
            "idx_app_users_phone",
            "Allow anon read/write for app_users",
            "ALTER TABLE IF EXISTS public.waste_businesses ADD COLUMN IF NOT EXISTS deployer_id",
            "ALTER TABLE IF EXISTS public.waste_clients ADD COLUMN IF NOT EXISTS photo_uri",
            "ALTER TABLE IF EXISTS public.tontine_businesses ADD COLUMN IF NOT EXISTS deployer_id",
            "ALTER TABLE IF EXISTS public.tontine_cards ADD COLUMN IF NOT EXISTS photo_uri"
        ).forEach { expected ->
            assertTrue("Missing SQL clause: $expected", sql.contains(expected))
        }

        assertTrue(RestaurantDirectoryHelper.isRestaurantShop("Cantine Scolaire Kipé"))
        assertTrue(RestaurantDirectoryHelper.isRestaurantShop("Restaurant & Cantine"))
    }
}
