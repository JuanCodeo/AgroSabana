package co.edu.ucompensar.agrosabana.ui.auth

import android.os.Bundle
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.databinding.ActivityRecoverBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.requireEmail

/** La respuesta es identica exista o no la cuenta (RF-AUT-05). */
class RecoverPasswordActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRecoverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.recover_title)

        binding.sendButton.setOnClickListener {
            if (binding.emailLayout.requireEmail()) binding.sentMessage.isVisible = true
        }
        binding.backButton.setOnClickListener { finish() }
    }
}
