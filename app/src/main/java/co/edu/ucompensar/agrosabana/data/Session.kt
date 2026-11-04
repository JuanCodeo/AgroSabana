package co.edu.ucompensar.agrosabana.data

import android.content.Context
import androidx.core.content.edit

/**
 * Sesion local de la etapa de frontend. Solo guarda identificadores, nunca contrasenas.
 * En el backend el rol se leera del token firmado de Firebase (RF-INI-01), no de este almacen.
 */
class Session(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("agrosabana_session", Context.MODE_PRIVATE)

    var currentUserId: Int
        get() = prefs.getInt(KEY_USER, NONE)
        set(value) = prefs.edit { putInt(KEY_USER, value) }

    /** Usuario que activo el ingreso con huella en este dispositivo. */
    var biometricUserId: Int
        get() = prefs.getInt(KEY_BIOMETRIC, NONE)
        set(value) = prefs.edit { putInt(KEY_BIOMETRIC, value) }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATIONS, value) }

    val currentUser: User? get() = Store.user(currentUserId)

    val biometricEnabled: Boolean get() = biometricUserId != NONE

    fun isBiometricEnabledFor(user: User) = biometricUserId == user.id

    fun disableBiometric() {
        biometricUserId = NONE
    }

    fun logout() {
        currentUserId = NONE
    }

    private companion object {
        const val NONE = -1
        const val KEY_USER = "user"
        const val KEY_BIOMETRIC = "biometric_user"
        const val KEY_NOTIFICATIONS = "notifications"
    }
}
