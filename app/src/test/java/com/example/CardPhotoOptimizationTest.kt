package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.AgentIdCardHelper
import com.example.util.KonabImageOptimizer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CardPhotoOptimizationTest {

    @Test
    fun `compresses and reloads high resolution card photo without failing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceFile = File(context.cacheDir, "huge_test_photo.jpg")
        val bigBitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        FileOutputStream(sourceFile).use { out ->
            assertTrue(bigBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out))
        }
        bigBitmap.recycle()

        val savedUri = AgentIdCardHelper.saveAgentPhotoToInternalStorage(
            context = context,
            sourceUri = Uri.fromFile(sourceFile),
            agentKey = "card_test_user"
        )
        assertNotNull("La photo haute résolution doit être sauvegardée", savedUri)

        val savedFile = File(Uri.parse(savedUri!!).path!!)
        assertTrue("Le fichier optimisé doit exister", savedFile.exists() && savedFile.length() > 0L)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(savedFile.absolutePath, bounds)
        assertTrue("La largeur redimensionnée doit être <= 640px", bounds.outWidth in 1..640)
        assertTrue("La hauteur redimensionnée doit être <= 640px", bounds.outHeight in 1..640)

        val reloaded = AgentIdCardHelper.loadAgentPhotoBitmap(context, savedUri)
        assertNotNull("Le bitmap de la carte doit se recharger correctement", reloaded)
        reloaded?.recycle()
        sourceFile.delete()
    }
}
