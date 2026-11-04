package co.edu.ucompensar.agrosabana.ui.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Product
import co.edu.ucompensar.agrosabana.data.ProductStatus
import co.edu.ucompensar.agrosabana.data.Role
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.data.UserStatus
import co.edu.ucompensar.agrosabana.databinding.ActivityProductReviewBinding
import co.edu.ucompensar.agrosabana.databinding.ActivityUserFormBinding
import co.edu.ucompensar.agrosabana.ui.common.BaseActivity
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

/** Crear, editar, aprobar, suspender y eliminar usuarios (RF-ADU-02 a 04, RF-USR-01 a 04). */
class UserFormActivity : BaseActivity() {

    private lateinit var binding: ActivityUserFormBinding
    private var user: User? = null
    private val roleNames by lazy { Role.entries.map { getString(it.label) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        user = Store.user(intent.getIntExtra(EXTRA_ID, NO_ID))
        setupBackToolbar(binding.toolbar, if (user == null) R.string.new_user else R.string.user_detail)

        binding.role.setOptions(roleNames)
        binding.role.setText(roleNames.first(), false)
        user?.let { existing ->
            binding.nameLayout.setText(existing.name)
            binding.emailLayout.setText(existing.email)
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
        render()
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

    private fun save() {
        val valid = listOf(
            binding.nameLayout.requireValue(),
            binding.emailLayout.requireEmail(),
            binding.municipalityLayout.requireValue()
        ).all { it }
        if (!valid) return

        val email = binding.emailLayout.text
        if (Store.emailExists(email, user?.id)) {
            binding.emailLayout.error = getString(R.string.error_email_taken)
            return
        }

        val existing = user
        if (existing == null) {
            val role = selectedRole()
            val status = if (role == Role.SELLER) UserStatus.PENDIENTE else UserStatus.ACTIVO
            Store.addUser(binding.nameLayout.text, email, binding.phoneLayout.text, role, binding.municipalityLayout.text, status)
            Toast.makeText(this, R.string.user_created, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // RF-ADU-04: un administrador no se retira su propio rol ni deja el sistema sin administradores.
        val newRole = selectedRole()
        if (newRole != existing.role && existing.role == Role.ADMIN && (existing.id == session.currentUserId || Store.isLastAdmin(existing))) {
            Snackbar.make(binding.root, R.string.error_last_admin, Snackbar.LENGTH_LONG).show()
            return
        }
        existing.name = binding.nameLayout.text
        existing.email = email
        existing.phone = binding.phoneLayout.text
        existing.municipality = binding.municipalityLayout.text
        existing.role = newRole
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
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
        existing.status = status
        // Suspender oculta sus productos (RF-ADU-03).
        if (status == UserStatus.SUSPENDIDO) {
            Store.productsOf(existing.id).forEach { it.status = ProductStatus.OCULTO }
        }
        audit(existing.name, auditAction, reason)
        binding.reasonLayout.setText("")
        render()
        Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
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
                audit(existing.name, R.string.audit_action_delete, reason)
                Store.deleteUser(existing)
                Toast.makeText(this, R.string.user_deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    private fun audit(target: String, action: Int, reason: String) {
        Store.audit(session.currentUser?.name.orEmpty(), getString(R.string.audit_user, getString(action), target, reason))
    }

    companion object {
        private const val EXTRA_ID = "user_id"
        private const val NO_ID = -1

        fun intentFor(context: Context, userId: Int) =
            Intent(context, UserFormActivity::class.java).putExtra(EXTRA_ID, userId)
    }
}

/** Revision de una publicacion: toda decision exige motivo y queda auditada (RF-ADP-01). */
class ProductReviewActivity : BaseActivity() {

    private lateinit var binding: ActivityProductReviewBinding
    private lateinit var product: Product

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackToolbar(binding.toolbar, R.string.review_product)

        product = Store.product(intent.getIntExtra(EXTRA_ID, -1)) ?: run { finish(); return }
        binding.image.showProductImage(product.photo, product.category.illustration, product.category.background)
        binding.name.text = product.name
        binding.line.text = getString(R.string.review_line, product.sellerName, Money.format(product.priceCents), product.stock)
        binding.description.text = product.description

        binding.approveButton.setOnClickListener { decide(ProductStatus.APROBADO, R.string.audit_action_approve_product) }
        binding.rejectButton.setOnClickListener { decide(ProductStatus.RECHAZADO, R.string.audit_action_reject_product) }
        binding.hideButton.setOnClickListener { decide(ProductStatus.OCULTO, R.string.audit_action_hide_product) }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        render()
    }

    private fun render() {
        binding.statusBadge.setText(product.status.label)
        binding.statusBadge.tintBadge(product.status.color)
        binding.approveButton.isVisible = product.status != ProductStatus.APROBADO
        binding.rejectButton.isVisible = product.status != ProductStatus.RECHAZADO
        binding.hideButton.isVisible = product.status != ProductStatus.OCULTO
    }

    private fun decide(status: ProductStatus, auditAction: Int) {
        if (!binding.reasonLayout.requireValue()) {
            binding.reasonLayout.error = getString(R.string.error_reason)
            return
        }
        product.status = status
        audit(auditAction)
        binding.reasonLayout.setText("")
        render()
        Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmDelete() {
        if (!binding.reasonLayout.requireValue()) {
            binding.reasonLayout.error = getString(R.string.error_reason)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_product)
            .setMessage(getString(R.string.delete_product_body, product.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                audit(R.string.audit_action_delete_product)
                Store.deleteProduct(product)
                Toast.makeText(this, R.string.product_deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    private fun audit(action: Int) {
        Store.audit(
            session.currentUser?.name.orEmpty(),
            getString(R.string.audit_user, getString(action), product.name, binding.reasonLayout.text)
        )
    }

    companion object {
        private const val EXTRA_ID = "product_id"

        fun intentFor(context: Context, productId: Int) =
            Intent(context, ProductReviewActivity::class.java).putExtra(EXTRA_ID, productId)
    }
}
