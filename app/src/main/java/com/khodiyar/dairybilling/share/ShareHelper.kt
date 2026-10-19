package com.khodiyar.dairybilling.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sharing goes through FileProvider content:// URIs with a temporary read grant, so no private
 * file path is ever exposed. Opening a share sheet does NOT mean the document was sent: the user
 * completes sending inside the chosen app.
 */
object ShareHelper {
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    fun authority(context: Context) = context.packageName + ".fileprovider"

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    fun buildSendIntent(context: Context, file: File, mime: String, message: String?, subject: String?): Intent {
        val uri = uriFor(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!message.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, message)
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun hasShareTargets(context: Context, mime: String): Boolean {
        val probe = Intent(Intent.ACTION_SEND).setType(mime)
        return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    }

    fun installedWhatsApp(context: Context): String? = listOf(WHATSAPP, WHATSAPP_BUSINESS).firstOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    enum class Outcome { OPENED, NO_APP }

    /** Android Sharesheet with every compatible app (WhatsApp, Gmail, Drive, Bluetooth ...). */
    fun share(context: Context, file: File, mime: String, message: String?, chooserTitle: String): Outcome {
        if (!hasShareTargets(context, mime)) return Outcome.NO_APP
        val chooser = Intent.createChooser(buildSendIntent(context, file, mime, message, file.nameWithoutExtension), chooserTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            Outcome.OPENED
        } catch (e: ActivityNotFoundException) {
            Outcome.NO_APP
        }
    }

    /** Sends the PDF straight to WhatsApp's contact picker; the user chooses and confirms the chat. */
    fun shareToWhatsApp(context: Context, file: File, message: String?): Outcome {
        val pkg = installedWhatsApp(context) ?: return Outcome.NO_APP
        val intent = buildSendIntent(context, file, "application/pdf", message, null).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Outcome.OPENED
        } catch (e: ActivityNotFoundException) {
            Outcome.NO_APP
        }
    }

    /** Opens the customer's WhatsApp chat with the message prefilled (text only; attach the PDF there). */
    fun openWhatsAppChat(context: Context, mobile: String, message: String): Outcome {
        val number = normalizeIndianMobile(mobile) ?: return Outcome.NO_APP
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=" + Uri.encode(message)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        installedWhatsApp(context)?.let { intent.setPackage(it) } ?: return Outcome.NO_APP
        return try {
            context.startActivity(intent)
            Outcome.OPENED
        } catch (e: ActivityNotFoundException) {
            Outcome.NO_APP
        }
    }

    fun dial(context: Context, mobile: String): Outcome = try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + mobile.filter { it.isDigit() || it == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Outcome.OPENED
    } catch (e: ActivityNotFoundException) {
        Outcome.NO_APP
    }

    /** "98765 43210", "+91-98765-43210", "098765..." -> "919876543210"; null if not a valid mobile. */
    fun normalizeIndianMobile(raw: String): String? {
        var d = raw.filter { it.isDigit() }
        if (d.length == 11 && d.startsWith("0")) d = d.drop(1)
        if (d.length == 10 && d[0] in '6'..'9') return "91$d"
        if (d.length == 12 && d.startsWith("91") && d[2] in '6'..'9') return d
        return null
    }
}
