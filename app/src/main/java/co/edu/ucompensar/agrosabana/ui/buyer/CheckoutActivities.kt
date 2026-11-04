package co.edu.ucompensar.agrosabana.ui.buyer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.addCallback
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Order
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.databinding.ActivityCheckoutBinding
import co.edu.ucompensar.agrosabana.databinding.ActivityPaymentBinding
import co.edu.ucompensar.agrosabana.databinding.ActivityPaymentResultBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.StepperView
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.setText
import co.edu.ucompensar.agrosabana.ui.common.text
import co.edu.ucompensar.agrosabana.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

private fun StepperView.showCheckoutStep(step: Int) {
    setSteps(
        listOf(R.string.step_cart, R.string.step_delivery, R.string.step_payment, R.string.step_done)
            .map(context::getString),
        step
    )
}

/** Resumen previo al pago con proveedor, total y retracto (RF-PAG-01, Ley 1480 de 2011). */
class CheckoutActivity : BaseActivity() {

    private lateinit var binding: ActivityCheckoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.delivery_title)
        binding.stepper.showCheckoutStep(1)

        val user = session.currentUser ?: run { logoutAndExit(); return }
        binding.addressLayout.setText(user.address)
        binding.subtotal.text = Money.format(Store.cartSubtotalCents)
        binding.shipping.text = Money.format(Store.cartShippingCents)
        binding.total.text = Money.format(Store.cartTotalCents)
        binding.sellers.text = getString(
            R.string.seller_identity,
            Store.cart.map { it.product.sellerName }.distinct().joinToString(", ")
        )

        binding.locationButton.setOnClickListener {
            binding.addressLayout.setText(DEMO_LOCATION)
            Snackbar.make(binding.root, R.string.location_filled, Snackbar.LENGTH_SHORT).show()
        }
        binding.termsSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) binding.termsError.isVisible = false
        }
        binding.payButton.setOnClickListener { goToPayment() }
    }

    private fun goToPayment() {
        val addressOk = binding.addressLayout.requireValue()
        binding.termsError.isVisible = !binding.termsSwitch.isChecked
        if (!addressOk || !binding.termsSwitch.isChecked) return
        startActivity(PaymentActivity.intentFor(this, binding.addressLayout.text))
    }

    private companion object {
        const val DEMO_LOCATION = "Cra 15 # 8-21, Funza"
    }
}

/**
 * Pasarela simulada: solo elige el medio de pago y nunca captura datos de tarjeta (RF-PAG-02).
 * En el backend esta pantalla abre el checkout tokenizado y el pedido se confirma por webhook.
 */
class PaymentActivity : BaseActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.gateway_title)
        binding.stepper.showCheckoutStep(2)

        val total = Money.format(Store.cartTotalCents)
        binding.total.text = total
        binding.payButton.text = getString(R.string.pay_amount, total)
        binding.payButton.setOnClickListener { pay() }
    }

    private fun pay() {
        val user = session.currentUser ?: return logoutAndExit()
        binding.payButton.isEnabled = false
        binding.payButton.setText(R.string.processing)
        binding.progress.isVisible = true
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        handler.postDelayed({
            val order = Store.confirmSimulatedPayment(user, address)
            startActivity(PaymentResultActivity.intentFor(this, order.code))
            finishAffinity()
        }, SIMULATED_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ADDRESS = "address"
        private const val SIMULATED_DELAY_MS = 1200L

        fun intentFor(context: Context, address: String) =
            Intent(context, PaymentActivity::class.java).putExtra(EXTRA_ADDRESS, address)
    }
}

class PaymentResultActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPaymentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val order = Store.order(intent.getStringExtra(EXTRA_CODE).orEmpty()) ?: run { goToOrders(); return }
        binding.orderText.text = getString(R.string.order_confirmed, order.code)
        binding.stateValue.setText(order.status.label)
        binding.total.text = Money.format(order.totalCents)
        binding.deliveryValue.setText(R.string.tomorrow)
        binding.receiptButton.setOnClickListener { showReceipt(order) }
        binding.ordersButton.setOnClickListener { goToOrders() }
        onBackPressedDispatcher.addCallback(this) { goToOrders() }
    }

    private fun showReceipt(order: Order) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.receipt_title, order.code))
            .setMessage(orderSummary(order))
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun goToOrders() {
        startActivity(BuyerActivity.intentFor(this, R.id.nav_orders))
        finish()
    }

    companion object {
        private const val EXTRA_CODE = "order_code"

        fun intentFor(context: Context, code: String) =
            Intent(context, PaymentResultActivity::class.java).putExtra(EXTRA_CODE, code)
    }
}

/** Texto del pedido usado en el comprobante y en el detalle del historial. */
fun Context.orderSummary(order: Order): String = getString(
    R.string.order_detail_body,
    order.date,
    order.address,
    getString(order.status.label),
    order.items.joinToString("\n") {
        "${it.name} · " + getString(R.string.item_line, it.quantity, Money.format(it.unitPriceCents))
    },
    Money.format(order.subtotalCents),
    Money.format(order.shippingCents),
    Money.format(order.totalCents)
)
