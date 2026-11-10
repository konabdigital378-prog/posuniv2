package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utilitaire permettant d'extraire et de partager le fichier APK de l'application
 * directement sur WhatsApp (standard ou WhatsApp Business) ou toute autre application de messagerie.
 */
object ApkShareHelper {

    private const val APK_FILE_NAME = "Konab_POS.apk"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /**
     * Copie l'APK installé dans le cache partagé et lance l'intent de partage ciblant WhatsApp.
     *
     * @param context Contexte Android
     * @param onLoading Callback informant de l'état de préparation (début / fin)
     */
    suspend fun shareApkOnWhatsApp(
        context: Context,
        onLoading: (Boolean) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                onLoading(true)
                Toast.makeText(context, "Préparation du fichier APK de Konab POS...", Toast.LENGTH_SHORT).show()
            }

            // 1. Récupération du fichier APK source installé
            val sourceApkPath = context.applicationInfo.sourceDir
            val sourceApk = File(sourceApkPath)

            if (!sourceApk.exists() || sourceApk.length() == 0L) {
                withContext(Dispatchers.Main) {
                    onLoading(false)
                    Toast.makeText(context, "Fichier APK d'origine introuvable sur cet appareil", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }

            // 2. Dossier de cache partagé configuré dans file_paths.xml (<cache-path name="shared_apks" path="apks/" />)
            val apkDir = File(context.cacheDir, "apks")
            if (!apkDir.exists()) {
                apkDir.mkdirs()
            }

            val targetApk = File(apkDir, APK_FILE_NAME)

            // Copie de l'APK (si absent ou si la taille diffère)
            if (!targetApk.exists() || targetApk.length() != sourceApk.length()) {
                sourceApk.inputStream().use { input ->
                    targetApk.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // 3. Obtention de l'URI sécurisée via FileProvider
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApk
            )

            // 4. Construction de l'Intent de partage
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "📦 *Application Konab POS (Fichier APK Officiel)*\n\n" +
                    "✨ Solution de caisse et de gestion commerciale 100% hors-ligne & synchronisable pour commerces et PME.\n\n" +
                    "📲 *Comment installer :*\n" +
                    "1. Téléchargez et cliquez sur ce fichier APK reçu.\n" +
                    "2. Autorisez l'installation depuis cette source si demandé.\n" +
                    "3. Ouvrez Konab POS et configurez votre boutique en 1 minute !"
                )
                putExtra(Intent.EXTRA_SUBJECT, "Application Konab POS — Fichier APK d'installation")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 5. Détection de WhatsApp ou WhatsApp Business
            val pm = context.packageManager
            val isWhatsAppInstalled = try {
                pm.getPackageInfo(WHATSAPP_PACKAGE, 0)
                true
            } catch (_: Exception) {
                false
            }

            val isWhatsAppBusinessInstalled = try {
                pm.getPackageInfo(WHATSAPP_BUSINESS_PACKAGE, 0)
                true
            } catch (_: Exception) {
                false
            }

            withContext(Dispatchers.Main) {
                onLoading(false)
                when {
                    isWhatsAppInstalled -> {
                        shareIntent.setPackage(WHATSAPP_PACKAGE)
                        context.startActivity(shareIntent)
                        Toast.makeText(context, "Ouverture de WhatsApp pour envoyer l'APK...", Toast.LENGTH_SHORT).show()
                    }
                    isWhatsAppBusinessInstalled -> {
                        shareIntent.setPackage(WHATSAPP_BUSINESS_PACKAGE)
                        context.startActivity(shareIntent)
                        Toast.makeText(context, "Ouverture de WhatsApp Business...", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        // WhatsApp non installé : proposer le sélecteur Android pour choisir une application
                        val chooser = Intent.createChooser(
                            shareIntent,
                            "Partager le fichier APK Konab POS via..."
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                        Toast.makeText(
                            context,
                            "WhatsApp n'est pas installé sur cet appareil. Choisissez une application de partage.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onLoading(false)
                Toast.makeText(
                    context,
                    "Erreur lors de la préparation de l'APK : ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
        }
    }
}
