package co.edu.ucompensar.agrosabana.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.databinding.DialogInputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

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

/** Foto desde la camara o el Photo Picker del sistema; ninguno requiere permisos en el manifiesto. */
class PhotoPicker(private val activity: ComponentActivity, private val onPhoto: (Bitmap) -> Unit) {

    private val camera = activity.registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let(onPhoto)
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
            camera.launch(null)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, R.string.camera_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadFromUri(uri: Uri) {
        val bitmap = activity.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
        }
        if (bitmap != null) onPhoto(bitmap)
        else Toast.makeText(activity, R.string.photo_error, Toast.LENGTH_LONG).show()
    }
}
