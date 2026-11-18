package co.edu.ucompensar.agrosabana.ui.common

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.GeoPoint
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.databinding.ActivityEditProfileBinding
import co.edu.ucompensar.agrosabana.util.ImageCodec
import co.edu.ucompensar.agrosabana.util.LocationHelper
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.snackbar.Snackbar

/**
 * Actualizacion del perfil propio (RF-PER-02), compartida por los tres roles: datos de contacto,
 * foto (subida a Firebase Storage) y ubicacion principal capturada con el GPS.
 */
class EditProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var user: User
    private var newPhoto: Bitmap? = null
    private var location: GeoPoint? = null

    private val photoPicker = PhotoPicker(this) { photo ->
        newPhoto = photo
        binding.avatar.showAvatar(user, photo)
    }

    private val locationHelper = LocationHelper(
        this,
        onLocation = ::onLocation,
        onDenied = { Snackbar.make(binding.root, R.string.location_denied, Snackbar.LENGTH_LONG).show() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.edit_profile)

        user = session.currentUser!!
        location = user.location
        binding.nameLayout.setText(user.name)
        binding.phoneLayout.setText(user.phone)
        binding.municipalityLayout.setText(user.municipality)
        binding.addressLayout.setText(user.address)
        binding.avatar.showAvatar(user)
        binding.map.show(location, user.name)

        binding.photoButton.setOnClickListener { photoPicker.show() }
        binding.locationButton.setOnClickListener {
            binding.locationButton.isEnabled = false
            locationHelper.request()
        }
        binding.saveButton.setOnClickListener { save() }
    }

    /** Muestra el punto en el mapa y sugiere la direccion por geocodificacion inversa. */
    private fun onLocation(point: GeoPoint) {
        location = point
        binding.locationButton.isEnabled = true
        binding.map.show(point, user.name)
        launchTask {
            LocationHelper.reverseGeocode(this, point)?.let { binding.addressLayout.setText(it) }
            Snackbar.make(binding.root, R.string.location_filled, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        if (!binding.nameLayout.requireValue()) return
        val updated = user.copy(
            name = binding.nameLayout.text,
            phone = binding.phoneLayout.text,
            municipality = binding.municipalityLayout.text,
            address = binding.addressLayout.text,
            location = location
        )
        binding.saveButton.isEnabled = false
        binding.saveButton.setText(R.string.saving)
        launchTask(onError = {
            binding.saveButton.isEnabled = true
            binding.saveButton.setText(R.string.save_changes)
        }) {
            Store.updateProfile(updated, newPhoto)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

/** Avatar circular: la foto recien tomada, la guardada en Storage o el icono por defecto. */
fun ImageView.showAvatar(user: User, localPhoto: Bitmap? = null) {
    val source: Any? = localPhoto
        ?: user.photoUrl?.let { if (ImageCodec.isDataUri(it)) ImageCodec.decode(it) else it }
    if (source == null) {
        setImageResource(R.drawable.ic_person)
        return
    }
    scaleType = ImageView.ScaleType.CENTER_CROP
    imageTintList = null
    setPadding(0, 0, 0, 0)
    load(source) {
        crossfade(true)
        transformations(CircleCropTransformation())
        error(R.drawable.ic_person)
    }
}
