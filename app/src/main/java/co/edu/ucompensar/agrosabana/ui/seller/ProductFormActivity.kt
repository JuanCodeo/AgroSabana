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
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.setOptions
import co.edu.ucompensar.agrosabana.ui.common.setText
import co.edu.ucompensar.agrosabana.ui.common.text
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Crear, actualizar y eliminar producto (RF-PRO-01, RF-PRO-03, RF-PRO-04). */
class ProductFormActivity : BaseActivity() {

    private lateinit var binding: ActivityProductFormBinding
    private var product: Product? = null
    private var photo: Bitmap? = null
    private val photoPicker = PhotoPicker(this) { showPhoto(it) }
    private val categoryNames by lazy { Category.entries.map { getString(it.label) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        product = Store.product(intent.getIntExtra(EXTRA_ID, NO_ID))
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
        existing.photo?.let(::showPhoto)
    }

    private fun showPhoto(bitmap: Bitmap) {
        photo = bitmap
        binding.photo.setImageBitmap(bitmap)
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

        val category = Category.entries[categoryNames.indexOf(binding.category.text.toString()).coerceAtLeast(0)]
        val existing = product
        if (existing == null) {
            val seller = session.currentUser ?: return logoutAndExit()
            Store.addProduct(
                seller, binding.nameLayout.text, binding.descriptionLayout.text,
                pesos!! * CENTS_PER_PESO, binding.unitLayout.text, stock!!, category, photo
            )
        } else {
            existing.name = binding.nameLayout.text
            existing.description = binding.descriptionLayout.text
            existing.priceCents = pesos!! * CENTS_PER_PESO
            existing.unit = binding.unitLayout.text
            existing.stock = stock!!
            existing.category = category
            existing.photo = photo
            // Los cambios vuelven a moderacion (RF-PRO-03).
            existing.status = ProductStatus.PENDIENTE
        }
        Toast.makeText(this, R.string.product_sent, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        val existing = product ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_product)
            .setMessage(getString(R.string.delete_product_body, existing.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                Store.deleteProduct(existing)
                Toast.makeText(this, R.string.product_deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    companion object {
        private const val EXTRA_ID = "product_id"
        private const val NO_ID = -1
        private const val CENTS_PER_PESO = 100L

        fun intentFor(context: Context, productId: Int) =
            Intent(context, ProductFormActivity::class.java).putExtra(EXTRA_ID, productId)
    }
}
