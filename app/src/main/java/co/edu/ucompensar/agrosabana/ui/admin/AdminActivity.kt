package co.edu.ucompensar.agrosabana.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Category
import co.edu.ucompensar.agrosabana.data.Order
import co.edu.ucompensar.agrosabana.data.OrderStatus
import co.edu.ucompensar.agrosabana.data.ProductStatus
import co.edu.ucompensar.agrosabana.data.Role
import co.edu.ucompensar.agrosabana.data.Session
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.data.UserStatus
import co.edu.ucompensar.agrosabana.databinding.FragmentDashboardBinding
import co.edu.ucompensar.agrosabana.databinding.FragmentSalesReportBinding
import co.edu.ucompensar.agrosabana.ui.common.ListFragment
import co.edu.ucompensar.agrosabana.ui.common.RoleHostActivity
import co.edu.ucompensar.agrosabana.ui.common.Row
import co.edu.ucompensar.agrosabana.ui.common.RowAdapter
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.promptText
import co.edu.ucompensar.agrosabana.util.Money
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

private const val CHART_DAYS = 7

class AdminActivity : RoleHostActivity() {
    override val bottomMenu = R.menu.menu_bottom_admin
    override val drawerMenu = R.menu.menu_drawer_admin
    override val startDestination = R.id.nav_dashboard

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_dashboard -> AdminDashboardFragment()
        R.id.nav_users -> AdminUsersFragment()
        R.id.nav_products -> AdminProductsFragment()
        R.id.nav_sales -> SalesReportFragment()
        else -> null
    }
}

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.banner.isVisible = false
        binding.chartTitle.setText(R.string.orders_per_day)
        binding.primaryAction.setText(R.string.view_audit)
        binding.primaryAction.setOnClickListener { showAudit() }
        binding.noticeCard.setOnClickListener {
            (activity as AdminActivity).selectDestination(R.id.nav_users)
        }
        Store.changes.observe(viewLifecycleOwner) { render() }
    }

    override fun onResume() {
        super.onResume()
        (activity as? AdminActivity)?.setScreenTitle(getString(R.string.administration))
    }

    /** Indicadores globales recalculados con cada cambio en Firestore. */
    private fun render() {
        if (_binding == null) return
        val pendingSellers = Store.users.count { it.role == Role.SELLER && it.status == UserStatus.PENDIENTE }
        binding.tile1.label.setText(R.string.users)
        binding.tile1.value.text = Store.users.size.toString()
        binding.tile2.label.setText(R.string.pending_sellers)
        binding.tile2.value.text = pendingSellers.toString()
        binding.tile3.label.setText(R.string.total_sales)
        binding.tile3.value.text = Money.format(Store.salesTotalCents)
        binding.tile4.label.setText(R.string.pending_products)
        binding.tile4.value.text = Store.products.count { it.status == ProductStatus.PENDIENTE }.toString()
        binding.noticeCard.isVisible = pendingSellers > 0
        binding.noticeTitle.text = resources.getQuantityString(R.plurals.seller_requests, pendingSellers, pendingSellers)
        binding.chart.values = Store.dailySales(CHART_DAYS)
    }

    private fun showAudit() {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.audit_title)
            .setPositiveButton(R.string.close, null)
        if (Store.auditLog.isEmpty()) builder.setMessage(R.string.audit_empty)
        else builder.setItems(Store.auditLog.map { it.toString() }.toTypedArray(), null)
        builder.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

/** Listado de usuarios con filtros y correo enmascarado (RF-ADU-01). */
class AdminUsersFragment : ListFragment() {

    override val filters = listOf(R.string.tab_all, R.string.tab_buyers, R.string.tab_sellers)
    override val emptyMessage = R.string.no_users
    override val searchHint = R.string.search_users

    override fun configure() {
        binding.fab.isVisible = true
        binding.fab.contentDescription = getString(R.string.new_user)
        binding.fab.setOnClickListener { startActivity(Intent(requireContext(), UserFormActivity::class.java)) }
    }

    override fun rows(filterIndex: Int, query: String): List<Row> =
        Store.users
            .filter { user ->
                val inTab = when (filterIndex) {
                    1 -> user.role == Role.BUYER
                    2 -> user.role == Role.SELLER
                    else -> true
                }
                inTab && (user.name.contains(query, ignoreCase = true) || user.email.contains(query, ignoreCase = true))
            }
            .map { user ->
                Row(
                    id = user.id,
                    title = user.name,
                    subtitle = getString(R.string.user_row, Store.maskEmail(user.email), getString(user.role.label)),
                    icon = R.drawable.ic_person,
                    iconBackground = R.color.agro_slate_soft,
                    badge = getString(user.status.label),
                    badgeColor = user.status.color,
                    photo = user.photoUrl
                )
            }

    override fun onRowClick(row: Row) {
        startActivity(UserFormActivity.intentFor(requireContext(), row.id))
    }
}

/** Moderacion de publicaciones y categorias (RF-ADP-01, RF-ADP-02). */
class AdminProductsFragment : ListFragment() {

    private val statuses = listOf(
        ProductStatus.PENDIENTE, ProductStatus.APROBADO, ProductStatus.OCULTO, ProductStatus.RECHAZADO
    )
    override val filters = listOf(R.string.tab_pending, R.string.tab_approved, R.string.tab_hidden, R.string.tab_rejected)
    override val emptyMessage = R.string.no_admin_products

    override fun configure() {
        binding.bottomAction.isVisible = true
        binding.bottomAction.setText(R.string.manage_categories)
        binding.bottomAction.setOnClickListener { manageCategories() }
    }

    override fun rows(filterIndex: Int, query: String): List<Row> =
        Store.products.filter { it.status == statuses[filterIndex] }.map { product ->
            Row(
                id = product.id,
                title = product.name,
                subtitle = getString(R.string.admin_product_row, product.sellerName, getString(product.category.label)),
                icon = product.category.illustration,
                iconBackground = product.category.background,
                badge = getString(product.status.label),
                badgeColor = product.status.color,
                photo = product.photoUrl
            )
        }

    override fun onRowClick(row: Row) {
        startActivity(ProductReviewActivity.intentFor(requireContext(), row.id))
    }

    private fun manageCategories() {
        val categories = Category.entries
        val names = categories.map { getString(it.label) }.toTypedArray()
        val active = categories.map { it !in Store.inactiveCategories }.toBooleanArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.manage_categories)
            .setMultiChoiceItems(names, active) { _, which, checked -> active[which] = checked }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val inactive = categories.filterIndexed { index, _ -> !active[index] }.toSet()
                launchTask {
                    Store.setInactiveCategories(inactive)
                    Snackbar.make(binding.root, R.string.categories_hint, Snackbar.LENGTH_LONG).show()
                }
            }
            .show()
    }
}

/** Reporte de ventas con indicadores, vendedores destacados y supervision de compras. */
class SalesReportFragment : Fragment() {

    private var _binding: FragmentSalesReportBinding? = null
    private val binding get() = _binding!!
    private val sellersAdapter = RowAdapter()
    private val ordersAdapter = RowAdapter { row -> Store.order(row.id)?.let(::confirmVoid) }
    private var filter = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSalesReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.topSellers.layoutManager = LinearLayoutManager(requireContext())
        binding.topSellers.adapter = sellersAdapter
        binding.orders.layoutManager = LinearLayoutManager(requireContext())
        binding.orders.adapter = ordersAdapter

        val ids = listOf(R.string.tab_all_f, R.string.tab_paid, R.string.tab_dispute).mapIndexed { index, label ->
            Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(label)
                isCheckable = true
                isChecked = index == 0
            }.also(binding.chips::addView).id
        }
        binding.chips.setOnCheckedStateChangeListener { _, checked ->
            filter = ids.indexOf(checked.firstOrNull()).coerceAtLeast(0)
            refresh()
        }
        binding.exportButton.setOnClickListener { export() }
        Store.changes.observe(viewLifecycleOwner) { refresh() }
    }

    override fun onResume() {
        super.onResume()
        (activity as? AdminActivity)?.setScreenTitle(getString(R.string.sales_report))
    }

    private fun refresh() {
        if (_binding == null) return
        binding.tile1.label.setText(R.string.total_sales)
        binding.tile1.value.text = Money.format(Store.salesTotalCents)
        binding.tile2.label.setText(R.string.sales_count)
        binding.tile2.value.text = Store.salesCount.toString()
        binding.tile3.label.setText(R.string.avg_ticket)
        binding.tile3.value.text = Money.format(Store.averageTicketCents)
        binding.tile4.label.setText(R.string.commission)
        binding.tile4.value.text = Money.format(Store.commissionCents)

        sellersAdapter.submitList(Store.topSellers().map { (seller, cents) ->
            Row(
                id = seller.id,
                title = seller.name,
                subtitle = seller.municipality,
                icon = R.drawable.ic_person,
                iconBackground = R.color.agro_slate_soft,
                trailing = Money.format(cents)
            )
        })

        val orders = Store.orders.filter { it.status != OrderStatus.PENDIENTE_PAGO }.filter {
            when (filter) {
                1 -> it.status == OrderStatus.PAGADO
                2 -> it.status == OrderStatus.DISPUTA
                else -> true
            }
        }
        ordersAdapter.submitList(orders.map { order ->
            Row(
                id = order.code,
                title = getString(R.string.order_code, order.code),
                subtitle = getString(R.string.buyer_total, order.buyerName, Money.format(order.totalCents)),
                icon = order.items.first().category.illustration,
                iconBackground = order.items.first().category.background,
                badge = getString(order.status.label),
                badgeColor = order.status.color
            )
        })
        binding.empty.isVisible = orders.isEmpty()
    }

    /** Anulacion por fraude o disputa: nunca hay borrado fisico (RF-PED-04). */
    private fun confirmVoid(order: Order) {
        if (order.status == OrderStatus.ANULADO) return
        requireContext().promptText(R.string.void_order, R.string.void_hint, R.string.void_order) { reason ->
            val admin = Session(requireContext()).currentUser?.name.orEmpty()
            launchTask {
                Store.setOrderStatus(order, OrderStatus.ANULADO)
                Store.audit(admin, getString(R.string.audit_user, getString(R.string.audit_action_void), order.code, reason))
                Snackbar.make(binding.root, R.string.order_voided, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun export() {
        val csv = buildString {
            appendLine("pedido,comprador,fecha,estado,total_cop,referencia_epayco")
            Store.orders.forEach {
                appendLine("${it.code},${it.buyerName},${it.date},${getString(it.status.label)},${it.totalCents / 100},${it.payment?.reference.orEmpty()}")
            }
        }
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_subject))
            .putExtra(Intent.EXTRA_TEXT, csv)
        startActivity(Intent.createChooser(share, getString(R.string.export_report)))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
