package co.edu.ucompensar.agrosabana.ui.common

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.Session
import co.edu.ucompensar.agrosabana.data.Store
import co.edu.ucompensar.agrosabana.data.User
import co.edu.ucompensar.agrosabana.data.UserStatus
import co.edu.ucompensar.agrosabana.databinding.FragmentProfileBinding
import co.edu.ucompensar.agrosabana.databinding.FragmentSellerProfileBinding
import co.edu.ucompensar.agrosabana.databinding.ViewProfileHeaderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar

private fun ViewProfileHeaderBinding.show(user: User, detail: String) {
    avatar.showAvatar(user)
    name.text = user.name
    this.detail.text = detail
}

/** Activar la huella exige que el dispositivo tenga una registrada (RF-PER-04). */
private fun MaterialSwitch.bindBiometric(session: Session, user: User) {
    isChecked = session.isBiometricEnabledFor(user)
    setOnCheckedChangeListener { view, checked ->
        when {
            !checked -> session.disableBiometric()
            Biometrics.isAvailable(context) -> {
                session.biometricUserId = user.id
                Snackbar.make(view, R.string.biometric_enabled, Snackbar.LENGTH_SHORT).show()
            }
            else -> {
                isChecked = false
                Snackbar.make(view, R.string.biometric_unavailable, Snackbar.LENGTH_LONG).show()
            }
        }
    }
}

/** Doble confirmacion y reautenticacion antes de borrar la cuenta (RF-PER-05, Ley 1581). */
private fun Fragment.confirmAccountDeletion(user: User) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.delete_account_title)
        .setMessage(R.string.delete_account_body)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.delete_action) { _, _ ->
            requireContext().promptText(
                R.string.delete_account_confirm_title,
                R.string.password,
                R.string.delete_action,
                password = true
            ) { password ->
                if (Store.authenticate(user.email, password) == null) {
                    Toast.makeText(requireContext(), R.string.login_error, Toast.LENGTH_LONG).show()
                } else {
                    val session = Session(requireContext())
                    if (session.isBiometricEnabledFor(user)) session.disableBiometric()
                    Store.deleteUser(user)
                    (activity as BaseActivity).logoutAndExit()
                }
            }
        }
        .show()
}

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val session = Session(requireContext())
        val user = session.currentUser ?: return
        val openEditor = View.OnClickListener { startActivity(Intent(requireContext(), EditProfileActivity::class.java)) }
        binding.dataCard.setOnClickListener(openEditor)
        binding.addressCard.setOnClickListener(openEditor)
        binding.biometricSwitch.bindBiometric(session, user)
        binding.notificationsSwitch.isChecked = session.notificationsEnabled
        binding.notificationsSwitch.setOnCheckedChangeListener { _, checked -> session.notificationsEnabled = checked }
        binding.logoutButton.setOnClickListener { (activity as BaseActivity).logoutAndExit() }
        binding.deleteButton.setOnClickListener { confirmAccountDeletion(user) }
    }

    override fun onResume() {
        super.onResume()
        val user = Session(requireContext()).currentUser ?: return
        binding.header.show(user, getString(R.string.role_city, getString(user.role.label), user.municipality))
        binding.addressText.text = user.address.ifEmpty { user.municipality }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

/** Perfil del vendedor con punto de despacho y radio de entrega (pantalla 18). */
class SellerProfileFragment : Fragment() {

    private var _binding: FragmentSellerProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSellerProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val session = Session(requireContext())
        val user = session.currentUser ?: return
        binding.radiusLayout.setText(user.deliveryRadiusKm.toString())
        binding.biometricSwitch.bindBiometric(session, user)
        binding.saveButton.setOnClickListener {
            val radius = binding.radiusLayout.text.toIntOrNull()
            if (radius == null || radius <= 0) {
                binding.radiusLayout.error = getString(R.string.error_required)
            } else {
                binding.radiusLayout.error = null
                user.deliveryRadiusKm = radius
                Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.editButton.setOnClickListener { startActivity(Intent(requireContext(), EditProfileActivity::class.java)) }
        binding.logoutButton.setOnClickListener { (activity as BaseActivity).logoutAndExit() }
        binding.deleteButton.setOnClickListener { confirmAccountDeletion(user) }
    }

    override fun onResume() {
        super.onResume()
        val user = Session(requireContext()).currentUser ?: return
        val state = if (user.status == UserStatus.ACTIVO) R.string.store_verified else R.string.store_pending
        binding.header.show(user, getString(state))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
