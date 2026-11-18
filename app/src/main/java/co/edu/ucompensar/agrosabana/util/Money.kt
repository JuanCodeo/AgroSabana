package co.edu.ucompensar.agrosabana.util

import java.text.NumberFormat
import java.util.Locale

/** Pesos colombianos sin decimales (RNF-USA-04). */
object Money {
    private val locale = Locale("es", "CO")
    private val number = NumberFormat.getIntegerInstance(locale)

    fun format(cents: Long): String = "$ " + number.format(cents / 100)

    /** Distancia legible; cadena vacia si no se conoce la ubicacion del comprador o del vendedor. */
    fun distance(km: Double): String =
        if (km >= co.edu.ucompensar.agrosabana.data.Store.UNKNOWN_DISTANCE) "" else String.format(locale, "%.1f km", km)
}
