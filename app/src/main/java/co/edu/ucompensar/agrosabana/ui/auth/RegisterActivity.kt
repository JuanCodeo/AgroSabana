package co.edu.ucompensar.agrosabana.ui.auth

import android.os.Bundle
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.AuthRepository
import co.edu.ucompensar.agrosabana.data.Role
import co.edu.ucompensar.agrosabana.databinding.ActivityRegisterBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.requireEmail
import co.edu.ucompensar.agrosabana.ui.common.requirePassword
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.text
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Registro con seleccion de rol y autorizacion de datos (RF-AUT-01, Ley 1581 de 2012).
 * Crea la cuenta en Firebase Authentication y el perfil en Firestore. El rol Administrador no se
 * puede elegir aqui: solo otro administrador lo asigna.
 */
class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.create_account)

        binding.termsSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) binding.termsError.isVisible = false
        }
        binding.createButton.setOnClickListener { create() }
    }

    private fun create() {
        val valid = listOf(
            binding.nameLayout.requireValue(),
            binding.emailLayout.requireEmail(),
            binding.passwordLayout.requirePassword(),
            binding.municipalityLayout.requireValue()
        ).all { it }
        binding.termsError.isVisible = !binding.termsSwitch.isChecked
        if (!valid || !binding.termsSwitch.isChecked) return

        val isSeller = binding.roleSeller.isChecked
        binding.createButton.isEnabled = false
        launchTask(onError = { binding.createButton.isEnabled = true }) {
            val user = AuthRepository.register(
                binding.nameLayout.text,
                binding.emailLayout.text,
                binding.passwordLayout.text,
                if (isSeller) Role.SELLER else Role.BUYER,
                binding.municipalityLayout.text
            )
            if (user == null) {
                binding.createButton.isEnabled = true
                binding.emailLayout.error = getString(R.string.error_email_taken)
                return@launchTask
            }
            if (isSeller) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.seller_pending_title)
                    .setMessage(R.string.seller_pending_body)
                    .setCancelable(false)
                    .setPositiveButton(R.string.understood) { _, _ -> openHomeFor(user) }
                    .show()
            } else {
                openHomeFor(user)
            }
        }
    }
}
