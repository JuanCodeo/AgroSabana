package co.edu.ucompensar.agrosabana.data

import android.graphics.Bitmap
import co.edu.ucompensar.agrosabana.BuildConfig
import co.edu.ucompensar.agrosabana.util.ImageCodec
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Repositorio unico de datos de la app, respaldado por Cloud Firestore y Firebase Storage.
 *
 * Arquitectura: al iniciar sesion, [start] abre listeners en tiempo real (addSnapshotListener)
 * sobre las colecciones que el rol puede leer segun las reglas de seguridad. Cada cambio en el
 * servidor actualiza las listas en memoria y emite [changes]; las pantallas observan ese LiveData y
 * se redibujan solas. Las escrituras son funciones suspend que van directo a Firestore: la vista se
 * actualiza cuando llega la confirmacion del listener (o al instante gracias a la cache local).
 *
 * Colecciones:
 *  - users/{uid}                     perfil y rol de cada cuenta
 *  - products/{id}                   catalogo con foto en Storage (products/{id}/foto.jpg)
 *  - carts/{uid}/items/{productId}   carrito persistente del comprador
 *  - orders/{code}                   pedidos e historial con datos del pago ePayco
 *  - audit/{id}                      bitacora de acciones administrativas
 *  - config/categories               categorias desactivadas por el administrador
 */
object Store {

    private const val TAG = "Store"

    /** Comision de la plataforma sobre las ventas, mostrada en el reporte del administrador. */
    const val COMMISSION_PERCENT = 8L

    /** Valor que indica que no se puede calcular la distancia (falta una de las ubicaciones). */
    const val UNKNOWN_DISTANCE = 9_999.0

    /** Costo de envio por cada vendedor distinto en el carrito: $ 3.000. */
    private const val SHIPPING_PER_SELLER_CENTS = 300_000L

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    // ---------- Estado en memoria (alimentado por los listeners) ----------

    var currentUser: User? = null
        private set
    var users: List<User> = emptyList()
        private set
    var products: List<Product> = emptyList()
        private set
    var orders: List<Order> = emptyList()
        private set
    var auditLog: List<AuditEntry> = emptyList()
        private set
    var inactiveCategories: Set<Category> = emptySet()
        private set

    /** Cantidades del carrito por id de producto, tal como estan en Firestore. */
    private var cartQuantities: Map<String, Int> = emptyMap()

    private val listeners = mutableListOf<ListenerRegistration>()
    private val _changes = MutableLiveData(0)

    /** Emite un numero nuevo cada vez que cambia cualquier dato; las pantallas lo observan. */
    val changes: LiveData<Int> get() = _changes

    /** Se invoca si la cuenta actual se elimina o se suspende mientras la app esta abierta. */
    var onSessionInvalid: (() -> Unit)? = null

    private fun notifyChanged() {
        _changes.value = (_changes.value ?: 0) + 1
    }

    // ---------- Ciclo de vida de la sesion ----------

    /** Lee una sola vez el perfil users/{uid}; se usa al iniciar sesion antes de abrir listeners. */
    suspend fun fetchUser(uid: String): User? = FirestoreMapper.user(db.collection("users").document(uid).get().await())

    /** Abre los listeners que corresponden al rol del usuario autenticado. */
    fun start(user: User) {
        stop()
        currentUser = user
        val uid = user.id

        // Perfil propio (o todos los usuarios si es administrador).
        if (user.role == Role.ADMIN) {
            listen(db.collection("users")) { snap ->
                users = snap.documents.mapNotNull(FirestoreMapper::user).sortedBy { it.name.lowercase() }
                updateCurrentUser(users.find { it.id == uid })
            }
        } else {
            listeners += db.collection("users").document(uid).addSnapshotListener { doc, error ->
                if (error != null) { Log.w(TAG, "users/$uid", error); return@addSnapshotListener }
                val me = doc?.let(FirestoreMapper::user)
                users = listOfNotNull(me)
                updateCurrentUser(me)
            }
        }

        // Productos: el comprador solo ve los aprobados, el vendedor los suyos y el admin todos.
        val productQuery: Query = when (user.role) {
            Role.BUYER -> db.collection("products").whereEqualTo("status", ProductStatus.APROBADO.name)
            Role.SELLER -> db.collection("products").whereEqualTo("sellerId", uid)
            Role.ADMIN -> db.collection("products")
        }
        listen(productQuery) { snap ->
            products = snap.documents.mapNotNull(FirestoreMapper::product).sortedBy { it.name.lowercase() }
        }

        // Pedidos: el comprador ve los propios y el vendedor los que incluyen sus productos.
        val orderQuery: Query = when (user.role) {
            Role.BUYER -> db.collection("orders").whereEqualTo("buyerId", uid)
            Role.SELLER -> db.collection("orders").whereArrayContains("sellerIds", uid)
            Role.ADMIN -> db.collection("orders")
        }
        listen(orderQuery) { snap ->
            orders = snap.documents.mapNotNull(FirestoreMapper::order).sortedByDescending { it.createdAt }
        }

        if (user.role == Role.BUYER) {
            listen(db.collection("carts").document(uid).collection("items")) { snap ->
                cartQuantities = snap.documents.associate { it.id to (it.getLong("quantity")?.toInt() ?: 0) }
            }
        }

        if (user.role == Role.ADMIN) {
            listen(db.collection("audit").orderBy("createdAt", Query.Direction.DESCENDING).limit(100)) { snap ->
                auditLog = snap.documents.map(FirestoreMapper::audit)
            }
        }

        listeners += db.collection("config").document("categories").addSnapshotListener { doc, error ->
            if (error != null) { Log.w(TAG, "config/categories", error); return@addSnapshotListener }
            @Suppress("UNCHECKED_CAST")
            val names = doc?.get("inactive") as? List<String> ?: emptyList()
            inactiveCategories = Category.entries.filter { it.name in names }.toSet()
            notifyChanged()
        }
        notifyChanged()
    }

    /** Cierra los listeners y limpia la memoria; se llama al cerrar sesion. */
    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
        currentUser = null
        users = emptyList()
        products = emptyList()
        orders = emptyList()
        auditLog = emptyList()
        cartQuantities = emptyMap()
        inactiveCategories = emptySet()
    }

    private fun listen(query: Query, onSnapshot: (com.google.firebase.firestore.QuerySnapshot) -> Unit) {
        listeners += query.addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Listener rechazado", error)
                return@addSnapshotListener
            }
            if (snap != null) {
                onSnapshot(snap)
                notifyChanged()
            }
        }
    }

    /** Si el perfil desaparece o queda suspendido, la sesion deja de ser valida. */
    private fun updateCurrentUser(me: User?) {
        if (me == null || me.status == UserStatus.SUSPENDIDO) {
            if (currentUser != null) onSessionInvalid?.invoke()
            return
        }
        currentUser = me
    }

    // ---------- Usuarios ----------

    fun user(id: String): User? = users.find { it.id == id }

    /** Crea o reemplaza el documento de perfil users/{uid}. */
    suspend fun saveUser(user: User) {
        db.collection("users").document(user.id).set(FirestoreMapper.userMap(user)).await()
    }

    /** Actualiza solo los campos editables del perfil propio (las reglas impiden cambiar rol o estado). */
    suspend fun updateProfile(user: User, photo: Bitmap?) {
        val photoUrl = if (photo != null) uploadImage("users/${user.id}/avatar.jpg", photo) else user.photoUrl
        db.collection("users").document(user.id).update(
            mapOf(
                "name" to user.name,
                "phone" to user.phone,
                "municipality" to user.municipality,
                "address" to user.address,
                "location" to user.location?.let(FirestoreMapper::geoMap),
                "deliveryRadiusKm" to user.deliveryRadiusKm,
                "photoUrl" to photoUrl
            )
        ).await()
    }

    /** Guarda la ultima ubicacion del usuario para calcular distancias y prellenar la entrega. */
    suspend fun saveLocation(point: GeoPoint, address: String?) {
        val me = currentUser ?: return
        val data = mutableMapOf<String, Any>("location" to FirestoreMapper.geoMap(point))
        if (!address.isNullOrBlank()) data["address"] = address
        db.collection("users").document(me.id).update(data).await()
    }

    /** Cambia el estado de una cuenta; suspender oculta sus productos (RF-ADU-03). */
    suspend fun setUserStatus(user: User, status: UserStatus) {
        val batch = db.batch()
        batch.update(db.collection("users").document(user.id), "status", status.name)
        if (status == UserStatus.SUSPENDIDO) {
            products.filter { it.sellerId == user.id }.forEach {
                batch.update(db.collection("products").document(it.id), "status", ProductStatus.OCULTO.name)
            }
        }
        batch.commit().await()
    }

    /** Hay que conservar al menos un administrador activo (RF-ADU-04). */
    fun isLastAdmin(user: User) =
        user.role == Role.ADMIN && users.count { it.role == Role.ADMIN && it.status == UserStatus.ACTIVO } <= 1

    /**
     * Elimina el perfil. Los productos sin ventas se borran y los demas se ocultan para no romper
     * el historial de pedidos. La cuenta de Authentication sin perfil ya no puede entrar a la app.
     */
    suspend fun deleteUser(user: User) {
        val batch = db.batch()
        val own = db.collection("products").whereEqualTo("sellerId", user.id).get().await()
        own.documents.forEach { doc ->
            if ((doc.getLong("sold") ?: 0L) == 0L) batch.delete(doc.reference)
            else batch.update(doc.reference, "status", ProductStatus.OCULTO.name)
        }
        batch.delete(db.collection("users").document(user.id))
        batch.commit().await()
    }

    /** Registra una accion administrativa en la bitacora. */
    suspend fun audit(adminName: String, action: String) {
        db.collection("audit").add(
            mapOf("adminName" to adminName, "action" to action, "createdAt" to FieldValue.serverTimestamp())
        ).await()
    }

    // ---------- Productos ----------

    fun product(id: String): Product? = products.find { it.id == id }

    /** El comprador solo ve productos aprobados de categorias activas (RF-INI-02). */
    fun visibleProducts() = products.filter {
        it.status == ProductStatus.APROBADO && it.category !in inactiveCategories
    }

    fun productsOf(sellerId: String) = products.filter { it.sellerId == sellerId }

    /**
     * Crea o actualiza un producto. Si hay foto nueva se sube primero a Firebase Storage y se guarda
     * su URL de descarga en el documento. Cualquier cambio del vendedor vuelve a moderacion.
     */
    suspend fun saveProduct(product: Product, photo: Bitmap?): Product {
        val ref = if (product.id.isEmpty()) db.collection("products").document() else db.collection("products").document(product.id)
        val photoUrl = if (photo != null) uploadImage("products/${ref.id}/${System.currentTimeMillis()}.jpg", photo) else product.photoUrl
        val saved = product.copy(id = ref.id, photoUrl = photoUrl, status = ProductStatus.PENDIENTE)
        val data = FirestoreMapper.productMap(saved).toMutableMap()
        data["updatedAt"] = FieldValue.serverTimestamp()
        ref.set(data, SetOptions.merge()).await()
        return saved
    }

    /** Decision de moderacion del administrador (RF-ADP-01). */
    suspend fun setProductStatus(product: Product, status: ProductStatus) {
        db.collection("products").document(product.id).update("status", status.name).await()
    }

    /** Borra el documento (y sus fotos en Storage si ese modo esta activo). */
    suspend fun deleteProduct(product: Product) {
        db.collection("products").document(product.id).delete().await()
        if (!BuildConfig.USE_FIREBASE_STORAGE) return
        runCatching {
            val folder = storage.reference.child("products/${product.id}")
            folder.listAll().await().items.forEach { it.delete().await() }
        }.onFailure { Log.w(TAG, "No se borraron las fotos de ${product.id}", it) }
    }

    /**
     * Guarda una foto y devuelve el valor para el campo photoUrl.
     *
     * Modo beta (por defecto): la foto se comprime y se guarda como data URI en el mismo documento
     * de Firestore, porque Firebase Storage exige el plan de pago Blaze. Con
     * USE_FIREBASE_STORAGE=true se sube a Storage y se guarda su URL publica de descarga.
     */
    private suspend fun uploadImage(path: String, bitmap: Bitmap): String {
        if (!BuildConfig.USE_FIREBASE_STORAGE) return ImageCodec.encode(bitmap)
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        val ref = storage.reference.child(path)
        ref.putBytes(bytes, com.google.firebase.storage.storageMetadata { contentType = "image/jpeg" }).await()
        return ref.downloadUrl.await().toString()
    }

    private const val JPEG_QUALITY = 82

    // ---------- Categorias ----------

    suspend fun setInactiveCategories(inactive: Set<Category>) {
        db.collection("config").document("categories")
            .set(mapOf("inactive" to inactive.map { it.name })).await()
    }

    // ---------- Carrito (carts/{uid}/items) ----------

    /** Items del carrito unidos al producto vigente; si el producto ya no esta aprobado se omite. */
    val cart: List<CartItem>
        get() = cartQuantities.mapNotNull { (id, quantity) -> product(id)?.let { CartItem(it, quantity) } }

    private fun cartRef(productId: String) =
        db.collection("carts").document(currentUser?.id.orEmpty()).collection("items").document(productId)

    /** @return false si la cantidad total supera el stock disponible (RF-CAR-01). */
    suspend fun addToCart(product: Product, quantity: Int): Boolean {
        val total = (cartQuantities[product.id] ?: 0) + quantity
        if (total > product.stock) return false
        setCartQuantity(product, total)
        return true
    }

    suspend fun setCartQuantity(product: Product, quantity: Int) {
        cartRef(product.id).set(mapOf("quantity" to quantity, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun removeFromCart(product: Product) {
        cartRef(product.id).delete().await()
    }

    /** Vuelve a escribir items borrados; se usa para "Deshacer" (RF-CAR-04). */
    suspend fun restoreCart(items: List<CartItem>) {
        val batch = db.batch()
        items.forEach { batch.set(cartRef(it.product.id), mapOf("quantity" to it.quantity)) }
        batch.commit().await()
    }

    suspend fun clearCart() {
        val batch = db.batch()
        cartQuantities.keys.forEach { batch.delete(cartRef(it)) }
        batch.commit().await()
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

    fun ordersOfBuyer(buyerId: String) = orders.filter { it.buyerId == buyerId }

    fun ordersOfSeller(sellerId: String) = orders.filter { order -> order.items.any { it.sellerId == sellerId } }

    /**
     * Crea el pedido en estado PENDIENTE_PAGO con los precios congelados del carrito.
     * El codigo (AS-aaMMdd-XXXX) es tambien la referencia de factura que se envia a ePayco.
     */
    suspend fun createOrder(buyer: User, address: String, location: GeoPoint?): Order {
        val code = "AS-" + SimpleDateFormat("yyMMdd", Locale.US).format(Date()) + "-" +
            (1..4).map { CODE_CHARS.random() }.joinToString("")
        val order = Order(
            code = code,
            buyerId = buyer.id,
            buyerName = buyer.name,
            items = cart.map {
                OrderItem(it.product.id, it.product.name, it.product.priceCents, it.quantity, it.product.sellerId, it.product.category)
            },
            shippingCents = cartShippingCents,
            address = address,
            location = location,
            createdAt = Date(),
            status = OrderStatus.PENDIENTE_PAGO
        )
        db.collection("orders").document(code).set(FirestoreMapper.orderMap(order)).await()
        return order
    }

    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Lee un pedido directo del servidor (por ejemplo al volver de la pasarela). */
    suspend fun fetchOrder(code: String): Order? =
        FirestoreMapper.order(db.collection("orders").document(code).get().await())

    /**
     * Registra el resultado validado de ePayco. Si la transaccion fue aceptada, en una transaccion
     * de Firestore se descuenta el inventario, se suma lo vendido y el pedido pasa a PAGADO; despues
     * se vacia el carrito. Si fue rechazada, el pedido queda en PAGO_RECHAZADO.
     */
    suspend fun applyPayment(order: Order, payment: PaymentInfo, approved: Boolean) {
        val orderRef = db.collection("orders").document(order.code)
        val newStatus = if (approved) OrderStatus.PAGADO else OrderStatus.PAGO_RECHAZADO
        db.runTransaction { tx ->
            val current = tx.get(orderRef)
            // Si el pedido ya se proceso (por ejemplo, la pantalla se reabrio), no se repite.
            if (current.getString("status") != OrderStatus.PENDIENTE_PAGO.name) return@runTransaction
            if (approved) {
                val refs = order.items.groupBy { it.productId }
                val snapshots = refs.keys.associateWith { tx.get(db.collection("products").document(it)) }
                refs.forEach { (productId, items) ->
                    val snap = snapshots.getValue(productId)
                    val stock = snap.getLong("stock") ?: 0L
                    val quantity = items.sumOf { it.quantity }.toLong()
                    if (snap.exists() && stock > 0) {
                        tx.update(
                            snap.reference,
                            mapOf("stock" to (stock - quantity).coerceAtLeast(0), "sold" to FieldValue.increment(quantity))
                        )
                    }
                }
            }
            tx.update(
                orderRef,
                mapOf(
                    "status" to newStatus.name,
                    "payment" to FirestoreMapper.paymentMap(payment),
                    "paidAt" to if (approved) Timestamp.now() else null
                )
            )
        }.await()
        if (approved) clearCart()
    }

    /**
     * Guarda la referencia de una transaccion que ePayco aun reporta como pendiente (por ejemplo,
     * un PSE en proceso), para volver a verificarla despues desde el historial.
     */
    suspend fun savePendingPayment(order: Order, payment: PaymentInfo) {
        db.collection("orders").document(order.code).update("payment", FirestoreMapper.paymentMap(payment)).await()
    }

    /** Cambio de estado del pedido por comprador, vendedor o administrador (RF-PED-02 a 04). */
    suspend fun setOrderStatus(order: Order, status: OrderStatus, problem: String? = null) {
        val data = mutableMapOf<String, Any>("status" to status.name, "updatedAt" to FieldValue.serverTimestamp())
        if (problem != null) data["problem"] = problem
        db.collection("orders").document(order.code).update(data).await()
    }

    // ---------- Reportes ----------

    private val sales get() = orders.filter { it.status.isSale }

    val salesTotalCents: Long get() = sales.sumOf { it.subtotalCents }
    val salesCount: Int get() = sales.size
    val averageTicketCents: Long get() = if (salesCount == 0) 0 else salesTotalCents / salesCount
    val commissionCents: Long get() = salesTotalCents * COMMISSION_PERCENT / 100

    fun sellerSalesCents(sellerId: String): Long =
        sales.sumOf { order -> order.itemsOf(sellerId).sumOf { it.subtotalCents } }

    /** Ventas por dia de los ultimos [days] dias (para la grafica de barras). */
    fun dailySales(days: Int, sellerId: String? = null): List<Int> {
        val dayMs = 24 * 60 * 60 * 1000L
        val today = System.currentTimeMillis() / dayMs
        return (days - 1 downTo 0).map { back ->
            sales.filter { it.createdAt.time / dayMs == today - back }
                .count { sellerId == null || sellerId in it.sellerIds }
        }
    }

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

    // ---------- Geolocalizacion ----------

    /** Distancia en km (formula de Haversine) entre el usuario actual y un punto de origen. */
    fun distanceTo(origin: GeoPoint?): Double {
        val me = currentUser?.location ?: return UNKNOWN_DISTANCE
        if (origin == null) return UNKNOWN_DISTANCE
        val earthKm = 6371.0
        val dLat = Math.toRadians(origin.lat - me.lat)
        val dLng = Math.toRadians(origin.lng - me.lng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(me.lat)) * cos(Math.toRadians(origin.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return earthKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
