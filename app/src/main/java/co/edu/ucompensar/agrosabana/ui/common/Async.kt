package co.edu.ucompensar.agrosabana.ui.common

import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import co.edu.ucompensar.agrosabana.R
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Ejecucion de operaciones de red (Firestore, Storage, ePayco) desde la interfaz.
 *
 * Las operaciones corren en el lifecycleScope de la pantalla, asi se cancelan si el usuario sale.
 * Cualquier excepcion se registra en Logcat y se muestra como un Snackbar entendible, en lugar de
 * cerrar la app.
 */

private fun LifecycleCoroutineScope.launchTask(
    anchor: () -> View?,
    onError: ((Exception) -> Unit)?,
    block: suspend () -> Unit
): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("AgroSabana", "Operacion fallida", e)
        onError?.invoke(e)
        anchor()?.let { Snackbar.make(it, errorMessage(e), Snackbar.LENGTH_LONG).show() }
    }
}

/** Mensaje de error para el usuario segun el tipo de excepcion. */
fun errorMessage(e: Exception): Int = when {
    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_permission
    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.error_network
    e is java.io.IOException -> R.string.error_network
    else -> R.string.error_generic
}

fun FragmentActivity.launchTask(onError: ((Exception) -> Unit)? = null, block: suspend () -> Unit): Job =
    lifecycleScope.launchTask({ findViewById(android.R.id.content) }, onError, block)

fun Fragment.launchTask(onError: ((Exception) -> Unit)? = null, block: suspend () -> Unit): Job =
    viewLifecycleOwner.lifecycleScope.launchTask({ view }, onError, block)
