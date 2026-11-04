package co.edu.ucompensar.agrosabana.ui.buyer

import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Order
import co.edu.ucompensar.agrosabana.data.OrderStatus
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.ListFragment
import co.edu.ucompensar.agrosabana.ui.common.Row
import co.edu.ucompensar.agrosabana.data.Session
import co.edu.ucompensar.agrosabana.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Historial de compras con cancelacion antes del envio y retracto (RF-PED-02, RF-PAG-05). */
class BuyerOrdersFragment : ListFragment() {

    override val filters = listOf(R.string.tab_in_progress, R.string.tab_delivered, R.string.tab_cancelled)
    override val emptyMessage = R.string.no_orders

    override fun configure() {
        binding.banner.isVisible = true
        binding.banner.text = getString(R.string.retract_title) + ". " + getString(R.string.retract_body)
    }

    override fun rows(filterIndex: Int, query: String): List<Row> {
        val userId = Session(requireContext()).currentUserId
        return Store.ordersOfBuyer(userId)
            .filter { order ->
                when (filterIndex) {
                    0 -> order.status.isOpen || order.status == OrderStatus.DISPUTA
                    1 -> order.status == OrderStatus.ENTREGADO
                    else -> order.status == OrderStatus.CANCELADO || order.status == OrderStatus.ANULADO || order.status == OrderStatus.RETRACTO
                }
            }
            .map { order ->
                Row(
                    id = order.code,
                    title = getString(R.string.order_code, order.code),
                    subtitle = getString(
                        R.string.order_sellers_total,
                        resources.getQuantityString(R.plurals.sellers, order.sellerCount, order.sellerCount),
                        Money.format(order.totalCents)
                    ),
                    icon = order.items.first().category.illustration,
                    iconBackground = order.items.first().category.background,
                    badge = getString(order.status.label),
                    badgeColor = order.status.color
                )
            }
    }

    override fun onRowClick(row: Row) {
        val order = Store.order(row.id) ?: return
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(row.title)
            .setMessage(requireContext().orderSummary(order))
            .setPositiveButton(R.string.close, null)
        when (order.status) {
            OrderStatus.PAGADO, OrderStatus.PREPARANDO ->
                dialog.setNegativeButton(R.string.cancel_order) { _, _ ->
                    update(order, OrderStatus.CANCELADO, R.string.order_cancelled)
                }
            OrderStatus.ENTREGADO ->
                dialog.setNegativeButton(R.string.retract_action) { _, _ ->
                    update(order, OrderStatus.RETRACTO, R.string.retract_requested)
                }
            else -> Unit
        }
        dialog.show()
    }

    private fun update(order: Order, status: OrderStatus, message: Int) {
        order.status = status
        refresh()
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
