package co.edu.ucompensar.agrosabana.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.CartItem
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.databinding.FragmentCartBinding
import co.edu.ucompensar.agrosabana.databinding.ItemCartBinding
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.showProductImage
import co.edu.ucompensar.agrosabana.util.Money
import com.google.android.material.snackbar.Snackbar

/**
 * CRUD del carrito agrupado por vendedor (RF-CAR-01 a RF-CAR-04).
 * El carrito vive en Firestore (carts/{uid}/items), asi se conserva entre sesiones y dispositivos.
 */
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private val adapter = CartAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.continueButton.setOnClickListener {
            startActivity(Intent(requireContext(), CheckoutActivity::class.java))
        }
        binding.clearButton.setOnClickListener { clearCart() }
        Store.changes.observe(viewLifecycleOwner) { refresh() }
    }

    private fun refresh() {
        if (_binding == null) return
        adapter.submit(Store.cartBySeller())
        val empty = Store.cart.isEmpty()
        binding.empty.isVisible = empty
        binding.summary.isVisible = !empty
        binding.subtotal.text = Money.format(Store.cartSubtotalCents)
        binding.shipping.text = Money.format(Store.cartShippingCents)
        binding.total.text = Money.format(Store.cartTotalCents)
        (activity as? BuyerActivity)?.refreshCartBadge()
    }

    private fun remove(item: CartItem) = launchTask {
        Store.removeFromCart(item.product)
        showUndo(R.string.item_removed, listOf(item))
    }

    private fun clearCart() = launchTask {
        val backup = Store.cart
        Store.clearCart()
        showUndo(R.string.cart_cleared, backup)
    }

    private fun changeQuantity(item: CartItem, quantity: Int) = launchTask {
        Store.setCartQuantity(item.product, quantity)
    }

    /** Deshacer disponible durante 5 segundos (RF-CAR-04). */
    private fun showUndo(message: Int, items: List<CartItem>) {
        if (_binding == null) return
        Snackbar.make(binding.root, message, UNDO_MILLIS)
            .setAction(R.string.undo) { launchTask { Store.restoreCart(items) } }
            .show()
    }

    private inner class CartAdapter : RecyclerView.Adapter<CartAdapter.Holder>() {
        private var items: List<CartItem> = emptyList()

        fun submit(list: List<CartItem>) {
            items = list
            notifyDataSetChanged()
        }

        inner class Holder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val product = item.product
            with(holder.binding) {
                val startsGroup = position == 0 || items[position - 1].product.sellerId != product.sellerId
                sellerHeader.isVisible = startsGroup
                sellerHeader.text = product.sellerName
                thumb.showProductImage(product.photoUrl, product.category.illustration, product.category.background)
                name.text = product.name
                subtotal.text = Money.format(item.subtotalCents)
                quantity.text = item.quantity.toString()
                increase.isEnabled = item.quantity < product.stock
                increase.alpha = if (increase.isEnabled) 1f else DISABLED_ALPHA
                increase.setOnClickListener { changeQuantity(item, item.quantity + 1) }
                decrease.setOnClickListener {
                    if (item.quantity > 1) changeQuantity(item, item.quantity - 1) else remove(item)
                }
                remove.setOnClickListener { remove(item) }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val UNDO_MILLIS = 5000
        const val DISABLED_ALPHA = 0.35f
    }
}
