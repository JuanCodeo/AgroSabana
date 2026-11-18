package co.edu.ucompensar.agrosabana.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * Conversion entre documentos de Cloud Firestore y el modelo de dominio.
 *
 * Se hace a mano (en lugar de toObject) para tolerar documentos incompletos: un campo ausente o un
 * enum desconocido toma un valor por defecto en vez de cerrar la app.
 */
object FirestoreMapper {

    // ---------- Lectura ----------

    fun user(doc: DocumentSnapshot): User? {
        if (!doc.exists()) return null
        return User(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            email = doc.getString("email").orEmpty(),
            phone = doc.getString("phone").orEmpty(),
            role = enumOf(doc.getString("role"), Role.BUYER),
            status = enumOf(doc.getString("status"), UserStatus.PENDIENTE),
            municipality = doc.getString("municipality").orEmpty(),
            address = doc.getString("address").orEmpty(),
            location = geo(doc.get("location")),
            deliveryRadiusKm = doc.getLong("deliveryRadiusKm")?.toInt() ?: 15,
            photoUrl = doc.getString("photoUrl")
        )
    }

    fun product(doc: DocumentSnapshot): Product? {
        if (!doc.exists()) return null
        return Product(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            description = doc.getString("description").orEmpty(),
            priceCents = doc.getLong("priceCents") ?: 0L,
            unit = doc.getString("unit").orEmpty(),
            stock = doc.getLong("stock")?.toInt() ?: 0,
            category = enumOf(doc.getString("category"), Category.FRUTAS),
            sellerId = doc.getString("sellerId").orEmpty(),
            sellerName = doc.getString("sellerName").orEmpty(),
            municipality = doc.getString("municipality").orEmpty(),
            origin = geo(doc.get("origin")),
            status = enumOf(doc.getString("status"), ProductStatus.PENDIENTE),
            sold = doc.getLong("sold")?.toInt() ?: 0,
            photoUrl = doc.getString("photoUrl")
        )
    }

    fun order(doc: DocumentSnapshot): Order? {
        if (!doc.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val rawItems = doc.get("items") as? List<Map<String, Any?>> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val rawPayment = doc.get("payment") as? Map<String, Any?>
        return Order(
            code = doc.id,
            buyerId = doc.getString("buyerId").orEmpty(),
            buyerName = doc.getString("buyerName").orEmpty(),
            items = rawItems.map { item ->
                OrderItem(
                    productId = item["productId"] as? String ?: "",
                    name = item["name"] as? String ?: "",
                    unitPriceCents = (item["unitPriceCents"] as? Number)?.toLong() ?: 0L,
                    quantity = (item["quantity"] as? Number)?.toInt() ?: 0,
                    sellerId = item["sellerId"] as? String ?: "",
                    category = enumOf(item["category"] as? String, Category.FRUTAS)
                )
            },
            shippingCents = doc.getLong("shippingCents") ?: 0L,
            address = doc.getString("address").orEmpty(),
            location = geo(doc.get("location")),
            createdAt = doc.getTimestamp("createdAt")?.toDate() ?: Date(),
            status = enumOf(doc.getString("status"), OrderStatus.PENDIENTE_PAGO),
            payment = rawPayment?.let {
                PaymentInfo(
                    provider = it["provider"] as? String ?: "",
                    reference = it["reference"] as? String ?: "",
                    transactionId = it["transactionId"] as? String ?: "",
                    response = it["response"] as? String ?: "",
                    method = it["method"] as? String ?: "",
                    test = it["test"] as? Boolean ?: true
                )
            },
            problem = doc.getString("problem")
        )
    }

    fun audit(doc: DocumentSnapshot) = AuditEntry(
        adminName = doc.getString("adminName").orEmpty(),
        action = doc.getString("action").orEmpty(),
        createdAt = doc.getTimestamp("createdAt")?.toDate() ?: Date()
    )

    // ---------- Escritura ----------

    fun userMap(user: User): Map<String, Any?> = mapOf(
        "name" to user.name,
        "email" to user.email,
        "phone" to user.phone,
        "role" to user.role.name,
        "status" to user.status.name,
        "municipality" to user.municipality,
        "address" to user.address,
        "location" to user.location?.let(::geoMap),
        "deliveryRadiusKm" to user.deliveryRadiusKm,
        "photoUrl" to user.photoUrl
    )

    fun productMap(product: Product): Map<String, Any?> = mapOf(
        "name" to product.name,
        "description" to product.description,
        "priceCents" to product.priceCents,
        "unit" to product.unit,
        "stock" to product.stock,
        "category" to product.category.name,
        "sellerId" to product.sellerId,
        "sellerName" to product.sellerName,
        "municipality" to product.municipality,
        "origin" to product.origin?.let(::geoMap),
        "status" to product.status.name,
        "sold" to product.sold,
        "photoUrl" to product.photoUrl
    )

    fun orderMap(order: Order): Map<String, Any?> = mapOf(
        "buyerId" to order.buyerId,
        "buyerName" to order.buyerName,
        "items" to order.items.map {
            mapOf(
                "productId" to it.productId,
                "name" to it.name,
                "unitPriceCents" to it.unitPriceCents,
                "quantity" to it.quantity,
                "sellerId" to it.sellerId,
                "category" to it.category.name
            )
        },
        "sellerIds" to order.sellerIds,
        "shippingCents" to order.shippingCents,
        "subtotalCents" to order.subtotalCents,
        "totalCents" to order.totalCents,
        "address" to order.address,
        "location" to order.location?.let(::geoMap),
        "createdAt" to Timestamp(order.createdAt),
        "status" to order.status.name
    )

    fun paymentMap(payment: PaymentInfo): Map<String, Any?> = mapOf(
        "provider" to payment.provider,
        "reference" to payment.reference,
        "transactionId" to payment.transactionId,
        "response" to payment.response,
        "method" to payment.method,
        "test" to payment.test
    )

    fun geoMap(point: GeoPoint) = mapOf("lat" to point.lat, "lng" to point.lng)

    // ---------- Utilidades ----------

    private fun geo(raw: Any?): GeoPoint? {
        val map = raw as? Map<*, *> ?: return null
        val lat = (map["lat"] as? Number)?.toDouble() ?: return null
        val lng = (map["lng"] as? Number)?.toDouble() ?: return null
        return GeoPoint(lat, lng)
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
