package co.edu.ucompensar.agrosabana.data

import android.graphics.Bitmap
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import co.edu.ucompensar.agrosabana.R

enum class Role(@StringRes val label: Int) {
    BUYER(R.string.role_buyer),
    SELLER(R.string.role_seller),
    ADMIN(R.string.role_admin)
}

enum class UserStatus(@StringRes val label: Int, @ColorRes val color: Int) {
    ACTIVO(R.string.status_active, R.color.state_ok),
    PENDIENTE(R.string.status_pending, R.color.state_warn),
    SUSPENDIDO(R.string.status_suspended, R.color.state_error)
}

/** Estados de moderacion de RF-ADP-01. */
enum class ProductStatus(@StringRes val label: Int, @ColorRes val color: Int) {
    PENDIENTE(R.string.status_pending, R.color.state_info),
    APROBADO(R.string.status_approved, R.color.state_ok),
    RECHAZADO(R.string.status_rejected, R.color.state_error),
    OCULTO(R.string.status_hidden, R.color.state_muted)
}

/** Estados del pedido de RF-PED-01 a RF-PED-04. */
enum class OrderStatus(@StringRes val label: Int, @ColorRes val color: Int) {
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

    val isOpen: Boolean get() = this == PAGADO || this == PREPARANDO || this == ENVIADO
}

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

data class User(
    val id: Int,
    var name: String,
    var email: String,
    var phone: String,
    var role: Role,
    var status: UserStatus,
    var municipality: String,
    var address: String = "",
    var deliveryRadiusKm: Int = 15,
    var photo: Bitmap? = null
)

/** Precio en centavos (Long) para evitar errores de punto flotante, como exige RF-PRO-01. */
data class Product(
    val id: Int,
    var name: String,
    var description: String,
    var priceCents: Long,
    var unit: String,
    var stock: Int,
    var category: Category,
    val sellerId: Int,
    val sellerName: String,
    val municipality: String,
    val distanceKm: Double,
    var status: ProductStatus,
    var sold: Int = 0,
    var photo: Bitmap? = null
)

data class CartItem(val product: Product, var quantity: Int) {
    val subtotalCents: Long get() = product.priceCents * quantity
}

/** El precio queda congelado en el item: editar el producto no altera pedidos (RF-PRO-03). */
data class OrderItem(
    val name: String,
    val unitPriceCents: Long,
    val quantity: Int,
    val sellerId: Int,
    val category: Category
) {
    val subtotalCents: Long get() = unitPriceCents * quantity
}

data class Order(
    val code: String,
    val buyerId: Int,
    val buyerName: String,
    val items: List<OrderItem>,
    val shippingCents: Long,
    val address: String,
    val date: String,
    var status: OrderStatus
) {
    val subtotalCents: Long get() = items.sumOf { it.subtotalCents }
    val totalCents: Long get() = subtotalCents + shippingCents
    val sellerCount: Int get() = items.map { it.sellerId }.distinct().size

    fun itemsOf(sellerId: Int) = items.filter { it.sellerId == sellerId }
}
