package co.edu.ucompensar.agrosabana.ui.common

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import co.edu.ucompensar.agrosabana.R
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Inicio de sesion con huella dactilar (RF-AUT-04) usando BiometricPrompt clase BIOMETRIC_STRONG.
 *
 * No basta con mostrar el dialogo de huella: las credenciales se cifran con una llave AES-256 del
 * Android Keystore creada con setUserAuthenticationRequired(true). El sistema solo libera esa llave
 * despues de una huella valida (CryptoObject), por lo que sin la huella del dueno no se pueden
 * descifrar. Si se registra una huella nueva en el telefono, la llave se invalida y el usuario debe
 * volver a activar la opcion con su contrasena.
 */
object Biometrics {

    private const val KEY_ALIAS = "agrosabana_biometric_key"
    private const val PREFS = "agrosabana_biometric"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /** true si el dispositivo tiene sensor y al menos una huella registrada. */
    fun isAvailable(context: Context) =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** true si hay credenciales cifradas guardadas en este dispositivo. */
    fun isEnabled(context: Context) = prefs(context).contains("data")

    /** Correo asociado a la huella, para mostrar en la configuracion del perfil. */
    fun enabledEmail(context: Context): String? = prefs(context).getString("email", null)

    fun disable(context: Context) {
        prefs(context).edit { clear() }
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    /**
     * Activa la huella: pide una huella para autorizar el cifrado y guarda correo y contrasena
     * cifrados. [onDone] recibe true si quedo activa.
     */
    fun enable(activity: FragmentActivity, email: String, password: String, onDone: (Boolean) -> Unit) {
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            disable(activity)
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        } catch (_: Exception) {
            onDone(false)
            return
        }
        prompt(activity, cipher, R.string.biometric_enable_title, onError = { onDone(false) }) { authorized ->
            val encrypted = authorized.doFinal("$email\n$password".toByteArray(Charsets.UTF_8))
            prefs(activity).edit {
                putString("email", email)
                putString("iv", Base64.encodeToString(authorized.iv, Base64.NO_WRAP))
                putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
            onDone(true)
        }
    }

    /**
     * Pide la huella y, si es valida, descifra las credenciales y las entrega a [onCredentials]
     * para iniciar sesion en Firebase. [onInvalidated] se llama si la llave ya no sirve.
     */
    fun unlock(
        activity: FragmentActivity,
        onCredentials: (email: String, password: String) -> Unit,
        onInvalidated: () -> Unit,
        onError: () -> Unit
    ) {
        val stored = prefs(activity)
        val iv = stored.getString("iv", null)
        val data = stored.getString("data", null)
        if (iv == null || data == null) return onInvalidated()
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            disable(activity)
            return onInvalidated()
        } catch (_: Exception) {
            return onError()
        }
        prompt(activity, cipher, R.string.biometric_title, onError) { authorized ->
            val plain = runCatching {
                String(authorized.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
            }.getOrNull()
            val parts = plain?.split("\n", limit = 2)
            if (parts == null || parts.size != 2) onError() else onCredentials(parts[0], parts[1])
        }
    }

    /** Muestra BiometricPrompt ligado al cifrador; solo devuelve el cifrador si la huella es valida. */
    private fun prompt(
        activity: FragmentActivity,
        cipher: Cipher,
        title: Int,
        onError: () -> Unit,
        onSuccess: (Cipher) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorized = result.cryptoObject?.cipher
                    if (authorized == null) onError() else runCatching { onSuccess(authorized) }.onFailure { onError() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!cancelled) onError()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(title))
            .setSubtitle(activity.getString(R.string.biometric_subtitle))
            .setNegativeButtonText(activity.getString(R.string.biometric_use_password))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** Llave AES del Keystore que exige huella en cada uso y se invalida con huellas nuevas. */
    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }
}
