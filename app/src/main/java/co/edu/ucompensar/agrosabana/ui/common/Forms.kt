package co.edu.ucompensar.agrosabana.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.databinding.DialogInputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.io.File

val TextInputLayout.text: String get() = editText?.text?.toString()?.trim().orEmpty()

fun TextInputLayout.setText(value: String?) {
    editText?.setText(value)
}

/** Marca el campo con error y devuelve false si esta vacio. */
fun TextInputLayout.requireValue(): Boolean {
    error = if (text.isEmpty()) context.getString(R.string.error_required) else null
    return error == null
}

fun TextInputLayout.requireEmail(): Boolean {
    error = when {
        text.isEmpty() -> context.getString(R.string.error_required)
        !Patterns.EMAIL_ADDRESS.matcher(text).matches() -> context.getString(R.string.error_email)
        else -> null
    }
    return error == null
}

/** RF-AUT-01: minimo 8 caracteres, con letras y numeros. */
fun TextInputLayout.requirePassword(): Boolean {
    val ok = text.length >= 8 && text.any(Char::isLetter) && text.any(Char::isDigit)
    error = if (ok) null else context.getString(R.string.error_password_rule)
    return ok
}

fun AutoCompleteTextView.setOptions(options: List<String>) {
    setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, options))
}

/** Dialogo de un campo; onConfirm recibe el texto y no se llama si esta vacio. */
fun Context.promptText(
    @StringRes title: Int,
    @StringRes hint: Int,
    @StringRes action: Int,
    password: Boolean = false,
    onConfirm: (String) -> Unit
) {
    val binding = DialogInputBinding.inflate(LayoutInflater.from(this))
    binding.inputLayout.setHint(hint)
    if (password) {
        binding.input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(binding.root)
        .setPositiveButton(action, null)
        .setNegativeButton(R.string.cancel, null)
        .show()
    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        if (binding.inputLayout.requireValue()) {
            dialog.dismiss()
            onConfirm(binding.inputLayout.text)
        }
    }
}

/**
 * Foto desde la camara o desde el Photo Picker del sistema (galeria).
 *
 * La camara guarda la foto en resolucion completa en un archivo temporal compartido con
 * FileProvider; luego se reduce a un maximo de [MAX_SIDE] px para subirla rapido a Firebase Storage.
 * Ninguna de las dos opciones requiere permisos en el manifiesto.
 */
class PhotoPicker(private val activity: ComponentActivity, private val onPhoto: (Bitmap) -> Unit) {

    private var pendingCameraUri: Uri? = null

    private val camera = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) loadFromUri(uri)
    }

    private val gallery = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::loadFromUri)
    }

    fun show() {
        val options = arrayOf(activity.getString(R.string.photo_camera), activity.getString(R.string.photo_gallery))
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.photo_source)
            .setItems(options) { _, which ->
                if (which == 0) launchCamera()
                else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .show()
    }

    private fun launchCamera() {
        try {
            val dir = File(activity.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
            pendingCameraUri = uri
            camera.launch(uri)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, R.string.camera_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    /** Decodifica la imagen ya reducida; ImageDecoder (Android 9+) respeta la rotacion EXIF. */
    private fun loadFromUri(uri: Uri) {
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(activity.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val scale = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
                    if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
                activity.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        }.getOrNull()
        if (bitmap != null) onPhoto(bitmap)
        else Toast.makeText(activity, R.string.photo_error, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val MAX_SIDE = 1280
    }
}
