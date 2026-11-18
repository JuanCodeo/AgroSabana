package co.edu.ucompensar.agrosabana.ui.buyer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.EpaycoGateway
import co.edu.ucompensar.agrosabana.data.EpaycoTransaction
import co.edu.ucompensar.agrosabana.data.GeoPoint
import co.edu.ucompensar.agrosabana.data.Order
import co.edu.ucompensar.agrosabana.data.OrderStatus
import co.edu.ucompensar.agrosabana.data.PaymentInfo
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.databinding.ActivityCheckoutBinding
import co.edu.ucompensar.agrosabana.databinding.ActivityEpaycoCheckoutBinding
import co.edu.ucompensar.agrosabana.databinding.ActivityPaymentBinding
import co.edu.ucompensar.agrosabana.databinding.ActivityPaymentResultBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.StepperView
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.setText
import co.edu.ucompensar.agrosabana.ui.common.text
import co.edu.ucompensar.agrosabana.util.LocationHelper
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

/**
 * Paso 2 del checkout: direccion de entrega con geolocalizacion y resumen previo al pago
 * (RF-PAG-01, Ley 1480 de 2011). Al continuar se crea el pedido en Firestore en PENDIENTE_PAGO.
 */
class CheckoutActivity : BaseActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private var location: GeoPoint? = null

    private val locationHelper = LocationHelper(
        this,
        onLocation = ::onLocation,
        onDenied = {
            binding.locationButton.isEnabled = true
            Snackbar.make(binding.root, R.string.location_denied, Snackbar.LENGTH_LONG).show()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.delivery_title)
        binding.stepper.showCheckoutStep(1)

        val user = session.currentUser!!
        binding.addressLayout.setText(user.address)
        location = user.location
        binding.map.show(location, getString(R.string.delivery))
        binding.subtotal.text = Money.format(Store.cartSubtotalCents)
        binding.shipping.text = Money.format(Store.cartShippingCents)
        binding.total.text = Money.format(Store.cartTotalCents)
        binding.sellers.text = getString(
            R.string.seller_identity,
            Store.cart.map { it.product.sellerName }.distinct().joinToString(", ")
        )

        binding.locationButton.setOnClickListener {
            binding.locationButton.isEnabled = false
            locationHelper.request()
        }
        binding.termsSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) binding.termsError.isVisible = false
        }
        binding.payButton.setOnClickListener { goToPayment() }
    }

    /** Ubicacion GPS capturada: se muestra en el mapa y se convierte en direccion sugerida. */
    private fun onLocation(point: GeoPoint) {
        location = point
        binding.locationButton.isEnabled = true
        binding.map.show(point, getString(R.string.delivery))
        launchTask {
            val address = LocationHelper.reverseGeocode(this, point)
            if (address != null) binding.addressLayout.setText(address)
            Snackbar.make(binding.root, R.string.location_filled, Snackbar.LENGTH_SHORT).show()
            // Se guarda en el perfil para calcular distancias en el catalogo.
            Store.saveLocation(point, null)
        }
    }

    private fun goToPayment() {
        val addressOk = binding.addressLayout.requireValue()
        binding.termsError.isVisible = !binding.termsSwitch.isChecked
        if (!addressOk || !binding.termsSwitch.isChecked) return
        if (Store.cart.isEmpty()) return finish()
        val buyer = session.currentUser ?: return logoutAndExit()

        binding.payButton.isEnabled = false
        launchTask(onError = { binding.payButton.isEnabled = true }) {
            val order = Store.createOrder(buyer, binding.addressLayout.text, location)
            startActivity(PaymentActivity.intentFor(this, order.code))
            binding.payButton.isEnabled = true
        }
    }
}

/**
 * Paso 3: pasarela de pagos ePayco (RF-PAG-02).
 *
 * Abre el checkout oficial de ePayco; al volver con la referencia (ref_payco) consulta el estado
 * real de la transaccion en ePayco, comprueba que factura y monto coincidan con el pedido y solo
 * entonces registra el pago en Firestore.
 */
class PaymentActivity : BaseActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var orderCode: String

    private val checkout = registerForActivityResult(EpaycoCheckoutActivity.Contract()) { reference ->
        if (reference != null) verify(reference)
        else setBusy(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.gateway_title)
        binding.stepper.showCheckoutStep(2)

        orderCode = intent.getStringExtra(EXTRA_CODE).orEmpty()
        launchTask(onError = { finish() }) {
            val order = Store.order(orderCode) ?: Store.fetchOrder(orderCode) ?: return@launchTask finish()
            val total = Money.format(order.totalCents)
            binding.total.text = total
            binding.payButton.text = getString(R.string.pay_amount, total)
            // Los medios se eligen dentro de ePayco; los chips solo informan cuales se aceptan.
            listOf(binding.methodCard, binding.methodPse, binding.methodNequi).forEach {
                it.isCheckable = false
                it.isClickable = false
            }
            binding.payButton.setOnClickListener { pay() }
            // Pago pendiente desde el historial: se vuelve a consultar en ePayco.
            intent.getStringExtra(EXTRA_REFERENCE)?.let(::verify)
        }
    }

    private fun setBusy(busy: Boolean, message: Int = R.string.processing) {
        binding.payButton.isEnabled = !busy
        binding.progress.isVisible = busy
        if (busy) binding.payButton.setText(message)
        else Store.order(orderCode)?.let { binding.payButton.text = getString(R.string.pay_amount, Money.format(it.totalCents)) }
    }

    private fun pay() {
        if (!EpaycoGateway.isConfigured) {
            Snackbar.make(binding.root, R.string.epayco_not_configured, Snackbar.LENGTH_LONG).show()
            return
        }
        setBusy(true)
        checkout.launch(orderCode)
    }

    /** Consulta la transaccion en ePayco y actualiza el pedido segun el resultado. */
    private fun verify(reference: String) {
        setBusy(true, R.string.verifying_payment)
        launchTask(onError = { setBusy(false) }) {
            val order = Store.fetchOrder(orderCode) ?: return@launchTask finish()
            if (order.status != OrderStatus.PENDIENTE_PAGO) {
                // Ya procesado (por ejemplo, la pantalla se reabrio).
                return@launchTask openResult(order.code, reference)
            }
            val transaction = EpaycoGateway.validate(reference)
            if (!EpaycoGateway.matches(transaction, order)) {
                setBusy(false)
                Snackbar.make(binding.root, R.string.payment_mismatch, Snackbar.LENGTH_LONG).show()
                return@launchTask
            }
            val info = transaction.toPaymentInfo()
            when {
                transaction.approved -> Store.applyPayment(order, info, approved = true)
                transaction.finalRejection -> Store.applyPayment(order, info, approved = false)
                else -> Store.savePendingPayment(order, info)
            }
            openResult(order.code, reference)
        }
    }

    private fun openResult(code: String, reference: String) {
        startActivity(PaymentResultActivity.intentFor(this, code, reference))
        finishAffinity()
    }

    private fun EpaycoTransaction.toPaymentInfo() = PaymentInfo(
        provider = "ePayco",
        reference = reference,
        transactionId = transactionId,
        response = response,
        method = franchise,
        test = test
    )

    companion object {
        private const val EXTRA_CODE = "order_code"
        private const val EXTRA_REFERENCE = "reference"

        fun intentFor(context: Context, code: String, pendingReference: String? = null) =
            Intent(context, PaymentActivity::class.java)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_REFERENCE, pendingReference)
    }
}

/**
 * Contenedor del checkout de ePayco en una WebView. Devuelve la referencia ref_payco cuando ePayco
 * redirige a la URL de respuesta, o null si el usuario cancela.
 */
class EpaycoCheckoutActivity : BaseActivity() {

    private lateinit var binding: ActivityEpaycoCheckoutBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityEpaycoCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.gateway_title)

        val order = Store.order(intent.getStringExtra(EXTRA_CODE).orEmpty()) ?: return finish()
        val buyer = session.currentUser!!

        with(binding.webView) {
            settings.javaScriptEnabled = true // checkout.js de ePayco lo requiere
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false) // los bancos PSE abren en la misma vista
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    capture(request.url.toString())

                // La respuesta puede llegar por POST, que no pasa por shouldOverrideUrlLoading.
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (capture(url)) view.stopLoading()
                    binding.progress.isVisible = true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    binding.progress.isVisible = false
                }
            }
            loadDataWithBaseURL(BASE_URL, EpaycoGateway.checkoutHtml(order, buyer), "text/html", "utf-8", null)
        }

        onBackPressedDispatcher.addCallback(this) { confirmCancel() }
    }

    /** Si la URL es la de respuesta de ePayco, entrega la referencia y cierra. */
    private fun capture(url: String): Boolean {
        val reference = EpaycoGateway.referenceFrom(url) ?: return false
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_REFERENCE, reference))
        finish()
        return true
    }

    private fun confirmCancel() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cancel_payment_title)
            .setMessage(R.string.cancel_payment_body)
            .setNegativeButton(R.string.continue_paying, null)
            .setPositiveButton(R.string.cancel_payment) { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.webView.destroy()
        super.onDestroy()
    }

    /** Contrato de Activity Result: entrada = codigo del pedido, salida = ref_payco o null. */
    class Contract : ActivityResultContract<String, String?>() {
        override fun createIntent(context: Context, input: String) =
            Intent(context, EpaycoCheckoutActivity::class.java).putExtra(EXTRA_CODE, input)

        override fun parseResult(resultCode: Int, intent: Intent?): String? =
            if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_REFERENCE) else null
    }

    private companion object {
        const val EXTRA_CODE = "order_code"
        const val EXTRA_REFERENCE = "ref_payco"
        const val BASE_URL = "https://agrosabana.app/"
    }
}

/** Paso 4: resultado de la transaccion (aprobada, rechazada o pendiente) con el comprobante. */
class PaymentResultActivity : BaseActivity() {

    private lateinit var binding: ActivityPaymentResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityPaymentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.ordersButton.setOnClickListener { goToOrders() }
        onBackPressedDispatcher.addCallback(this) { goToOrders() }

        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        launchTask(onError = { goToOrders() }) {
            val order = Store.fetchOrder(code) ?: return@launchTask goToOrders()
            render(order)
        }
    }

    private fun render(order: Order) {
        val (title, icon, color) = when (order.status) {
            OrderStatus.PENDIENTE_PAGO -> Triple(R.string.payment_pending, R.drawable.ic_clock, R.color.state_info)
            OrderStatus.PAGO_RECHAZADO -> Triple(R.string.payment_rejected, R.drawable.ic_close, R.color.state_error)
            else -> Triple(R.string.payment_ok, R.drawable.ic_check, R.color.agro_green)
        }
        binding.resultTitle.setText(title)
        binding.resultIcon.setImageResource(icon)
        binding.resultIcon.backgroundTintList = getColorStateList(color)
        binding.orderText.text = getString(R.string.order_confirmed, order.code)
        binding.stateValue.setText(order.status.label)
        binding.total.text = Money.format(order.totalCents)
        binding.referenceValue.text = order.payment?.reference ?: intent.getStringExtra(EXTRA_REFERENCE).orEmpty()
        binding.deliveryRow.isVisible = order.status == OrderStatus.PAGADO
        binding.deliveryValue.setText(R.string.tomorrow)
        binding.receiptButton.setOnClickListener { showReceipt(order) }
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
        private const val EXTRA_REFERENCE = "reference"

        fun intentFor(context: Context, code: String, reference: String) =
            Intent(context, PaymentResultActivity::class.java)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_REFERENCE, reference)
    }
}

/** Texto del pedido usado en el comprobante y en el detalle del historial. */
fun Context.orderSummary(order: Order): String {
    val base = getString(
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
    val payment = order.payment ?: return base
    return base + "\n\n" + getString(
        R.string.payment_detail, payment.provider, payment.reference, payment.response,
        payment.method.ifEmpty { "-" }
    ) + if (payment.test) "\n" + getString(R.string.payment_test_mode) else ""
}
