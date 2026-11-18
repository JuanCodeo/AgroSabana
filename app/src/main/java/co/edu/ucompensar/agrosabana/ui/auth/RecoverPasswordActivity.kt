package co.edu.ucompensar.agrosabana.ui.auth

import android.os.Bundle
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.AuthRepository
import co.edu.ucompensar.agrosabana.databinding.ActivityRecoverBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.requireEmail
import co.edu.ucompensar.agrosabana.ui.common.text

/**
 * Recuperacion de contrasena (RF-AUT-05): Firebase envia un enlace para definir una nueva.
 * La respuesta es identica exista o no la cuenta, para no revelar que correos estan registrados.
 */
class RecoverPasswordActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRecoverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.recover_title)

        binding.sendButton.setOnClickListener {
            if (!binding.emailLayout.requireEmail()) return@setOnClickListener
            binding.sendButton.isEnabled = false
            launchTask(onError = { binding.sendButton.isEnabled = true }) {
                AuthRepository.sendPasswordReset(binding.emailLayout.text)
                binding.sentMessage.isVisible = true
                binding.sendButton.isEnabled = true
            }
        }
        binding.backButton.setOnClickListener { finish() }
    }
}
