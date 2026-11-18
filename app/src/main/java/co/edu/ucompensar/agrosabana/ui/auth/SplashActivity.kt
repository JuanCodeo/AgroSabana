package co.edu.ucompensar.agrosabana.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.AuthRepository
import co.edu.ucompensar.agrosabana.data.LoginResult
import co.edu.ucompensar.agrosabana.databinding.ActivitySplashBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.launchTask

/**
 * Punto de entrada unico (RF-INI-01): si Firebase conserva una sesion, carga el perfil desde
 * Firestore y abre el inicio del rol; si no, muestra el inicio de sesion.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)
        route()
    }

    private fun route() = launchTask(onError = { openLogin() }) {
        when (val result = AuthRepository.restore()) {
            is LoginResult.Success -> openHomeFor(result.user)
            LoginResult.Blocked -> {
                Toast.makeText(this, R.string.account_blocked, Toast.LENGTH_LONG).show()
                openLogin()
            }
            is LoginResult.Error -> {
                // Sin conexion al abrir: se informa y se deja intentar desde el login.
                Toast.makeText(this, R.string.error_network, Toast.LENGTH_LONG).show()
                openLogin()
            }
            else -> openLogin()
        }
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
