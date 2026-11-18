package co.edu.ucompensar.agrosabana.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.OrderStatus
import co.edu.ucompensar.agrosabana.data.ProductStatus
import co.edu.ucompensar.agrosabana.data.Session
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.data.UserStatus
import co.edu.ucompensar.agrosabana.databinding.FragmentDashboardBinding
import co.edu.ucompensar.agrosabana.ui.common.ListFragment
import co.edu.ucompensar.agrosabana.ui.common.RoleHostActivity
import co.edu.ucompensar.agrosabana.ui.common.Row
import co.edu.ucompensar.agrosabana.ui.common.SellerProfileFragment
import co.edu.ucompensar.agrosabana.util.Money

private const val LOW_STOCK_THRESHOLD = 5
private const val CHART_DAYS = 7

class SellerActivity : RoleHostActivity() {
    override val bottomMenu = R.menu.menu_bottom_seller
    override val drawerMenu = R.menu.menu_drawer_seller
    override val startDestination = R.id.nav_dashboard

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_dashboard -> SellerDashboardFragment()
        R.id.nav_products -> SellerProductsFragment()
        R.id.nav_received -> SellerOrdersFragment()
        R.id.nav_profile -> SellerProfileFragment()
        else -> null
    }
}

class SellerDashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.chartTitle.setText(R.string.last_days_sales)
        binding.primaryAction.setText(R.string.publish_product)
        binding.primaryAction.setOnClickListener {
            startActivity(Intent(requireContext(), ProductFormActivity::class.java))
        }
        Store.changes.observe(viewLifecycleOwner) { render() }
    }

    /** Indicadores del vendedor calculados con sus productos y pedidos en tiempo real. */
    private fun render() {
        if (_binding == null) return
        val user = Session(requireContext()).currentUser ?: return
        (activity as? SellerActivity)?.setScreenTitle(getString(R.string.hello_seller, user.name))

        val verified = user.status == UserStatus.ACTIVO
        binding.banner.text = getString(if (verified) R.string.verified_title else R.string.pending_title) + ". " +
            getString(if (verified) R.string.verified_body else R.string.pending_body)
        binding.banner.setBackgroundResource(if (verified) R.drawable.bg_banner_tint else R.drawable.bg_banner_warn)

        val products = Store.productsOf(user.id)
        val active = products.filter { it.status == ProductStatus.APROBADO }
        val newOrders = Store.ordersOfSeller(user.id).count { it.status == OrderStatus.PAGADO }
        binding.tile1.label.setText(R.string.month_sales)
        binding.tile1.value.text = Money.format(Store.sellerSalesCents(user.id))
        binding.tile2.label.setText(R.string.new_orders)
        binding.tile2.value.text = newOrders.toString()
        binding.tile3.label.setText(R.string.active_products)
        binding.tile3.value.text = active.size.toString()
        binding.tile4.label.setText(R.string.low_stock)
        binding.tile4.value.text = active.count { it.stock < LOW_STOCK_THRESHOLD }.toString()
        binding.chart.values = Store.dailySales(CHART_DAYS, user.id)
        binding.noticeCard.isVisible = false
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

/** CRUD de productos del vendedor: el listado filtra por dueno (RF-PRO-02). */
class SellerProductsFragment : ListFragment() {

    override val filters = listOf(R.string.tab_active, R.string.tab_pending, R.string.tab_hidden)
    override val emptyMessage = R.string.no_seller_products
    override val searchHint = R.string.search_my_products

    override fun configure() {
        binding.fab.isVisible = true
        binding.fab.setOnClickListener {
            startActivity(Intent(requireContext(), ProductFormActivity::class.java))
        }
    }

    override fun rows(filterIndex: Int, query: String): List<Row> {
        val sellerId = Session(requireContext()).currentUserId
        return Store.productsOf(sellerId)
            .filter { product ->
                val inTab = when (filterIndex) {
                    0 -> product.status == ProductStatus.APROBADO
                    1 -> product.status == ProductStatus.PENDIENTE
                    else -> product.status == ProductStatus.OCULTO || product.status == ProductStatus.RECHAZADO
                }
                inTab && product.name.contains(query, ignoreCase = true)
            }
            .map { product ->
                val (badge, color) = when {
                    product.status != ProductStatus.APROBADO -> product.status.label to product.status.color
                    product.stock == 0 -> R.string.status_sold_out to R.color.state_error
                    product.stock < LOW_STOCK_THRESHOLD -> R.string.status_low_stock to R.color.state_warn
                    else -> product.status.label to product.status.color
                }
                Row(
                    id = product.id,
                    title = product.name,
                    subtitle = getString(R.string.product_row, product.stock, Money.format(product.priceCents)),
                    icon = product.category.illustration,
                    iconBackground = product.category.background,
                    badge = getString(badge),
                    badgeColor = color,
                    photo = product.photoUrl
                )
            }
    }

    override fun onRowClick(row: Row) {
        startActivity(ProductFormActivity.intentFor(requireContext(), row.id))
    }
}

/** Historial de pedidos recibidos: el vendedor solo ve sus items (RF-PED-02). */
class SellerOrdersFragment : ListFragment() {

    override val filters = listOf(R.string.tab_new, R.string.tab_preparing, R.string.tab_sent, R.string.tab_history)
    override val emptyMessage = R.string.no_seller_orders

    override fun configure() {
        binding.banner.isVisible = true
        binding.banner.setText(R.string.only_your_items)
    }

    override fun rows(filterIndex: Int, query: String): List<Row> {
        val sellerId = Session(requireContext()).currentUserId
        return Store.ordersOfSeller(sellerId)
            .filter { order ->
                when (filterIndex) {
                    0 -> order.status == OrderStatus.PAGADO
                    1 -> order.status == OrderStatus.PREPARANDO
                    2 -> order.status == OrderStatus.ENVIADO
                    else -> !order.status.isOpen
                }
            }
            .map { order ->
                val items = order.itemsOf(sellerId)
                Row(
                    id = order.code,
                    title = getString(R.string.order_code, order.code),
                    subtitle = items.joinToString(" · ") { "${it.name} x${it.quantity}" },
                    icon = items.first().category.illustration,
                    iconBackground = items.first().category.background,
                    badge = if (filterIndex == 3) getString(order.status.label) else null,
                    badgeColor = order.status.color,
                    trailing = if (filterIndex == 3) null else Money.format(items.sumOf { it.subtotalCents })
                )
            }
    }

    override fun onRowClick(row: Row) {
        startActivity(SellerOrderDetailActivity.intentFor(requireContext(), row.id))
    }
}
