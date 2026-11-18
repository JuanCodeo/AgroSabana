package co.edu.ucompensar.agrosabana.ui.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.AuthRepository
import co.edu.ucompensar.agrosabana.data.Product
import co.edu.ucompensar.agrosabana.data.ProductStatus
import co.edu.ucompensar.agrosabana.data.Role
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.data.UserStatus
import co.edu.ucompensar.agrosabana.databinding.ActivityProductReviewBinding
import co.edu.ucompensar.agrosabana.databinding.ActivityUserFormBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
import co.edu.ucompensar.agrosabana.ui.common.launchTask
import co.edu.ucompensar.agrosabana.ui.common.requireEmail
import co.edu.ucompensar.agrosabana.ui.common.requireValue
import co.edu.ucompensar.agrosabana.ui.common.setOptions
import co.edu.ucompensar.agrosabana.ui.common.setText
import co.edu.ucompensar.agrosabana.ui.common.showProductImage
import co.edu.ucompensar.agrosabana.ui.common.text
import co.edu.ucompensar.agrosabana.ui.common.tintBadge
import co.edu.ucompensar.agrosabana.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * CRUD de usuarios desde el rol Administrador (RF-ADU-02 a 04, RF-USR-01 a 04), con persistencia
 * en Firestore: crear (cuenta en Firebase Authentication + perfil), editar, aprobar vendedores,
 * suspender, reactivar y eliminar. Toda accion queda en la bitacora de auditoria.
 */
class UserFormActivity : BaseActivity() {

    private lateinit var binding: ActivityUserFormBinding
    private var userId: String? = null
    private val roleNames by lazy { Role.entries.map { getString(it.label) } }

    /** Version vigente del usuario, siempre leida del Store (que sigue a Firestore). */
    private val user: User? get() = userId?.let(Store::user)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra(EXTRA_ID)
        setupBackToolbar(binding.toolbar, if (userId == null) R.string.new_user else R.string.user_detail)

        binding.role.setOptions(roleNames)
        binding.role.setText(roleNames.first(), false)
        user?.let { existing ->
            binding.nameLayout.setText(existing.name)
            binding.emailLayout.setText(existing.email)
            // El correo es el usuario de Firebase Authentication: no se cambia desde aqui.
            binding.emailLayout.isEnabled = false
            binding.phoneLayout.setText(existing.phone)
            binding.municipalityLayout.setText(existing.municipality)
            binding.role.setText(getString(existing.role.label), false)
        }

        binding.saveButton.setOnClickListener { save() }
        binding.approveButton.setOnClickListener {
            changeStatus(UserStatus.ACTIVO, R.string.audit_action_approve)
        }
        binding.rejectButton.setOnClickListener {
            changeStatus(UserStatus.SUSPENDIDO, R.string.audit_action_reject)
        }
        binding.suspendButton.setOnClickListener {
            if (user?.status == UserStatus.SUSPENDIDO) changeStatus(UserStatus.ACTIVO, R.string.audit_action_reactivate)
            else changeStatus(UserStatus.SUSPENDIDO, R.string.audit_action_suspend)
        }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        Store.changes.observe(this) { render() }
    }

    private fun render() {
        val existing = user
        binding.statusText.isVisible = existing != null
        binding.reasonLayout.isVisible = existing != null
        binding.deleteButton.isVisible = existing != null
        binding.suspendButton.isVisible = existing != null && existing.status != UserStatus.PENDIENTE
        val pending = existing?.status == UserStatus.PENDIENTE
        binding.documentCard.isVisible = pending
        binding.approveButton.isVisible = pending
        binding.rejectButton.isVisible = pending
        if (existing != null) {
            binding.statusText.text = getString(R.string.user_status, getString(existing.status.label))
            binding.suspendButton.setText(
                if (existing.status == UserStatus.SUSPENDIDO) R.string.reactivate else R.string.suspend
            )
        }
    }

    private fun selectedRole() = Role.entries[roleNames.indexOf(binding.role.text.toString()).coerceAtLeast(0)]

    private fun setBusy(busy: Boolean) {
        listOf(binding.saveButton, binding.deleteButton, binding.suspendButton, binding.approveButton, binding.rejectButton)
            .forEach { it.isEnabled = !busy }
    }

    private fun save() {
        val valid = listOf(
            binding.nameLayout.requireValue(),
            binding.emailLayout.requireEmail(),
            binding.municipalityLayout.requireValue()
        ).all { it }
        if (!valid) return

        val existing = user
        val admin = session.currentUser?.name.orEmpty()
        if (existing == null) {
            val role = selectedRole()
            val draft = User(
                id = "",
                name = binding.nameLayout.text,
                email = binding.emailLayout.text,
                phone = binding.phoneLayout.text,
                role = role,
                status = if (role == Role.SELLER) UserStatus.PENDIENTE else UserStatus.ACTIVO,
                municipality = binding.municipalityLayout.text
            )
            setBusy(true)
            launchTask(onError = { setBusy(false) }) {
                val created = AuthRepository.createUserAsAdmin(this, draft)
                if (created == null) {
                    setBusy(false)
                    binding.emailLayout.error = getString(R.string.error_email_taken)
                    return@launchTask
                }
                Store.audit(admin, getString(R.string.audit_simple, getString(R.string.audit_action_create), created.name, getString(created.role.label)))
                Toast.makeText(this, R.string.user_created_email, Toast.LENGTH_LONG).show()
                finish()
            }
            return
        }

        // RF-ADU-04: un administrador no se retira su propio rol ni deja el sistema sin administradores.
        val newRole = selectedRole()
        if (newRole != existing.role && existing.role == Role.ADMIN && (existing.id == session.currentUserId || Store.isLastAdmin(existing))) {
            Snackbar.make(binding.root, R.string.error_last_admin, Snackbar.LENGTH_LONG).show()
            return
        }
        val updated = existing.copy(
            name = binding.nameLayout.text,
            phone = binding.phoneLayout.text,
            municipality = binding.municipalityLayout.text,
            role = newRole
        )
        setBusy(true)
        launchTask(onError = { setBusy(false) }) {
            Store.saveUser(updated)
            Store.audit(admin, getString(R.string.audit_simple, getString(R.string.audit_action_edit), updated.name, getString(newRole.label)))
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** @return el motivo, o null si falta (toda accion administrativa queda auditada). */
    private fun requireReason(): String? =
        if (binding.reasonLayout.requireValue()) binding.reasonLayout.text
        else {
            binding.reasonLayout.error = getString(R.string.error_reason)
            null
        }

    private fun isBlockedOnSelf(existing: User): Boolean {
        if (existing.id == session.currentUserId) {
            Snackbar.make(binding.root, R.string.error_self_action, Snackbar.LENGTH_LONG).show()
            return true
        }
        return false
    }

    private fun changeStatus(status: UserStatus, auditAction: Int) {
        val existing = user ?: return
        if (isBlockedOnSelf(existing)) return
        val reason = requireReason() ?: return
        setBusy(true)
        launchTask(onError = { setBusy(false) }) {
            // Suspender oculta sus productos (RF-ADU-03).
            Store.setUserStatus(existing, status)
            audit(existing.name, auditAction, reason)
            binding.reasonLayout.setText("")
            setBusy(false)
            Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete() {
        val existing = user ?: return
        if (isBlockedOnSelf(existing)) return
        if (Store.isLastAdmin(existing)) {
            Snackbar.make(binding.root, R.string.error_last_admin, Snackbar.LENGTH_LONG).show()
            return
        }
        val reason = requireReason() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_user)
            .setMessage(getString(R.string.delete_user_body, existing.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                setBusy(true)
                launchTask(onError = { setBusy(false) }) {
                    audit(existing.name, R.string.audit_action_delete, reason)
                    Store.deleteUser(existing)
                    Toast.makeText(this, R.string.user_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .show()
    }

    private suspend fun audit(target: String, action: Int, reason: String) {
        Store.audit(session.currentUser?.name.orEmpty(), getString(R.string.audit_user, getString(action), target, reason))
    }

    companion object {
        private const val EXTRA_ID = "user_id"

        fun intentFor(context: Context, userId: String) =
            Intent(context, UserFormActivity::class.java).putExtra(EXTRA_ID, userId)
    }
}

/** Revision de una publicacion: toda decision exige motivo y queda auditada (RF-ADP-01). */
class ProductReviewActivity : BaseActivity() {

    private lateinit var binding: ActivityProductReviewBinding
    private lateinit var productId: String
    private val product: Product? get() = Store.product(productId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        binding = ActivityProductReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.review_product)

        productId = intent.getStringExtra(EXTRA_ID).orEmpty()
        val initial = product ?: return finish()
        binding.image.showProductImage(initial.photoUrl, initial.category.illustration, initial.category.background)
        binding.name.text = initial.name
        binding.line.text = getString(R.string.review_line, initial.sellerName, Money.format(initial.priceCents), initial.stock)
        binding.description.text = initial.description

        binding.approveButton.setOnClickListener { decide(ProductStatus.APROBADO, R.string.audit_action_approve_product) }
        binding.rejectButton.setOnClickListener { decide(ProductStatus.RECHAZADO, R.string.audit_action_reject_product) }
        binding.hideButton.setOnClickListener { decide(ProductStatus.OCULTO, R.string.audit_action_hide_product) }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        Store.changes.observe(this) { render() }
    }

    private fun render() {
        val current = product ?: return
        binding.statusBadge.setText(current.status.label)
        binding.statusBadge.tintBadge(current.status.color)
        binding.approveButton.isVisible = current.status != ProductStatus.APROBADO
        binding.rejectButton.isVisible = current.status != ProductStatus.RECHAZADO
        binding.hideButton.isVisible = current.status != ProductStatus.OCULTO
    }

    private fun requireReason(): Boolean {
        if (binding.reasonLayout.requireValue()) return true
        binding.reasonLayout.error = getString(R.string.error_reason)
        return false
    }

    private fun decide(status: ProductStatus, auditAction: Int) {
        val current = product ?: return
        if (!requireReason()) return
        launchTask {
            Store.setProductStatus(current, status)
            audit(current, auditAction)
            binding.reasonLayout.setText("")
            Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete() {
        val current = product ?: return
        if (!requireReason()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_product)
            .setMessage(getString(R.string.delete_product_body, current.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                launchTask {
                    audit(current, R.string.audit_action_delete_product)
                    Store.deleteProduct(current)
                    Toast.makeText(this, R.string.product_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .show()
    }

    private suspend fun audit(target: Product, action: Int) {
        Store.audit(
            session.currentUser?.name.orEmpty(),
            getString(R.string.audit_user, getString(action), target.name, binding.reasonLayout.text)
        )
    }

    companion object {
        private const val EXTRA_ID = "product_id"

        fun intentFor(context: Context, productId: String) =
            Intent(context, ProductReviewActivity::class.java).putExtra(EXTRA_ID, productId)
    }
}
