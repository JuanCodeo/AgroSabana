package co.edu.ucompensar.agrosabana.ui.common

import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.MenuRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.databinding.ActivityRoleHostBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Contenedor de cada rol: Drawer + Bottom Navigation sobre un mismo FrameLayout de fragmentos.
 * Cada rol solo declara sus menus y que fragmento abre cada destino.
 */
abstract class RoleHostActivity : BaseActivity() {

    protected lateinit var binding: ActivityRoleHostBinding

    /** Falso cuando no hay sesion y la actividad se esta cerrando desde onCreate. */
    protected val isReady: Boolean get() = ::binding.isInitialized

    @get:MenuRes protected abstract val bottomMenu: Int
    @get:MenuRes protected abstract val drawerMenu: Int
    protected abstract val startDestination: Int

    protected abstract fun fragmentFor(itemId: Int): Fragment?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = session.currentUser
        if (user == null) {
            // El proceso se reinicio y los datos en memoria ya no incluyen esta cuenta.
            logoutAndExit()
            return
        }
        binding = ActivityRoleHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation(user)
        if (savedInstanceState == null) selectDestination(startDestination)
    }

    private fun setupNavigation(user: User) {
        binding.bottomNav.inflateMenu(bottomMenu)
        binding.navigationView.inflateMenu(drawerMenu)

        val header = binding.navigationView.getHeaderView(0)
        header.findViewById<TextView>(R.id.drawerName).text = user.name
        header.findViewById<TextView>(R.id.drawerRole).setText(user.role.label)

        ActionBarDrawerToggle(
            this, binding.drawer, binding.toolbar, R.string.open_drawer, R.string.close_drawer
        ).apply {
            drawerArrowDrawable.color = getColor(R.color.text_on_primary)
            binding.drawer.addDrawerListener(this)
            syncState()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showDestination(item.itemId, item.title)
            binding.navigationView.setCheckedItem(item.itemId)
            true
        }
        binding.navigationView.setNavigationItemSelectedListener { item ->
            binding.drawer.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_logout -> logoutAndExit()
                R.id.nav_policy -> showDataPolicy()
                else -> selectDestination(item.itemId)
            }
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawer.isDrawerOpen(GravityCompat.START) -> binding.drawer.closeDrawer(GravityCompat.START)
                    binding.bottomNav.selectedItemId != startDestination -> selectDestination(startDestination)
                    else -> finish()
                }
            }
        })
    }

    /** Selecciona un destino como si el usuario tocara la barra inferior. */
    fun selectDestination(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    private fun showDestination(itemId: Int, title: CharSequence?) {
        val fragment = fragmentFor(itemId) ?: return
        binding.toolbar.title = title
        supportFragmentManager.commit { replace(R.id.fragmentContainer, fragment) }
    }

    fun setScreenTitle(title: CharSequence) {
        binding.toolbar.title = title
    }

    private fun showDataPolicy() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nav_policy)
            .setMessage(R.string.data_policy_body)
            .setPositiveButton(R.string.close, null)
            .show()
    }
}
