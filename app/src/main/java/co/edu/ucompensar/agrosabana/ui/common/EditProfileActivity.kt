package co.edu.ucompensar.agrosabana.ui.common

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.databinding.ActivityEditProfileBinding

/** Actualizacion del perfil propio, compartida por Comprador y Vendedor (RF-PER-02). */
class EditProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var user: User
    private val photoPicker = PhotoPicker(this) { photo ->
        user.photo = photo
        binding.avatar.showAvatar(user)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.edit_profile)

        user = session.currentUser ?: run { logoutAndExit(); return }
        binding.nameLayout.setText(user.name)
        binding.phoneLayout.setText(user.phone)
        binding.municipalityLayout.setText(user.municipality)
        binding.addressLayout.setText(user.address)
        binding.avatar.showAvatar(user)

        binding.photoButton.setOnClickListener { photoPicker.show() }
        binding.saveButton.setOnClickListener { save() }
    }

    private fun save() {
        if (!binding.nameLayout.requireValue()) return
        user.name = binding.nameLayout.text
        user.phone = binding.phoneLayout.text
        user.municipality = binding.municipalityLayout.text
        user.address = binding.addressLayout.text
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}

fun ImageView.showAvatar(user: User) {
    if (user.photo != null) {
        scaleType = ImageView.ScaleType.CENTER_CROP
        imageTintList = null
        clipToOutline = true
        setPadding(0, 0, 0, 0)
        setImageBitmap(user.photo)
    } else {
        setImageResource(R.drawable.ic_person)
    }
}
