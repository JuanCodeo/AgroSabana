package co.edu.ucompensar.agrosabana.ui.buyer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Category
import co.edu.ucompensar.agrosabana.data.Product
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.databinding.FragmentBuyerHomeBinding
import co.edu.ucompensar.agrosabana.databinding.FragmentCatalogBinding
import co.edu.ucompensar.agrosabana.ui.common.ProductGridAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

private const val GRID_COLUMNS = 2
private const val NEAR_KM = 5.0
private const val HOME_PRODUCTS = 6

/** Agrega "Todo" y las categorias activas; onSelect recibe null cuando se elige "Todo". */
private fun ChipGroup.bindCategories(onSelect: (Category?) -> Unit) {
    removeAllViews()
    val all = Chip(context).apply {
        id = View.generateViewId()
        setText(R.string.all)
        isCheckable = true
        isChecked = true
    }
    addView(all)
    val ids = mutableMapOf(all.id to null as Category?)
    Category.entries.filter { it !in Store.inactiveCategories }.forEach { category ->
        val chip = Chip(context).apply {
            id = View.generateViewId()
            setText(category.label)
            isCheckable = true
        }
        ids[chip.id] = category
        addView(chip)
    }
    setOnCheckedStateChangeListener { _, checked -> onSelect(ids[checked.firstOrNull()]) }
}

private fun Fragment.openDetail(product: Product) {
    startActivity(ProductDetailActivity.intentFor(requireContext(), product.id))
}

class BuyerHomeFragment : Fragment() {

    private var _binding: FragmentBuyerHomeBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductGridAdapter { openDetail(it) }
    private var category: Category? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentBuyerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.grid.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.grid.adapter = adapter
        binding.categoryChips.bindCategories {
            category = it
            refresh()
        }
        val openCatalog = View.OnClickListener { (activity as BuyerActivity).selectDestination(R.id.nav_catalog) }
        binding.searchCard.setOnClickListener(openCatalog)
        binding.catalogButton.setOnClickListener(openCatalog)
        Store.changes.observe(viewLifecycleOwner) { refresh() }
    }

    private fun refresh() {
        if (_binding == null) return
        adapter.submit(
            Store.visibleProducts()
                .filter { category == null || it.category == category }
                .sortedBy { it.distanceKm }
                .take(HOME_PRODUCTS)
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

/** Catalogo con busqueda y filtros por categoria, distancia y precio (RF-INI-03). */
class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductGridAdapter { openDetail(it) }
    private var category: Category? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.grid.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.grid.adapter = adapter
        binding.categoryChips.bindCategories {
            category = it
            refresh()
        }
        binding.search.doAfterTextChanged { refresh() }
        binding.nearChip.isCheckable = true
        binding.priceChip.isCheckable = true
        binding.nearChip.setOnCheckedChangeListener { _, _ -> refresh() }
        binding.priceChip.setOnCheckedChangeListener { _, _ -> refresh() }
        Store.changes.observe(viewLifecycleOwner) { refresh() }
    }

    private fun refresh() {
        if (_binding == null) return
        val query = binding.search.text?.toString()?.trim().orEmpty()
        val filtered = Store.visibleProducts().filter { product ->
            (category == null || product.category == category) &&
                (query.isEmpty() || product.name.contains(query, ignoreCase = true)) &&
                (!binding.nearChip.isChecked || product.distanceKm <= NEAR_KM)
        }
        val results = if (binding.priceChip.isChecked) filtered.sortedBy { it.priceCents } else filtered.sortedBy { it.distanceKm }
        adapter.submit(results)
        binding.empty.isVisible = results.isEmpty()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
