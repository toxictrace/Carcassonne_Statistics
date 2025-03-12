package by.toxic.carstat

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import by.toxic.carstat.databinding.FragmentSettingsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private lateinit var mainActivity: MainActivity

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainActivity = context as MainActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val themes = arrayOf("Light", "Dark", "Follow System")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.themeSpinner.adapter = adapter

        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val savedTheme = sharedPref.getString("theme", "Follow System")
        val themePosition = themes.indexOf(savedTheme)
        if (themePosition != -1) {
            binding.themeSpinner.setSelection(themePosition)
        }

        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTheme = themes[position]
                sharedPref.edit().putString("theme", selectedTheme).apply()
                mainActivity.applyTheme(selectedTheme)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val isBackgroundEnabled = sharedPref.getBoolean("background_enabled", false)
        binding.backgroundSwitch.isChecked = isBackgroundEnabled
        mainActivity.enableCustomBackgrounds(isBackgroundEnabled)

        binding.backgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("background_enabled", isChecked).apply()
            mainActivity.enableCustomBackgrounds(isChecked)
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        updateUI(account)

        binding.googleAccountText.setOnClickListener {
            mainActivity.signInWithGoogle()
        }
    }

    fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            updateUI(account)
        } catch (e: ApiException) {
            Log.w("SettingsFragment", "signInResult:failed code=" + e.statusCode)
            updateUI(null)
            Toast.makeText(context, getString(R.string.sign_in_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            binding.googleAccountText.visibility = View.GONE
            binding.googleAccountIcon.visibility = View.VISIBLE
            binding.googleAccountName.visibility = View.VISIBLE
            binding.googleAccountName.text = account.displayName
            binding.dataOptionsLayout.visibility = View.VISIBLE
        } else {
            binding.googleAccountText.visibility = View.VISIBLE
            binding.googleAccountIcon.visibility = View.GONE
            binding.googleAccountName.visibility = View.GONE
            binding.dataOptionsLayout.visibility = View.GONE
        }

        binding.saveDataButton.setOnClickListener {
            saveDataToGoogleDrive(account)
        }
        binding.loadDataButton.setOnClickListener {
            loadDataFromGoogleDrive(account)
        }
    }

    private fun saveDataToGoogleDrive(account: GoogleSignInAccount?) {
        if (account != null) {
            Toast.makeText(context, getString(R.string.saving_data), Toast.LENGTH_SHORT).show()
            Log.d("SettingsFragment", "Save data to Google Drive (API not configured yet)")
        } else {
            Toast.makeText(context, getString(R.string.sign_in_first), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDataFromGoogleDrive(account: GoogleSignInAccount?) {
        if (account != null) {
            Toast.makeText(context, getString(R.string.loading_data), Toast.LENGTH_SHORT).show()
            Log.d("SettingsFragment", "Load data from Google Drive (API not configured yet)")
        } else {
            Toast.makeText(context, getString(R.string.sign_in_first), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}