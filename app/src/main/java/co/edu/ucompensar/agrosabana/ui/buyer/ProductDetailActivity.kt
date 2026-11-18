package co.edu.ucompensar.agrosabana.ui.buyer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Product
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.databinding.ActivityProductDetailBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.showProductImage
import co.edu.ucompensar.agrosabana.util.Money
import com.google.android.material.snackbar.Snackbar

/** Detalle publico: no expone telefono ni direccion del vendedor (RF-INI-04, Ley 1581). */
class ProductDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var product: Product
    private var quantity = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.detail_title)

        product = Store.product(intent.getStringExtra(EXTRA_ID).orEmpty()) ?: run { finish(); return }
        binding.image.showProductImage(product.photoUrl, product.category.illustration, product.category.background)
        binding.name.text = product.name
        binding.seller.text = getString(R.string.seller_line, product.sellerName, product.municipality, Money.distance(product.distanceKm)).trimEnd(' ', '·')
        binding.price.text = Money.format(product.priceCents)
        binding.available.text = resources.getQuantityString(R.plurals.units, product.stock, product.stock)
        binding.description.text = product.description
        binding.unit.text = getString(R.string.unit_format, product.unit)

        binding.decrease.setOnClickListener { changeQuantity(-1) }
        binding.increase.setOnClickListener { changeQuantity(1) }
        binding.addButton.setOnClickListener { addToCart() }

        if (product.stock == 0) {
            binding.addButton.isEnabled = false
            binding.addButton.setText(R.string.out_of_stock)
        }
    }

    private fun changeQuantity(delta: Int) {
        quantity = (quantity + delta).coerceIn(1, product.stock.coerceAtLeast(1))
        binding.quantity.text = quantity.toString()
    }

    /** Agrega al carrito persistente en Firestore (carts/{uid}/items). */
    private fun addToCart() = launchTask {
        if (Store.addToCart(product, quantity)) {
            Snackbar.make(binding.root, R.string.added_to_cart, Snackbar.LENGTH_LONG)
                .setAction(R.string.go_to_cart) {
                    startActivity(BuyerActivity.intentFor(this, R.id.nav_cart))
                }
                .show()
        } else {
            Snackbar.make(binding.root, R.string.not_enough_stock, Snackbar.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val EXTRA_ID = "product_id"

        fun intentFor(context: Context, productId: String) =
            Intent(context, ProductDetailActivity::class.java).putExtra(EXTRA_ID, productId)
    }
}
