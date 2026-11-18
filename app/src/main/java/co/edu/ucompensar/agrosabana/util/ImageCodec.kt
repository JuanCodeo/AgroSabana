package co.edu.ucompensar.agrosabana.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream

/**
 * Codificacion de fotos como data URI (data:image/jpeg;base64,...) para guardarlas dentro de un
 * documento de Firestore.
 *
 * Es el modo por defecto de la version beta: Firebase Storage exige el plan de pago Blaze, asi que
 * mientras el proyecto no se apruebe las fotos se reducen a [MAX_SIDE] px y se comprimen en JPEG
 * (unos 40-80 KB), muy por debajo del limite de 1 MB por documento. Con USE_FIREBASE_STORAGE=true
 * en local.properties la app vuelve a subirlas a Storage.
 */
object ImageCodec {

    private const val PREFIX = "data:image/jpeg;base64,"
    private const val MAX_SIDE = 640
    private const val QUALITY = 70

    /** Cache en memoria de las imagenes ya decodificadas (hasta ~8 MB). */
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun isDataUri(value: String?) = value?.startsWith(PREFIX) == true

    /** Reduce la foto y la convierte en data URI lista para guardar en Firestore. */
    fun encode(bitmap: Bitmap): String {
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val bytes = ByteArrayOutputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        return PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Decodifica un data URI (con cache); null si el texto no es una imagen valida. */
    fun decode(dataUri: String): Bitmap? {
        val key = dataUri.hashCode().toString() + ":" + dataUri.length
        cache.get(key)?.let { return it }
        val bitmap = runCatching {
            val bytes = Base64.decode(dataUri.removePrefix(PREFIX), Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}
