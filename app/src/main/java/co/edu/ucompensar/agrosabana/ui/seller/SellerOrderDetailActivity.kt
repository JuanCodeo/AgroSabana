package co.edu.ucompensar.agrosabana.ui.seller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Order
import co.edu.ucompensar.agrosabana.data.OrderStatus
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.databinding.ActivitySellerOrderDetailBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.MapPreview
import co.edu.ucompensar.agrosabana.ui.common.Row
import co.edu.ucompensar.agrosabana.ui.common.RowAdapter
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.promptText
import co.edu.ucompensar.agrosabana.util.Money
import com.google.android.material.snackbar.Snackbar

/**
 * Detalle de un pedido recibido (RF-PED-03): muestra solo los items del vendedor, el punto de
 * entrega en Google Maps y permite avanzar el pedido por su maquina de estados.
 */
class SellerOrderDetailActivity : BaseActivity() {

    private lateinit var binding: ActivitySellerOrderDetailBinding
    private lateinit var code: String
    private val adapter = RowAdapter()
    private val flow = listOf(OrderStatus.PAGADO, OrderStatus.PREPARANDO, OrderStatus.ENVIADO, OrderStatus.ENTREGADO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivitySellerOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.order_title)

        code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        binding.items.layoutManager = LinearLayoutManager(this)
        binding.items.adapter = adapter
        binding.advanceButton.setOnClickListener { Store.order(code)?.let(::advance) }
        binding.problemButton.setOnClickListener { Store.order(code)?.let(::reportProblem) }
        // Se redibuja con cada cambio del pedido en Firestore.
        Store.changes.observe(this) { render() }
    }

    private fun render() {
        val order = Store.order(code) ?: return finish()
        val sellerId = session.currentUserId
        binding.code.text = getString(R.string.order_code, order.code)
        binding.paidOn.text = order.date
        binding.deliveryTo.text = order.address
        binding.map.show(order.location, order.buyerName)
        binding.directionsButton.isVisible = order.location != null
        binding.directionsButton.setOnClickListener {
            order.location?.let { MapPreview.openInMaps(this, it, order.address) }
        }

        adapter.submitList(order.itemsOf(sellerId).mapIndexed { index, item ->
            Row(
                id = index.toString(),
                title = item.name,
                subtitle = resources.getQuantityString(R.plurals.units, item.quantity, item.quantity),
                icon = item.category.illustration,
                iconBackground = item.category.background,
                trailing = Money.format(item.subtotalCents)
            )
        })

        binding.stateValue.setText(order.status.label)
        binding.stepper.setSteps(
            flow.map { getString(it.label).lowercase().replaceFirstChar(Char::uppercase) },
            flow.indexOf(order.status).coerceAtLeast(0)
        )
        val next = order.status.next()
        binding.advanceButton.isVisible = next != null
        if (next != null) {
            binding.advanceButton.text = getString(R.string.mark_as, getString(next.label).lowercase())
        }
        binding.problemButton.isVisible = order.status.isOpen
    }

    private fun advance(order: Order) {
        val next = order.status.next() ?: return
        binding.advanceButton.isEnabled = false
        launchTask(onError = { binding.advanceButton.isEnabled = true }) {
            Store.setOrderStatus(order, next)
            binding.advanceButton.isEnabled = true
        }
    }

    private fun reportProblem(order: Order) {
        promptText(R.string.report_problem, R.string.problem_hint, R.string.report_problem) { reason ->
            launchTask {
                Store.setOrderStatus(order, OrderStatus.DISPUTA, reason)
                Snackbar.make(binding.root, R.string.problem_reported, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val EXTRA_CODE = "order_code"

        fun intentFor(context: Context, code: String) =
            Intent(context, SellerOrderDetailActivity::class.java).putExtra(EXTRA_CODE, code)
    }
}
