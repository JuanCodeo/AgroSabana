package co.edu.ucompensar.agrosabana.ui.common

import android.content.Intent
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Role
import co.edu.ucompensar.agrosabana.data.Session
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.ui.admin.AdminActivity
import co.edu.ucompensar.agrosabana.ui.auth.LoginActivity
import co.edu.ucompensar.agrosabana.ui.buyer.BuyerActivity
import co.edu.ucompensar.agrosabana.ui.seller.SellerActivity

/** Comportamiento comun: barra superior, margenes de sistema y navegacion por rol. */
abstract class BaseActivity : AppCompatActivity() {

    protected val session: Session by lazy { Session(this) }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        applySystemInsets()
    }

    /** Desde Android 15 la app dibuja bajo las barras del sistema: se reserva ese espacio. */
    private fun applySystemInsets() {
        val content = findViewById<View>(android.R.id.content)
        val toolbar = findViewById<View?>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            toolbar?.updatePadding(top = bars.top)
            view.updatePadding(
                left = bars.left,
                top = if (toolbar == null) bars.top else 0,
                right = bars.right,
                bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    protected fun setupBackToolbar(toolbar: Toolbar, @StringRes title: Int) {
        toolbar.setTitle(title)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationContentDescription(R.string.back)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    /** Abre el inicio del rol y borra la pila de pantallas de autenticacion (RF-INI-01). */
    protected fun openHomeFor(user: User) {
        val target = when (user.role) {
            Role.BUYER -> BuyerActivity::class.java
            Role.SELLER -> SellerActivity::class.java
            Role.ADMIN -> AdminActivity::class.java
        }
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    fun logoutAndExit() {
        session.logout()
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}
