package co.edu.ucompensar.agrosabana.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.ucompensar.agrosabana.databinding.FragmentListBinding
import com.google.android.material.chip.Chip

/**
 * Plantilla de las pantallas de listado: busqueda opcional, filtros en chips, lista, estado vacio
 * y boton flotante. Cada rol implementa solo los filtros y la construccion de las filas.
 */
abstract class ListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    protected val binding get() = _binding!!

    protected val adapter = RowAdapter { onRowClick(it) }
    protected var selectedFilter = 0
        private set

    @get:StringRes protected abstract val filters: List<Int>
    @get:StringRes protected abstract val emptyMessage: Int
    @get:StringRes protected open val searchHint: Int? = null

    protected abstract fun rows(filterIndex: Int, query: String): List<Row>
    protected open fun onRowClick(row: Row) = Unit
    protected open fun configure() = Unit

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.empty.setText(emptyMessage)
        searchHint?.let { hint ->
            binding.searchLayout.isVisible = true
            binding.searchLayout.setHint(hint)
            binding.search.doAfterTextChanged { refresh() }
        }
        bindFilters()
        configure()
    }

    private fun bindFilters() {
        val ids = filters.mapIndexed { index, label ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(label)
                isCheckable = true
                isChecked = index == 0
            }
            binding.chips.addView(chip)
            chip.id
        }
        binding.chips.setOnCheckedStateChangeListener { _, checked ->
            selectedFilter = ids.indexOf(checked.firstOrNull()).coerceAtLeast(0)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    protected fun refresh() {
        val query = binding.search.text?.toString()?.trim().orEmpty()
        val rows = rows(selectedFilter, query)
        adapter.submitList(rows)
        binding.empty.isVisible = rows.isEmpty()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
