package co.edu.ucompensar.agrosabana.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.databinding.ActivityLoginBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.Biometrics
import co.edu.ucompensar.agrosabana.ui.common.requireEmail
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.text
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener { login() }
        binding.forgotButton.setOnClickListener { startActivity(Intent(this, RecoverPasswordActivity::class.java)) }
        binding.registerButton.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        binding.googleButton.setOnClickListener {
            Snackbar.make(binding.root, R.string.google_backend, Snackbar.LENGTH_LONG).show()
        }
        binding.fingerprintButton.setOnClickListener { loginWithFingerprint() }
    }

    override fun onResume() {
        super.onResume()
        // RF-AUT-04: la huella solo aparece si el usuario la activo despues de iniciar sesion.
        binding.fingerprintButton.isVisible = session.biometricEnabled
    }

    private fun login() {
        val emailOk = binding.emailLayout.requireEmail()
        val passwordOk = binding.passwordLayout.requireValue()
        if (!emailOk || !passwordOk) return

        val user = Store.authenticate(binding.emailLayout.text, binding.passwordLayout.text)
        if (user == null) {
            Snackbar.make(binding.root, R.string.login_error, Snackbar.LENGTH_LONG).show()
            return
        }
        session.currentUserId = user.id
        if (!session.biometricEnabled && Biometrics.isAvailable(this)) offerFingerprint(user) else openHomeFor(user)
    }

    private fun offerFingerprint(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.biometric_offer_title)
            .setMessage(R.string.biometric_offer_body)
            .setCancelable(false)
            .setPositiveButton(R.string.biometric_activate) { _, _ ->
                session.biometricUserId = user.id
                openHomeFor(user)
            }
            .setNegativeButton(R.string.biometric_not_now) { _, _ -> openHomeFor(user) }
            .show()
    }

    private fun loginWithFingerprint() {
        if (!Biometrics.isAvailable(this)) {
            Snackbar.make(binding.root, R.string.biometric_unavailable, Snackbar.LENGTH_LONG).show()
            return
        }
        Biometrics.authenticate(
            this,
            onSuccess = {
                val user = Store.user(session.biometricUserId)
                if (user != null) {
                    session.currentUserId = user.id
                    openHomeFor(user)
                } else {
                    session.disableBiometric()
                    binding.fingerprintButton.isVisible = false
                    Snackbar.make(binding.root, R.string.biometric_error, Snackbar.LENGTH_LONG).show()
                }
            },
            onError = { Snackbar.make(binding.root, R.string.biometric_error, Snackbar.LENGTH_LONG).show() }
        )
    }
}
