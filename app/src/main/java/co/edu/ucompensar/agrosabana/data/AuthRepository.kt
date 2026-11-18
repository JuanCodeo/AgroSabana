package co.edu.ucompensar.agrosabana.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Resultado de un intento de inicio de sesion. */
sealed class LoginResult {
    data class Success(val user: User) : LoginResult()

    /** Credenciales invalidas: el mensaje es unico para no revelar si el correo existe (RF-AUT-02). */
    data object InvalidCredentials : LoginResult()

    /** La cuenta existe pero esta suspendida o fue eliminada por el administrador. */
    data object Blocked : LoginResult()

    data class Error(val cause: Throwable) : LoginResult()
}

/**
 * Autenticacion con Firebase Authentication (correo y contrasena).
 *
 * Firebase guarda la sesion de forma segura en el dispositivo; el rol nunca se toma del cliente
 * sino del documento users/{uid}, cuyo rol y estado solo puede cambiar un administrador.
 */
object AuthRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** UID de la sesion activa o null si no hay sesion. */
    val currentUid: String? get() = auth.currentUser?.uid

    /** Inicia sesion y carga el perfil; si esta suspendido o no existe, cierra la sesion. */
    suspend fun login(email: String, password: String): LoginResult = try {
        val uid = auth.signInWithEmailAndPassword(email.trim(), password).await().user!!.uid
        loadProfile(uid)
    } catch (e: Exception) {
        if (e is com.google.firebase.auth.FirebaseAuthException) LoginResult.InvalidCredentials else LoginResult.Error(e)
    }

    /** Restaura la sesion guardada por Firebase al abrir la app (pantalla Splash). */
    suspend fun restore(): LoginResult? {
        val uid = currentUid ?: return null
        return try {
            loadProfile(uid)
        } catch (e: Exception) {
            LoginResult.Error(e)
        }
    }

    private suspend fun loadProfile(uid: String): LoginResult {
        val user = Store.fetchUser(uid)
        if (user == null || user.status == UserStatus.SUSPENDIDO) {
            auth.signOut()
            return LoginResult.Blocked
        }
        Store.start(user)
        return LoginResult.Success(user)
    }

    /**
     * Registro publico (RF-AUT-01): crea la cuenta en Authentication y su perfil en Firestore.
     * El comprador queda ACTIVO y el vendedor PENDIENTE de verificacion.
     * @return el usuario creado, o null si el correo ya esta registrado.
     */
    suspend fun register(name: String, email: String, password: String, role: Role, municipality: String): User? {
        val uid = try {
            auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!.uid
        } catch (_: FirebaseAuthUserCollisionException) {
            return null
        }
        val user = User(
            id = uid,
            name = name,
            email = email.trim().lowercase(),
            phone = "",
            role = role,
            status = if (role == Role.SELLER) UserStatus.PENDIENTE else UserStatus.ACTIVO,
            municipality = municipality
        )
        Store.saveUser(user)
        Store.start(user)
        return user
    }

    /**
     * Envia el correo de restablecimiento de Firebase. Los errores se ignoran a proposito para que
     * la respuesta sea identica exista o no la cuenta (RF-AUT-05).
     */
    suspend fun sendPasswordReset(email: String) {
        runCatching { auth.sendPasswordResetEmail(email.trim()).await() }
    }

    /**
     * Alta de usuario desde el rol Administrador (RF-ADU-02).
     *
     * Crear una cuenta con el SDK cliente inicia sesion con ella, lo que sacaria al administrador.
     * Por eso se usa una segunda instancia de FirebaseApp: la cuenta nueva se crea alli con una
     * contrasena aleatoria, se cierra esa sesion secundaria y se envia al usuario el correo para
     * que defina su propia contrasena.
     * @return el usuario creado, o null si el correo ya existe.
     */
    suspend fun createUserAsAdmin(context: Context, draft: User): User? {
        val primary = FirebaseApp.getInstance()
        val secondary = FirebaseApp.getApps(context).find { it.name == SECONDARY_APP }
            ?: FirebaseApp.initializeApp(context, primary.options, SECONDARY_APP)
        val secondaryAuth = FirebaseAuth.getInstance(secondary)
        val uid = try {
            secondaryAuth.createUserWithEmailAndPassword(draft.email, UUID.randomUUID().toString() + "Aa1").await().user!!.uid
        } catch (_: FirebaseAuthUserCollisionException) {
            return null
        } finally {
            secondaryAuth.signOut()
        }
        val user = draft.copy(id = uid, email = draft.email.lowercase())
        Store.saveUser(user)
        sendPasswordReset(user.email)
        return user
    }

    /** Verifica la contrasena actual antes de una accion sensible, como borrar la cuenta. */
    suspend fun reauthenticate(password: String): Boolean {
        val user = auth.currentUser ?: return false
        val email = user.email ?: return false
        return runCatching { user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await() }.isSuccess
    }

    /** Borra el perfil y la cuenta de Authentication del usuario actual (RF-PER-05). */
    suspend fun deleteOwnAccount(user: User) {
        Store.deleteUser(user)
        auth.currentUser?.delete()?.await()
        logout()
    }

    fun logout() {
        Store.stop()
        auth.signOut()
    }

    private const val SECONDARY_APP = "admin-user-creator"
}
