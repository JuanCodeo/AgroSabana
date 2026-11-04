package co.edu.ucompensar.agrosabana.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import co.edu.ucompensar.agrosabana.databinding.ActivitySplashBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity

/** Punto de entrada unico: valida si hay sesion y redirige segun el rol (RF-INI-01). */
@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val route = Runnable {
        val user = session.currentUser
        if (user != null) {
            openHomeFor(user)
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)
    }

    override fun onStart() {
        super.onStart()
        handler.postDelayed(route, CHECK_DELAY_MS)
    }

    override fun onStop() {
        handler.removeCallbacks(route)
        super.onStop()
    }

    private companion object {
        const val CHECK_DELAY_MS = 900L
    }
}
