package com.jupiter.vision.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jupiter.vision.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppLoader {

    data class SystemAppResolution(
        val role: String,
        val packageName: String?,
        val label: String,
        val icon: ImageBitmap?,
        val iconBitmap: Bitmap? = null
    )

    fun drawableToBitmap(d: Drawable): Bitmap? {
        return try {
            if (d is BitmapDrawable && d.bitmap != null && !d.bitmap.isRecycled) {
                return d.bitmap
            }
            val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
            val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
            val targetW = w.coerceIn(48, 144)
            val targetH = h.coerceIn(48, 144)
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    fun drawableToImageBitmap(d: Drawable): ImageBitmap? {
        val bmp = drawableToBitmap(d) ?: return null
        return try {
            bmp.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    fun getAppIconDrawable(context: Context, packageName: String): Drawable? {
        return try {
            val pm = context.packageManager
            pm.getApplicationIcon(packageName)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun resolveSystemRoles(context: Context): Map<String, SystemAppResolution> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val myPkg = context.packageName

        fun resolvePackage(intents: List<Intent>, candidatePackages: List<String>): String? {
            for (intent in intents) {
                try {
                    val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    val pkg = resolveInfo?.activityInfo?.packageName
                    if (pkg != null && pkg != myPkg && isPackageInstalled(pm, pkg)) {
                        return pkg
                    }
                } catch (_: Throwable) {}
            }
            for (pkg in candidatePackages) {
                if (isPackageInstalled(pm, pkg)) {
                    return pkg
                }
            }
            return null
        }

        val roles = mutableMapOf<String, SystemAppResolution>()

        // 1. Phone
        val phonePkg = resolvePackage(
            listOf(Intent(Intent.ACTION_DIAL), Intent(Intent.ACTION_CALL_BUTTON)),
            listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer", "com.google.android.apps.googlevoice")
        )
        val phoneDrawable = phonePkg?.let { getAppIconDrawable(context, it) }
        val phoneBmp = phoneDrawable?.let { drawableToBitmap(it) }
        roles["phone"] = SystemAppResolution("phone", phonePkg, "PHONE", phoneBmp?.asImageBitmap(), phoneBmp)

        // 2. Messages
        val msgPkg = resolvePackage(
            listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)),
            listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging")
        )
        val msgDrawable = msgPkg?.let { getAppIconDrawable(context, it) }
        val msgBmp = msgDrawable?.let { drawableToBitmap(it) }
        roles["msg"] = SystemAppResolution("msg", msgPkg, "MSG", msgBmp?.asImageBitmap(), msgBmp)

        // 3. Gallery
        val galPkg = resolvePackage(
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
                Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
            ),
            listOf("com.google.android.apps.photos", "com.android.gallery3d", "com.sec.android.gallery3d", "com.google.android.apps.photosgo")
        )
        val galDrawable = galPkg?.let { getAppIconDrawable(context, it) }
        val galBmp = galDrawable?.let { drawableToBitmap(it) }
        roles["gallery"] = SystemAppResolution("gallery", galPkg, "GALLERY", galBmp?.asImageBitmap(), galBmp)

        // 4. Camera
        val camPkg = resolvePackage(
            listOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            listOf("com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2", "com.sec.android.app.camera")
        )
        val camDrawable = camPkg?.let { getAppIconDrawable(context, it) }
        val camBmp = camDrawable?.let { drawableToBitmap(it) }
        roles["cam"] = SystemAppResolution("cam", camPkg, "CAM", camBmp?.asImageBitmap(), camBmp)

        // 5. Settings
        val setPkg = resolvePackage(
            listOf(Intent(Settings.ACTION_SETTINGS)),
            listOf("com.android.settings")
        )
        val setDrawable = setPkg?.let { getAppIconDrawable(context, it) }
        val setBmp = setDrawable?.let { drawableToBitmap(it) }
        roles["set"] = SystemAppResolution("set", setPkg, "SETTINGS", setBmp?.asImageBitmap(), setBmp)

        // 6. Music
        val musPkg = resolvePackage(
            listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)),
            listOf("com.google.android.apps.youtube.music", "com.spotify.music", "com.android.music", "com.sec.android.app.music")
        )
        val musDrawable = musPkg?.let { getAppIconDrawable(context, it) }
        val musBmp = musDrawable?.let { drawableToBitmap(it) }
        roles["music"] = SystemAppResolution("music", musPkg, "MUSIC", musBmp?.asImageBitmap(), musBmp)

        // 7. Flash
        roles["flash"] = SystemAppResolution("flash", null, "FLASH", null, null)

        // 8. Calculator
        val calcPkg = resolvePackage(
            listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)),
            listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator")
        )
        val calcDrawable = calcPkg?.let { getAppIconDrawable(context, it) }
        val calcBmp = calcDrawable?.let { drawableToBitmap(it) }
        roles["calc"] = SystemAppResolution("calc", calcPkg, "CALC", calcBmp?.asImageBitmap(), calcBmp)

        // 9. Notes
        val notePkg = resolvePackage(
            listOf(Intent(Intent.ACTION_CREATE_NOTE)),
            listOf("com.google.android.keep", "com.samsung.android.app.notes", "com.android.notes")
        )
        val noteDrawable = notePkg?.let { getAppIconDrawable(context, it) }
        val noteBmp = noteDrawable?.let { drawableToBitmap(it) }
        roles["notes"] = SystemAppResolution("notes", notePkg, "NOTES", noteBmp?.asImageBitmap(), noteBmp)

        // 10. NEW_SYS slot
        val usedPkgs = setOfNotNull(phonePkg, msgPkg, galPkg, camPkg, setPkg, musPkg, calcPkg, notePkg)

        val candidateNewSysRoles = listOf(
            listOf(
                Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
                Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CLOCK")
            ) to listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage"),
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)
            ) to listOf("com.google.android.contacts", "com.android.contacts"),
            listOf(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*" },
                Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            ) to listOf("com.google.android.documentsui", "com.android.documentsui", "com.google.android.apps.nbu.files"),
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            ) to listOf("com.google.android.calendar", "com.android.calendar")
        )

        var newSysPkg: String? = null
        var newSysLabel = "CLOCK"

        for ((intents, candidates) in candidateNewSysRoles) {
            val resolved = resolvePackage(intents, candidates)
            if (resolved != null && !usedPkgs.contains(resolved)) {
                newSysPkg = resolved
                newSysLabel = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(resolved, 0)).toString().uppercase()
                } catch (_: Throwable) {
                    "SYSTEM"
                }
                break
            }
        }

        if (newSysPkg == null) {
            try {
                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (app in installed) {
                    val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSys && app.packageName != myPkg && !usedPkgs.contains(app.packageName)) {
                        if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                            newSysPkg = app.packageName
                            newSysLabel = pm.getApplicationLabel(app).toString().uppercase()
                            break
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        val newSysDrawable = newSysPkg?.let { getAppIconDrawable(context, it) }
        val newSysBmp = newSysDrawable?.let { drawableToBitmap(it) }
        roles["new_sys"] = SystemAppResolution("new_sys", newSysPkg, newSysLabel, newSysBmp?.asImageBitmap(), newSysBmp)

        roles
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (_: Throwable) {
            emptyList()
        }

        val myPackage = context.packageName

        resolveInfos
            .filter { it.activityInfo != null && it.activityInfo.packageName != myPackage }
            .map { resolveInfo ->
                val label = try {
                    resolveInfo.loadLabel(pm)?.toString() ?: resolveInfo.activityInfo.name
                } catch (_: Throwable) {
                    resolveInfo.activityInfo.packageName
                }
                val pkgName = resolveInfo.activityInfo.packageName
                val iconDrawable = try {
                    resolveInfo.loadIcon(pm)
                } catch (_: Throwable) {
                    null
                }
                val iconBmp = iconDrawable?.let { drawableToBitmap(it) }
                val iconImgBmp = iconBmp?.asImageBitmap()

                AppInfo(
                    label = label,
                    packageName = pkgName,
                    iconBitmap = iconBmp,
                    iconImageBitmap = iconImgBmp
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    data class AudioTrackInfo(
        val id: Long,
        val title: String,
        val artist: String,
        val durationMs: Long
    ) {
        val formattedDuration: String
            get() {
                val totalSec = durationMs / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                return String.format("%d:%02d", min, sec)
            }
    }

    suspend fun queryDeviceAudio(context: Context, limit: Int = 50): List<AudioTrackInfo> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrackInfo>()
        try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
            )
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            val cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (c.moveToNext() && tracks.size < limit) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Track"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val duration = c.getLong(durCol)
                    tracks.add(AudioTrackInfo(id, title, artist, duration))
                }
            }
        } catch (_: Throwable) {}
        tracks
    }

    suspend fun queryDeviceImages(context: Context, limit: Int = 20): List<Bitmap> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Bitmap>()
        try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && results.size < limit) {
                    val id = c.getLong(idCol)
                    val itemUri = android.net.Uri.withAppendedPath(uri, id.toString())
                    try {
                        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            context.contentResolver.loadThumbnail(itemUri, android.util.Size(120, 120), null)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Thumbnails.getThumbnail(
                                context.contentResolver,
                                id,
                                MediaStore.Images.Thumbnails.MICRO_KIND,
                                null
                            )
                        }
                        if (bmp != null) results.add(bmp)
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
        results
    }
}
