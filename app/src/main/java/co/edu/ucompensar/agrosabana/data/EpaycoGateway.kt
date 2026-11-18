package co.edu.ucompensar.agrosabana.data

import co.edu.ucompensar.agrosabana.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Resultado de consultar una transaccion en ePayco. */
data class EpaycoTransaction(
    val reference: String,
    val invoice: String,
    val code: Int,
    val response: String,
    val transactionId: String,
    val amountPesos: Long,
    val franchise: String,
    val test: Boolean
) {
    val approved: Boolean get() = code == CODE_ACCEPTED

    /** Rechazada, fallida, reversada, expirada, abandonada o cancelada: ya no se puede aprobar. */
    val finalRejection: Boolean get() = code in listOf(2, 4, 6, 9, 10, 11)

    companion object {
        const val CODE_ACCEPTED = 1
        const val CODE_PENDING = 3
    }
}

/**
 * Integracion con la pasarela ePayco (proveedor certificado PCI DSS en Colombia).
 *
 * Flujo:
 *  1. [checkoutHtml] arma la pagina con el Checkout oficial de ePayco (checkout.js) usando solo la
 *     llave PUBLICA. La app nunca ve ni guarda datos de tarjeta: el formulario es de ePayco.
 *  2. Al terminar, ePayco redirige a [RESPONSE_URL] con el parametro ref_payco; la WebView
 *     intercepta esa URL sin cargarla.
 *  3. [validate] consulta el estado real en el API de validacion de ePayco y la app verifica que la
 *     factura y el monto coincidan con el pedido antes de marcarlo como pagado.
 */
object EpaycoGateway {

    /** URL de respuesta ficticia: nunca se carga, solo sirve para capturar ref_payco. */
    const val RESPONSE_URL = "https://agrosabana.app/epayco/respuesta"

    private const val VALIDATION_URL = "https://secure.epayco.co/validation/v1/reference/"

    val isConfigured: Boolean get() = BuildConfig.EPAYCO_PUBLIC_KEY.isNotBlank()

    /** Pagina HTML que abre el Checkout de ePayco con los datos del pedido. */
    fun checkoutHtml(order: Order, buyer: User): String {
        val data = JSONObject().apply {
            put("name", "AgroSabana")
            put("description", "Pedido ${order.code} - ${order.items.size} producto(s)")
            put("invoice", order.code)
            put("currency", "cop")
            put("amount", (order.totalCents / 100).toString())
            put("tax_base", "0")
            put("tax", "0")
            put("country", "co")
            put("lang", "es")
            put("external", "false")
            put("response", RESPONSE_URL)
            put("name_billing", buyer.name)
            put("email_billing", buyer.email)
            put("address_billing", order.address)
            put("mobilephone_billing", buyer.phone)
            put("extra1", order.buyerId)
        }
        return """
            <!doctype html>
            <html lang="es"><head>
            <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:90vh;color:#37474F}</style>
            </head><body>
            <p id="msg">Abriendo la pasarela segura de ePayco...</p>
            <script src="https://checkout.epayco.co/checkout.js"></script>
            <script>
              try {
                var handler = ePayco.checkout.configure({
                  key: ${JSONObject.quote(BuildConfig.EPAYCO_PUBLIC_KEY)},
                  test: ${BuildConfig.EPAYCO_TEST}
                });
                handler.open($data);
              } catch (e) {
                document.getElementById('msg').innerText = 'No se pudo cargar ePayco: ' + e;
              }
            </script>
            </body></html>
        """.trimIndent()
    }

    /** Extrae ref_payco de la URL de respuesta; null si la URL no es la de respuesta. */
    fun referenceFrom(url: String): String? {
        if (!url.startsWith(RESPONSE_URL)) return null
        return android.net.Uri.parse(url).getQueryParameter("ref_payco")
    }

    /** Consulta el estado de la transaccion en el API de validacion de ePayco. */
    suspend fun validate(reference: String): EpaycoTransaction = withContext(Dispatchers.IO) {
        val url = URL(VALIDATION_URL + URLEncoder.encode(reference, "UTF-8"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            check(json.optBoolean("success", false)) { "ePayco no encontro la referencia $reference" }
            val data = json.getJSONObject("data")
            EpaycoTransaction(
                reference = data.optString("x_ref_payco", reference),
                invoice = data.optString("x_id_invoice"),
                code = data.optString("x_cod_response").toIntOrNull() ?: 0,
                response = data.optString("x_response"),
                transactionId = data.optString("x_transaction_id"),
                amountPesos = data.optString("x_amount").toDoubleOrNull()?.toLong() ?: 0L,
                franchise = data.optString("x_franchise"),
                test = data.optString("x_test_request").equals("TRUE", ignoreCase = true)
            )
        } finally {
            connection.disconnect()
        }
    }

    /** La transaccion corresponde al pedido solo si factura y monto coinciden. */
    fun matches(transaction: EpaycoTransaction, order: Order) =
        transaction.invoice == order.code && transaction.amountPesos == order.totalCents / 100

    private const val TIMEOUT_MS = 15_000
}
