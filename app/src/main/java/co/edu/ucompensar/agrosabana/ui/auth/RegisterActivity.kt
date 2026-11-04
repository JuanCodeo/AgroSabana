package co.edu.ucompensar.agrosabana.ui.auth

import android.os.Bundle
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Role
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.data.UserStatus
import co.edu.ucompensar.agrosabana.databinding.ActivityRegisterBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.requireEmail
import co.edu.ucompensar.agrosabana.ui.common.requirePassword
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.text
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Registro con seleccion de rol y autorizacion de datos (RF-AUT-01, Ley 1581 de 2012). */
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

        if (Store.emailExists(binding.emailLayout.text)) {
            binding.emailLayout.error = getString(R.string.error_email_taken)
            return
        }

        val isSeller = binding.roleSeller.isChecked
        val user = Store.addUser(
            binding.nameLayout.text,
            binding.emailLayout.text,
            "",
            if (isSeller) Role.SELLER else Role.BUYER,
            binding.municipalityLayout.text,
            if (isSeller) UserStatus.PENDIENTE else UserStatus.ACTIVO
        )
        session.currentUserId = user.id

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
