package co.edu.ucompensar.agrosabana.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fuente de datos en memoria de la etapa de frontend.
 *
 * Permite recorrer los CRUD completos sin servidor. Los datos vuelven al estado inicial cuando se
 * cierra el proceso; en la etapa de backend este objeto se reemplaza por Firebase detras de la
 * misma interfaz de consumo.
 */
object Store {

    const val DEMO_PASSWORD = "Agro2026"
    const val COMMISSION_PERCENT = 8L
    private const val SHIPPING_PER_SELLER_CENTS = 300_000L
    private const val PESOS = 100L

    val users = mutableListOf(
        User(1, "Juan Pérez", "comprador@agrosabana.demo", "300 000 0000", Role.BUYER, UserStatus.ACTIVO, "Funza", "Cra 15 # 8-21, Funza"),
        User(2, "Finca El Rosal", "vendedor@agrosabana.demo", "301 000 0000", Role.SELLER, UserStatus.ACTIVO, "Funza", "Vereda El Rosal, Funza"),
        User(3, "Administrador AgroSabana", "admin@agrosabana.demo", "302 000 0000", Role.ADMIN, UserStatus.ACTIVO, "Mosquera"),
        User(4, "Lácteos La Pradera", "lacteos@agrosabana.demo", "303 000 0000", Role.SELLER, UserStatus.ACTIVO, "Madrid"),
        User(5, "Apiario Sabana", "apiario@agrosabana.demo", "304 000 0000", Role.SELLER, UserStatus.PENDIENTE, "Facatativá"),
        User(6, "María Gómez", "maria@agrosabana.demo", "305 000 0000", Role.BUYER, UserStatus.ACTIVO, "Bogotá"),
        User(7, "Cuenta 1842", "cuenta1842@agrosabana.demo", "306 000 0000", Role.BUYER, UserStatus.SUSPENDIDO, "Mosquera")
    )

    val products = mutableListOf(
        Product(1, "Fresa x 500 g", "Fresa fresca recogida el mismo día, sin intermediarios.", 8_500 * PESOS, "500 g", 24, Category.FRUTAS, 2, "Finca El Rosal", "Funza", 3.2, ProductStatus.APROBADO, 86),
        Product(2, "Queso campesino", "Queso fresco de leche entera, elaborado artesanalmente.", 14_000 * PESOS, "unidad", 18, Category.LACTEOS, 4, "Lácteos La Pradera", "Madrid", 5.0, ProductStatus.APROBADO, 54),
        Product(3, "Huevos AA x 30", "Huevos de gallina campesina, cubeta de 30 unidades.", 19_900 * PESOS, "cubeta", 40, Category.HUEVOS, 4, "Lácteos La Pradera", "Madrid", 7.8, ProductStatus.APROBADO, 41),
        Product(4, "Lechuga crespa", "Lechuga cosechada en la mañana, lista para consumir.", 2_500 * PESOS, "unidad", 60, Category.HORTALIZAS, 2, "Finca El Rosal", "Funza", 2.1, ProductStatus.APROBADO, 73),
        Product(5, "Mora x 500 g", "Mora de Castilla seleccionada para jugos y postres.", 7_000 * PESOS, "500 g", 3, Category.FRUTAS, 2, "Finca El Rosal", "Funza", 3.2, ProductStatus.APROBADO, 38),
        Product(6, "Uchuva x 250 g", "Uchuva dulce, empacada en canastilla.", 4_500 * PESOS, "250 g", 0, Category.FRUTAS, 2, "Finca El Rosal", "Funza", 3.2, ProductStatus.APROBADO, 19),
        Product(7, "Ramo de astromelias", "Ramo de flor fresca cortada el mismo día.", 12_000 * PESOS, "ramo", 15, Category.FLORES, 4, "Lácteos La Pradera", "Madrid", 9.4, ProductStatus.APROBADO, 12),
        Product(8, "Panela orgánica x 500 g", "Panela sin aditivos de trapiche artesanal.", 6_200 * PESOS, "500 g", 30, Category.PROCESADOS, 2, "Finca El Rosal", "Funza", 3.2, ProductStatus.APROBADO, 27),
        Product(9, "Arveja x libra", "Arveja verde desgranada.", 5_200 * PESOS, "libra", 15, Category.HORTALIZAS, 2, "Finca El Rosal", "Funza", 3.2, ProductStatus.PENDIENTE),
        Product(10, "Miel 500 ml", "Miel pura de abejas de la Sabana.", 22_000 * PESOS, "500 ml", 10, Category.PROCESADOS, 5, "Apiario Sabana", "Facatativá", 14.6, ProductStatus.PENDIENTE),
        Product(11, "Cuajada", "Cuajada fresca de leche entera.", 9_000 * PESOS, "unidad", 12, Category.LACTEOS, 4, "Lácteos La Pradera", "Madrid", 5.0, ProductStatus.PENDIENTE),
        Product(12, "Tomate chonto x kg", "Publicación oculta por fotos que no corresponden.", 4_800 * PESOS, "kg", 22, Category.HORTALIZAS, 2, "Finca El Rosal", "Funza", 3.2, ProductStatus.OCULTO)
    )

    val orders = mutableListOf(
        Order(
            "AS-10482", 1, "Juan Pérez",
            listOf(
                OrderItem("Fresa x 500 g", 8_500 * PESOS, 2, 2, Category.FRUTAS),
                OrderItem("Mora x 500 g", 7_000 * PESOS, 1, 2, Category.FRUTAS),
                OrderItem("Queso campesino", 14_000 * PESOS, 1, 4, Category.LACTEOS)
            ),
            6_000 * PESOS, "Cra 15 # 8-21, Funza", "13 oct 2026 · 10:42", OrderStatus.PAGADO
        ),
        Order(
            "AS-10479", 6, "María Gómez",
            listOf(OrderItem("Fresa x 500 g", 8_500 * PESOS, 1, 2, Category.FRUTAS)),
            3_000 * PESOS, "Calle 26 # 68-10, Bogotá", "12 oct 2026 · 17:05", OrderStatus.PREPARANDO
        ),
        Order(
            "AS-10470", 6, "María Gómez",
            listOf(OrderItem("Mora x 500 g", 7_000 * PESOS, 3, 2, Category.FRUTAS)),
            3_000 * PESOS, "Calle 26 # 68-10, Bogotá", "11 oct 2026 · 09:15", OrderStatus.ENVIADO
        ),
        Order(
            "AS-10391", 1, "Juan Pérez",
            listOf(OrderItem("Huevos AA x 30", 19_900 * PESOS, 1, 4, Category.HUEVOS)),
            3_000 * PESOS, "Cra 15 # 8-21, Funza", "6 oct 2026 · 12:30", OrderStatus.ENVIADO
        ),
        Order(
            "AS-10377", 1, "Juan Pérez",
            listOf(OrderItem("Fresa x 500 g", 8_500 * PESOS, 1, 2, Category.FRUTAS)),
            3_000 * PESOS, "Cra 15 # 8-21, Funza", "4 oct 2026 · 08:50", OrderStatus.ENTREGADO
        ),
        Order(
            "AS-10466", 6, "María Gómez",
            listOf(OrderItem("Queso campesino", 14_000 * PESOS, 2, 4, Category.LACTEOS)),
            3_000 * PESOS, "Calle 26 # 68-10, Bogotá", "10 oct 2026 · 15:20", OrderStatus.DISPUTA
        ),
        Order(
            "AS-10431", 7, "Cuenta 1842",
            listOf(OrderItem("Lechuga crespa", 2_500 * PESOS, 4, 2, Category.HORTALIZAS)),
            3_000 * PESOS, "Calle 5 # 3-14, Mosquera", "8 oct 2026 · 11:02", OrderStatus.ANULADO
        )
    )

    val cart = mutableListOf<CartItem>()
    val inactiveCategories = mutableSetOf<Category>()
    val auditLog = mutableListOf<String>()

    private var nextUserId = 100
    private var nextProductId = 100
    private var nextOrderNumber = 10483

    // ---------- Usuarios ----------

    fun user(id: Int): User? = users.find { it.id == id }

    /** Respuesta unica ante cualquier fallo: no revela si el correo existe (RF-AUT-02). */
    fun authenticate(email: String, password: String): User? {
        if (password != DEMO_PASSWORD) return null
        return users.find { it.email.equals(email.trim(), ignoreCase = true) && it.status != UserStatus.SUSPENDIDO }
    }

    fun emailExists(email: String, exceptId: Int? = null) =
        users.any { it.email.equals(email.trim(), ignoreCase = true) && it.id != exceptId }

    fun addUser(name: String, email: String, phone: String, role: Role, municipality: String, status: UserStatus): User {
        val user = User(nextUserId++, name, email.trim(), phone, role, status, municipality)
        users.add(0, user)
        return user
    }

    /** Hay que conservar al menos un administrador activo (RF-ADU-04). */
    fun isLastAdmin(user: User) =
        user.role == Role.ADMIN && users.count { it.role == Role.ADMIN && it.status == UserStatus.ACTIVO } <= 1

    fun deleteUser(user: User) {
        users.remove(user)
        products.removeAll { it.sellerId == user.id && it.sold == 0 }
        products.filter { it.sellerId == user.id }.forEach { it.status = ProductStatus.OCULTO }
    }

    fun audit(adminName: String, action: String) {
        val time = SimpleDateFormat("d MMM · HH:mm", Locale("es", "CO")).format(Date())
        auditLog.add(0, "$time · $adminName · $action")
    }

    // ---------- Productos ----------

    fun product(id: Int): Product? = products.find { it.id == id }

    /** El comprador solo ve productos aprobados de categorias activas (RF-INI-02). */
    fun visibleProducts() = products.filter {
        it.status == ProductStatus.APROBADO && it.category !in inactiveCategories
    }

    fun productsOf(sellerId: Int) = products.filter { it.sellerId == sellerId }

    fun addProduct(
        seller: User, name: String, description: String, priceCents: Long, unit: String,
        stock: Int, category: Category, photo: android.graphics.Bitmap?
    ): Product {
        val product = Product(
            nextProductId++, name, description, priceCents, unit, stock, category,
            seller.id, seller.name, seller.municipality, 3.2, ProductStatus.PENDIENTE, photo = photo
        )
        products.add(0, product)
        return product
    }

    fun deleteProduct(product: Product) {
        products.remove(product)
        cart.removeAll { it.product.id == product.id }
    }

    // ---------- Carrito ----------

    /** @return false si la cantidad supera el stock disponible (RF-CAR-01). */
    fun addToCart(product: Product, quantity: Int): Boolean {
        val existing = cart.find { it.product.id == product.id }
        val total = (existing?.quantity ?: 0) + quantity
        if (total > product.stock) return false
        if (existing != null) existing.quantity = total else cart.add(CartItem(product, quantity))
        return true
    }

    val cartCount: Int get() = cart.sumOf { it.quantity }
    val cartSubtotalCents: Long get() = cart.sumOf { it.subtotalCents }
    val cartShippingCents: Long
        get() = cart.map { it.product.sellerId }.distinct().size * SHIPPING_PER_SELLER_CENTS
    val cartTotalCents: Long get() = cartSubtotalCents + cartShippingCents

    /** Carrito ordenado por vendedor para mostrarlo agrupado (RF-CAR-02). */
    fun cartBySeller() = cart.sortedBy { it.product.sellerName }

    // ---------- Pedidos ----------

    fun order(code: String): Order? = orders.find { it.code == code }

    fun ordersOfBuyer(buyerId: Int) = orders.filter { it.buyerId == buyerId }

    fun ordersOfSeller(sellerId: Int) = orders.filter { order -> order.items.any { it.sellerId == sellerId } }

    /** Simula la confirmacion de la pasarela. En produccion la app nunca marca un pedido como pagado. */
    fun confirmSimulatedPayment(buyer: User, address: String): Order {
        val order = Order(
            "AS-${nextOrderNumber++}", buyer.id, buyer.name,
            cart.map { OrderItem(it.product.name, it.product.priceCents, it.quantity, it.product.sellerId, it.product.category) },
            cartShippingCents, address,
            SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es", "CO")).format(Date()),
            OrderStatus.PAGADO
        )
        cart.forEach {
            it.product.stock -= it.quantity
            it.product.sold += it.quantity
        }
        cart.clear()
        orders.add(0, order)
        return order
    }

    // ---------- Reportes ----------

    private fun isSale(order: Order) =
        order.status != OrderStatus.ANULADO && order.status != OrderStatus.CANCELADO && order.status != OrderStatus.RETRACTO

    val salesTotalCents: Long get() = orders.filter(::isSale).sumOf { it.subtotalCents }
    val salesCount: Int get() = orders.count(::isSale)
    val averageTicketCents: Long get() = if (salesCount == 0) 0 else salesTotalCents / salesCount
    val commissionCents: Long get() = salesTotalCents * COMMISSION_PERCENT / 100

    fun sellerSalesCents(sellerId: Int): Long =
        orders.filter(::isSale).sumOf { order -> order.itemsOf(sellerId).sumOf { it.subtotalCents } }

    fun topSellers(): List<Pair<User, Long>> =
        users.filter { it.role == Role.SELLER }
            .map { it to sellerSalesCents(it.id) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

    fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at < 2) return email
        return email.take(2) + "***" + email.substring(at)
    }
}
