package dev.franklyn.foldscenes.wallpaper

import android.app.WallpaperManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dev.franklyn.foldscenes.scene.Scene

/** Applies a scene as a static wallpaper: inner display via WallpaperManager, cover via Motorola's editor. */
object WallpaperApplier {
    const val INNER_WIDTH = 1224
    const val INNER_HEIGHT = 2992
    const val COVER_WIDTH = 1080
    const val COVER_HEIGHT = 1272

    const val COVER_EDITOR_ACTION = "com.motorola.intent.action.EDIT_SECONDARY_WALLPAPER"
    private const val PNG = "image/png"
    private const val FOLDER = "RazrFoldScenes"
    private val RELATIVE_PATH = Environment.DIRECTORY_PICTURES + "/" + FOLDER

    fun coverFileName(sceneId: String): String =
        "razrfold_${sceneId.replace(Regex("[^A-Za-z0-9_]"), "_")}_cover.png"

    /** Renders the scene flat (p = 1) at inner-display size and sets it as the system wallpaper. */
    fun applyInner(context: Context, scene: Scene): Result<Unit> = runCatching {
        val bitmap = SceneBitmap.render(scene, INNER_WIDTH, INNER_HEIGHT, 1f)
        try {
            WallpaperManager.getInstance(context)
                .setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            Unit
        } finally {
            bitmap.recycle()
        }
    }

    /** Saves the closed (p = 0) cover frame to Pictures/RazrFoldScenes and opens Motorola's cover editor on it. */
    fun applyCover(context: Context, scene: Scene): Result<Uri> {
        val uri = try {
            saveCoverFrame(context, scene)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val intent = Intent(COVER_EDITOR_ACTION).apply {
            setDataAndType(uri, PNG)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            return Result.failure(
                IllegalStateException("Motorola cover editor not found. Frame saved in Pictures/$FOLDER."),
            )
        }
        return try {
            context.startActivity(intent)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Renders the cover frame and stores it in MediaStore, replacing any earlier row with the same name. */
    fun saveCoverFrame(context: Context, scene: Scene): Uri {
        val name = coverFileName(scene.id)
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "$RELATIVE_PATH%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                resolver.delete(ContentUris.withAppendedId(collection, c.getLong(0)), null, null)
            }
        }

        val bitmap = SceneBitmap.render(scene, COVER_WIDTH, COVER_HEIGHT, 0f)
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, PNG)
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
            try {
                val out = resolver.openOutputStream(uri) ?: error("cannot open $uri")
                out.use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) { "PNG compress failed" } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        } finally {
            bitmap.recycle()
        }
    }
}
