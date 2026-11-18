package co.edu.ucompensar.agrosabana.ui.seller

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Category
import co.edu.ucompensar.agrosabana.data.Product
import co.edu.ucompensar.agrosabana.data.ProductStatus
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.databinding.ActivityProductFormBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.PhotoPicker
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.setOptions
import co.edu.ucompensar.agrosabana.ui.common.setText
import co.edu.ucompensar.agrosabana.ui.common.showProductImage
import co.edu.ucompensar.agrosabana.ui.common.text
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * CRUD de productos del vendedor (RF-PRO-01, RF-PRO-03, RF-PRO-04): crear, actualizar y eliminar.
 * La foto se toma con la camara o se elige de la galeria y se sube a Firebase Storage; el documento
 * del producto queda en Firestore en estado PENDIENTE hasta que el administrador lo apruebe.
 */
class ProductFormActivity : BaseActivity() {

    private lateinit var binding: ActivityProductFormBinding
    private var product: Product? = null
    private var newPhoto: Bitmap? = null
    private val photoPicker = PhotoPicker(this) { showPhoto(it) }
    private val categoryNames by lazy { Category.entries.map { getString(it.label) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        product = intent.getStringExtra(EXTRA_ID)?.let(Store::product)
        setupBackToolbar(binding.toolbar, if (product == null) R.string.new_product else R.string.edit_product)

        binding.category.setOptions(categoryNames)
        binding.category.setText(categoryNames.first(), false)
        product?.let(::fill)

        binding.photoFrame.setOnClickListener { photoPicker.show() }
        binding.photoButton.setOnClickListener { photoPicker.show() }
        binding.saveButton.setOnClickListener { save() }
        binding.deleteButton.isVisible = product != null
        binding.deleteButton.setOnClickListener { confirmDelete() }
    }

    private fun fill(existing: Product) {
        binding.nameLayout.setText(existing.name)
        binding.descriptionLayout.setText(existing.description)
        binding.priceLayout.setText((existing.priceCents / CENTS_PER_PESO).toString())
        binding.stockLayout.setText(existing.stock.toString())
        binding.unitLayout.setText(existing.unit)
        binding.category.setText(getString(existing.category.label), false)
        if (existing.photoUrl != null) {
            binding.photo.showProductImage(existing.photoUrl, existing.category.illustration, existing.category.background)
        }
    }

    private fun showPhoto(bitmap: Bitmap) {
        newPhoto = bitmap
        binding.photo.setImageBitmap(bitmap)
    }

    private fun setBusy(busy: Boolean) {
        binding.saveButton.isEnabled = !busy
        binding.deleteButton.isEnabled = !busy
        binding.saveButton.setText(if (busy) R.string.saving else R.string.send_to_review)
    }

    private fun save() {
        val required = listOf(
            binding.nameLayout.requireValue(),
            binding.descriptionLayout.requireValue(),
            binding.unitLayout.requireValue()
        ).all { it }

        val pesos = binding.priceLayout.text.toLongOrNull()
        val priceOk = pesos != null && pesos > 0
        binding.priceLayout.error = if (priceOk) null else getString(R.string.error_price)

        val stock = binding.stockLayout.text.toIntOrNull()
        val stockOk = stock != null && stock >= 0
        binding.stockLayout.error = if (stockOk) null else getString(R.string.error_stock)

        if (!required || !priceOk || !stockOk) return

        val seller = session.currentUser ?: return logoutAndExit()
        val category = Category.entries[categoryNames.indexOf(binding.category.text.toString()).coerceAtLeast(0)]
        val base = product ?: Product(
            id = "", name = "", description = "", priceCents = 0, unit = "", stock = 0, category = category,
            sellerId = seller.id, sellerName = seller.name, municipality = seller.municipality,
            origin = seller.location, status = ProductStatus.PENDIENTE
        )
        val draft = base.copy(
            name = binding.nameLayout.text,
            description = binding.descriptionLayout.text,
            priceCents = pesos!! * CENTS_PER_PESO,
            unit = binding.unitLayout.text,
            stock = stock!!,
            category = category,
            // El punto de despacho se actualiza si el vendedor cambio su ubicacion.
            origin = seller.location ?: base.origin
        )

        setBusy(true)
        launchTask(onError = { setBusy(false) }) {
            Store.saveProduct(draft, newPhoto)
            Toast.makeText(this, R.string.product_sent, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun confirmDelete() {
        val existing = product ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_product)
            .setMessage(getString(R.string.delete_product_body, existing.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                setBusy(true)
                launchTask(onError = { setBusy(false) }) {
                    Store.deleteProduct(existing)
                    Toast.makeText(this, R.string.product_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .show()
    }

    companion object {
        private const val EXTRA_ID = "product_id"
        private const val CENTS_PER_PESO = 100L

        fun intentFor(context: Context, productId: String) =
            Intent(context, ProductFormActivity::class.java).putExtra(EXTRA_ID, productId)
    }
}
