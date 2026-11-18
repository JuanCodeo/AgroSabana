package co.edu.ucompensar.agrosabana.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import co.edu.ucompensar.agrosabana.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Modelo de dominio de AgroSabana.
 *
 * Cada clase corresponde a un documento de Cloud Firestore (ver FirestoreMapper.kt). Los enums se
 * guardan por su nombre (por ejemplo "APROBADO"), de modo que las reglas de seguridad de Firestore
 * pueden validar los estados sin depender de la app.
 */

/** Rol del usuario; define la navegacion y los permisos en las reglas de Firestore. */
enum class Role(@StringRes val label: Int) {
    BUYER(R.string.role_buyer),
    SELLER(R.string.role_seller),
    ADMIN(R.string.role_admin)
}

/** Estado de la cuenta: un vendedor nuevo queda PENDIENTE hasta que el administrador lo aprueba. */
enum class UserStatus(@StringRes val label: Int, @ColorRes val color: Int) {
    ACTIVO(R.string.status_active, R.color.state_ok),
    PENDIENTE(R.string.status_pending, R.color.state_warn),
    SUSPENDIDO(R.string.status_suspended, R.color.state_error)
}

/** Estados de moderacion de RF-ADP-01. Solo los APROBADO son visibles para el comprador. */
enum class ProductStatus(@StringRes val label: Int, @ColorRes val color: Int) {
    PENDIENTE(R.string.status_pending, R.color.state_info),
    APROBADO(R.string.status_approved, R.color.state_ok),
    RECHAZADO(R.string.status_rejected, R.color.state_error),
    OCULTO(R.string.status_hidden, R.color.state_muted)
}

/**
 * Estados del pedido de RF-PED-01 a RF-PED-04.
 *
 * El pedido nace en PENDIENTE_PAGO; solo la respuesta validada de ePayco lo pasa a PAGADO o a
 * PAGO_RECHAZADO. Despues el vendedor lo avanza con [next].
 */
enum class OrderStatus(@StringRes val label: Int, @ColorRes val color: Int) {
    PENDIENTE_PAGO(R.string.status_payment_pending, R.color.state_info),
    PAGO_RECHAZADO(R.string.status_payment_rejected, R.color.state_error),
    PAGADO(R.string.status_paid, R.color.state_ok),
    PREPARANDO(R.string.status_preparing, R.color.state_warn),
    ENVIADO(R.string.status_sent, R.color.state_info),
    ENTREGADO(R.string.status_delivered, R.color.state_ok),
    CANCELADO(R.string.status_cancelled, R.color.state_muted),
    RETRACTO(R.string.status_withdrawal, R.color.state_warn),
    DISPUTA(R.string.status_dispute, R.color.state_error),
    ANULADO(R.string.status_voided, R.color.state_muted);

    /** Siguiente paso del flujo normal de entrega; null si ya no avanza. */
    fun next(): OrderStatus? = when (this) {
        PAGADO -> PREPARANDO
        PREPARANDO -> ENVIADO
        ENVIADO -> ENTREGADO
        else -> null
    }

    /** Pedido pagado que aun no se entrega. */
    val isOpen: Boolean get() = this == PAGADO || this == PREPARANDO || this == ENVIADO

    /** Cuenta como venta en los reportes (excluye pagos fallidos, cancelaciones y anulaciones). */
    val isSale: Boolean
        get() = this != PENDIENTE_PAGO && this != PAGO_RECHAZADO && this != ANULADO &&
            this != CANCELADO && this != RETRACTO
}

/** Categorias del catalogo con su ilustracion y color; el administrador puede desactivarlas. */
enum class Category(
    @StringRes val label: Int,
    @DrawableRes val illustration: Int,
    @ColorRes val background: Int
) {
    FRUTAS(R.string.cat_fruit, R.drawable.ill_fruit, R.color.cat_fruit),
    HORTALIZAS(R.string.cat_vegetable, R.drawable.ill_vegetable, R.color.cat_vegetable),
    LACTEOS(R.string.cat_dairy, R.drawable.ill_dairy, R.color.cat_dairy),
    HUEVOS(R.string.cat_egg, R.drawable.ill_egg, R.color.cat_egg),
    FLORES(R.string.cat_flower, R.drawable.ill_flower, R.color.cat_flower),
    PROCESADOS(R.string.cat_processed, R.drawable.ill_processed, R.color.cat_processed)
}

/** Coordenadas capturadas con el GPS (FusedLocationProviderClient). */
data class GeoPoint(val lat: Double, val lng: Double)

/** Documento users/{uid}. El id es el UID de Firebase Authentication. */
data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: Role,
    val status: UserStatus,
    val municipality: String,
    val address: String = "",
    val location: GeoPoint? = null,
    val deliveryRadiusKm: Int = 15,
    val photoUrl: String? = null
)

/**
 * Documento products/{id}.
 * El precio se guarda en centavos (Long) para evitar errores de punto flotante (RF-PRO-01) y la
 * foto es la URL publica del archivo subido a Firebase Storage.
 */
data class Product(
    val id: String,
    val name: String,
    val description: String,
    val priceCents: Long,
    val unit: String,
    val stock: Int,
    val category: Category,
    val sellerId: String,
    val sellerName: String,
    val municipality: String,
    val origin: GeoPoint?,
    val status: ProductStatus,
    val sold: Int = 0,
    val photoUrl: String? = null
) {
    /** Distancia en km desde la ubicacion del comprador; [Store.UNKNOWN_DISTANCE] si no se conoce. */
    val distanceKm: Double get() = Store.distanceTo(origin)
}

/** Documento carts/{uid}/items/{productId} unido al producto vigente del catalogo. */
data class CartItem(val product: Product, val quantity: Int) {
    val subtotalCents: Long get() = product.priceCents * quantity
}

/** El precio queda congelado en el item: editar el producto no altera pedidos (RF-PRO-03). */
data class OrderItem(
    val productId: String,
    val name: String,
    val unitPriceCents: Long,
    val quantity: Int,
    val sellerId: String,
    val category: Category
) {
    val subtotalCents: Long get() = unitPriceCents * quantity
}

/** Datos de la transaccion devueltos por ePayco y verificados por la app. */
data class PaymentInfo(
    val provider: String,
    val reference: String,
    val transactionId: String,
    val response: String,
    val method: String,
    val test: Boolean
)

/** Documento orders/{code}. sellerIds permite que cada vendedor consulte solo sus pedidos. */
data class Order(
    val code: String,
    val buyerId: String,
    val buyerName: String,
    val items: List<OrderItem>,
    val shippingCents: Long,
    val address: String,
    val location: GeoPoint?,
    val createdAt: Date,
    val status: OrderStatus,
    val payment: PaymentInfo? = null,
    val problem: String? = null
) {
    val subtotalCents: Long get() = items.sumOf { it.subtotalCents }
    val totalCents: Long get() = subtotalCents + shippingCents
    val sellerIds: List<String> get() = items.map { it.sellerId }.distinct()
    val sellerCount: Int get() = sellerIds.size

    /** Fecha legible en espanol de Colombia, por ejemplo "13 oct 2026 · 10:42". */
    val date: String get() = SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es", "CO")).format(createdAt)

    fun itemsOf(sellerId: String) = items.filter { it.sellerId == sellerId }
}

/** Documento audit/{id}: toda accion administrativa queda registrada (RF-ADU-02). */
data class AuditEntry(val adminName: String, val action: String, val createdAt: Date) {
    override fun toString(): String =
        SimpleDateFormat("d MMM · HH:mm", Locale("es", "CO")).format(createdAt) + " · $adminName · $action"
}
