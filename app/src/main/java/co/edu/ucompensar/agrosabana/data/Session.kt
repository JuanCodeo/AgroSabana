package co.edu.ucompensar.agrosabana.data

import android.content.Context
import androidx.core.content.edit

/**
 * Preferencias locales del dispositivo. La sesion en si la guarda Firebase Authentication, y las
 * credenciales para la huella las cifra [co.edu.ucompensar.agrosabana.ui.common.Biometrics].
 */
class Session(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("agrosabana_session", Context.MODE_PRIVATE)

    /** UID de Firebase del usuario autenticado, o cadena vacia si no hay sesion. */
    val currentUserId: String get() = AuthRepository.currentUid.orEmpty()

    /** Perfil del usuario autenticado, mantenido al dia por el listener de Firestore. */
    val currentUser: User? get() = Store.currentUser

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATIONS, value) }

    fun logout() = AuthRepository.logout()

    private companion object {
        const val KEY_NOTIFICATIONS = "notifications"
    }
}
