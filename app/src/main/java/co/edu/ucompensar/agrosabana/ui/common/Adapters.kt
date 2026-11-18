package co.edu.ucompensar.agrosabana.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Product
import co.edu.ucompensar.agrosabana.databinding.ItemProductGridBinding
import co.edu.ucompensar.agrosabana.databinding.ItemRowBinding
import co.edu.ucompensar.agrosabana.util.Money
import co.edu.ucompensar.agrosabana.util.ImageCodec
import coil.dispose
import coil.load

/** Fila generica reutilizada por pedidos, usuarios y productos de los tres roles. */
data class Row(
    val id: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val icon: Int,
    @ColorRes val iconBackground: Int,
    val badge: String? = null,
    @ColorRes val badgeColor: Int = R.color.state_ok,
    val trailing: String? = null,
    val photo: String? = null
)

class RowAdapter(private val onClick: ((Row) -> Unit)? = null) :
    ListAdapter<Row, RowAdapter.Holder>(RowDiff) {

    inner class Holder(val binding: ItemRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        with(holder.binding) {
            title.text = row.title
            subtitle.text = row.subtitle
            thumb.showProductImage(row.photo, row.icon, row.iconBackground)
            badge.isVisible = row.badge != null
            badge.text = row.badge
            if (row.badge != null) badge.tintBadge(row.badgeColor)
            trailing.isVisible = row.trailing != null
            trailing.text = row.trailing
            root.isClickable = onClick != null
            root.isFocusable = onClick != null
            if (onClick != null) root.setOnClickListener { onClick.invoke(row) } else root.setOnClickListener(null)
        }
    }

    private object RowDiff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(a: Row, b: Row) = a.id == b.id
        override fun areContentsTheSame(a: Row, b: Row) = a == b
    }
}

class ProductGridAdapter(private val onClick: (Product) -> Unit) :
    RecyclerView.Adapter<ProductGridAdapter.Holder>() {

    private var items: List<Product> = emptyList()

    fun submit(products: List<Product>) {
        items = products
        notifyDataSetChanged()
    }

    inner class Holder(val binding: ItemProductGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemProductGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = items[position]
        with(holder.binding) {
            image.showProductImage(product.photoUrl, product.category.illustration, product.category.background)
            name.text = product.name
            price.text = Money.format(product.priceCents)
            distance.text = Money.distance(product.distanceKm)
            root.setOnClickListener { onClick(product) }
        }
    }
}

/**
 * Muestra la foto del producto descargada de Firebase Storage (con Coil, que la guarda en cache);
 * mientras carga, o si no hay foto, muestra la ilustracion de la categoria sobre su color.
 */
fun ImageView.showProductImage(photoUrl: String?, @DrawableRes icon: Int, @ColorRes background: Int) {
    ViewCompat.setBackgroundTintList(this, ContextCompat.getColorStateList(context, background))
    if (this.background == null) setBackgroundResource(R.drawable.bg_thumb)
    val inset = (10 * resources.displayMetrics.density).toInt()
    if (photoUrl.isNullOrBlank()) {
        dispose()
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(inset, inset, inset, inset)
        setImageResource(icon)
        return
    }
    // Modo beta: la foto viene incrustada en el documento de Firestore.
    if (ImageCodec.isDataUri(photoUrl)) {
        dispose()
        val bitmap = ImageCodec.decode(photoUrl!!)
        if (bitmap != null) {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(0, 0, 0, 0)
            setImageBitmap(bitmap)
        } else {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(inset, inset, inset, inset)
            setImageResource(icon)
        }
        return
    }
    load(photoUrl) {
        crossfade(true)
        placeholder(icon)
        error(icon)
        listener(
            onStart = {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(inset, inset, inset, inset)
            },
            onSuccess = { _, _ ->
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(0, 0, 0, 0)
            }
        )
    }
}

/** Texto oscuro sobre ambar y claro sobre el resto para mantener contraste 4.5:1 (RNF-USA-02). */
fun android.widget.TextView.tintBadge(@ColorRes color: Int) {
    ViewCompat.setBackgroundTintList(this, ContextCompat.getColorStateList(context, color))
    val text = if (color == R.color.state_warn) R.color.text_on_amber else R.color.text_on_primary
    setTextColor(context.getColor(text))
}

fun View.show(visible: Boolean) {
    isVisible = visible
}
