package co.edu.ucompensar.agrosabana.ui.buyer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.ui.common.ProfileFragment
import co.edu.ucompensar.agrosabana.ui.common.RoleHostActivity

class BuyerActivity : RoleHostActivity() {

    override val bottomMenu = R.menu.menu_bottom_buyer
    override val drawerMenu = R.menu.menu_drawer_buyer
    override val startDestination = R.id.nav_home

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_home -> BuyerHomeFragment()
        R.id.nav_catalog -> CatalogFragment()
        R.id.nav_cart -> CartFragment()
        R.id.nav_orders -> BuyerOrdersFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isReady) openRequestedDestination(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedDestination(intent)
    }

    override fun onResume() {
        super.onResume()
        if (isReady) refreshCartBadge()
    }

    private fun openRequestedDestination(intent: Intent) {
        val destination = intent.getIntExtra(EXTRA_DESTINATION, 0)
        if (destination != 0) {
            intent.removeExtra(EXTRA_DESTINATION)
            selectDestination(destination)
        }
    }

    fun refreshCartBadge() {
        val count = Store.cartCount
        if (count > 0) {
            binding.bottomNav.getOrCreateBadge(R.id.nav_cart).apply {
                number = count
                backgroundColor = getColor(R.color.agro_amber)
                badgeTextColor = getColor(R.color.text_on_amber)
            }
        } else {
            binding.bottomNav.removeBadge(R.id.nav_cart)
        }
    }

    companion object {
        private const val EXTRA_DESTINATION = "destination"

        /** Vuelve al contenedor del comprador abriendo una pestana concreta. */
        fun intentFor(context: Context, destination: Int) =
            Intent(context, BuyerActivity::class.java)
                .putExtra(EXTRA_DESTINATION, destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
