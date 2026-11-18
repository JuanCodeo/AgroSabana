package co.edu.ucompensar.agrosabana.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.AuthRepository
import co.edu.ucompensar.agrosabana.data.LoginResult
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.databinding.ActivityLoginBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.Biometrics
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.requireEmail
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.text
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Inicio de sesion con correo y contrasena (Firebase Authentication) o con huella (RF-AUT-02, RF-AUT-04). */
class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener { login() }
        binding.forgotButton.setOnClickListener { startActivity(Intent(this, RecoverPasswordActivity::class.java)) }
        binding.registerButton.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        binding.fingerprintButton.setOnClickListener { loginWithFingerprint() }
    }

    override fun onResume() {
        super.onResume()
        // La huella solo aparece si el usuario la activo antes en este dispositivo.
        binding.fingerprintButton.isVisible = Biometrics.isEnabled(this)
    }

    private fun setBusy(busy: Boolean) {
        binding.loginButton.isEnabled = !busy
        binding.fingerprintButton.isEnabled = !busy
        binding.loginButton.setText(if (busy) R.string.processing else R.string.login)
    }

    private fun login() {
        val emailOk = binding.emailLayout.requireEmail()
        val passwordOk = binding.passwordLayout.requireValue()
        if (!emailOk || !passwordOk) return
        signIn(binding.emailLayout.text, binding.passwordLayout.text, offerBiometric = true)
    }

    /** Autentica en Firebase y, si todo sale bien, abre el inicio del rol. */
    private fun signIn(email: String, password: String, offerBiometric: Boolean) {
        setBusy(true)
        launchTask(onError = { setBusy(false) }) {
            when (val result = AuthRepository.login(email, password)) {
                is LoginResult.Success -> {
                    if (offerBiometric && !Biometrics.isEnabled(this) && Biometrics.isAvailable(this)) {
                        offerFingerprint(result.user, email, password)
                    } else {
                        openHomeFor(result.user)
                    }
                }
                LoginResult.InvalidCredentials -> {
                    setBusy(false)
                    if (!offerBiometric) {
                        // La contrasena cambio despues de activar la huella: hay que volver a activarla.
                        Biometrics.disable(this)
                        binding.fingerprintButton.isVisible = false
                    }
                    Snackbar.make(binding.root, R.string.login_error, Snackbar.LENGTH_LONG).show()
                }
                LoginResult.Blocked -> {
                    setBusy(false)
                    Snackbar.make(binding.root, R.string.account_blocked, Snackbar.LENGTH_LONG).show()
                }
                is LoginResult.Error -> {
                    setBusy(false)
                    Snackbar.make(binding.root, R.string.error_network, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Despues del primer ingreso se ofrece activar la huella (requiere confirmarla con el sensor). */
    private fun offerFingerprint(user: User, email: String, password: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.biometric_offer_title)
            .setMessage(R.string.biometric_offer_body)
            .setCancelable(false)
            .setPositiveButton(R.string.biometric_activate) { _, _ ->
                Biometrics.enable(this, email, password) { enabled ->
                    if (enabled) openHomeFor(user)
                    else {
                        Snackbar.make(binding.root, R.string.biometric_error, Snackbar.LENGTH_SHORT).show()
                        openHomeFor(user)
                    }
                }
            }
            .setNegativeButton(R.string.biometric_not_now) { _, _ -> openHomeFor(user) }
            .show()
    }

    private fun loginWithFingerprint() {
        if (!Biometrics.isAvailable(this)) {
            Snackbar.make(binding.root, R.string.biometric_unavailable, Snackbar.LENGTH_LONG).show()
            return
        }
        Biometrics.unlock(
            this,
            onCredentials = { email, password -> signIn(email, password, offerBiometric = false) },
            onInvalidated = {
                binding.fingerprintButton.isVisible = false
                Snackbar.make(binding.root, R.string.biometric_invalidated, Snackbar.LENGTH_LONG).show()
            },
            onError = { Snackbar.make(binding.root, R.string.biometric_error, Snackbar.LENGTH_LONG).show() }
        )
    }
}
