package co.edu.ucompensar.agrosabana.ui.common

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import co.edu.ucompensar.agrosabana.R
import co.edu.ucompensar.agrosabana.data.AuthRepository
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

/**
 * Interruptor de ingreso con huella (RF-PER-04). Activarlo pide la contrasena, la verifica con
 * Firebase (reautenticacion) y la cifra con una llave del Keystore protegida por la huella.
 */
private fun MaterialSwitch.bindBiometric(fragment: Fragment, user: User) {
    val context = fragment.requireContext()
    setOnCheckedChangeListener(null)
    isChecked = Biometrics.isEnabled(context) && Biometrics.enabledEmail(context).equals(user.email, ignoreCase = true)
    setOnCheckedChangeListener { view, checked ->
        when {
            !checked -> Biometrics.disable(context)
            !Biometrics.isAvailable(context) -> {
                isChecked = false
                Snackbar.make(view, R.string.biometric_unavailable, Snackbar.LENGTH_LONG).show()
            }
            else -> {
                isChecked = false
                context.promptText(R.string.biometric_confirm_password, R.string.password, R.string.biometric_activate, password = true) { password ->
                    fragment.launchTask {
                        if (!AuthRepository.reauthenticate(password)) {
                            Snackbar.make(view, R.string.login_error, Snackbar.LENGTH_LONG).show()
                            return@launchTask
                        }
                        Biometrics.enable(fragment.requireActivity(), user.email, password) { enabled ->
                            if (enabled) bindBiometric(fragment, user)
                            Snackbar.make(view, if (enabled) R.string.biometric_enabled else R.string.biometric_error, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Doble confirmacion y reautenticacion antes de borrar la cuenta (RF-PER-05, Ley 1581): se borra
 * el perfil de Firestore y la cuenta de Firebase Authentication.
 */
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
                launchTask {
                    if (!AuthRepository.reauthenticate(password)) {
                        Toast.makeText(requireContext(), R.string.login_error, Toast.LENGTH_LONG).show()
                        return@launchTask
                    }
                    Biometrics.disable(requireContext())
                    AuthRepository.deleteOwnAccount(user)
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
        Store.changes.observe(viewLifecycleOwner) { render() }
        val session = Session(requireContext())
        val user = session.currentUser ?: return
        val openEditor = View.OnClickListener { startActivity(Intent(requireContext(), EditProfileActivity::class.java)) }
        binding.dataCard.setOnClickListener(openEditor)
        binding.addressCard.setOnClickListener(openEditor)
        binding.biometricSwitch.bindBiometric(this, user)
        binding.notificationsSwitch.isChecked = session.notificationsEnabled
        binding.notificationsSwitch.setOnCheckedChangeListener { _, checked -> session.notificationsEnabled = checked }
        binding.logoutButton.setOnClickListener { (activity as BaseActivity).logoutAndExit() }
        binding.deleteButton.setOnClickListener { confirmAccountDeletion(user) }
    }

    /** Se llama al abrir y cada vez que el perfil cambia en Firestore. */
    private fun render() {
        if (_binding == null) return
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
        Store.changes.observe(viewLifecycleOwner) { render() }
        val session = Session(requireContext())
        val user = session.currentUser ?: return
        binding.radiusLayout.setText(user.deliveryRadiusKm.toString())
        binding.biometricSwitch.bindBiometric(this, user)
        binding.saveButton.setOnClickListener {
            val radius = binding.radiusLayout.text.toIntOrNull()
            if (radius == null || radius <= 0) {
                binding.radiusLayout.error = getString(R.string.error_required)
            } else {
                binding.radiusLayout.error = null
                val current = Store.currentUser ?: return@setOnClickListener
                launchTask {
                    Store.updateProfile(current.copy(deliveryRadiusKm = radius), null)
                    Snackbar.make(binding.root, R.string.saved, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        binding.editButton.setOnClickListener { startActivity(Intent(requireContext(), EditProfileActivity::class.java)) }
        binding.logoutButton.setOnClickListener { (activity as BaseActivity).logoutAndExit() }
        binding.deleteButton.setOnClickListener { confirmAccountDeletion(user) }
    }

    /** Se llama al abrir y cada vez que el perfil cambia en Firestore. */
    private fun render() {
        if (_binding == null) return
        val user = Session(requireContext()).currentUser ?: return
        val state = if (user.status == UserStatus.ACTIVO) R.string.store_verified else R.string.store_pending
        binding.header.show(user, getString(state))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
