package by.toxic.carstat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import by.toxic.carstat.databinding.FragmentStatsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textView.text = getString(R.string.stats_title) // Обновлено для использования строки из strings.xml

        // Обработка "назад" с подтверждением выхода
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.exit_app_title)
                    .setMessage(R.string.exit_app_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        requireActivity().finish() // Завершаем приложение
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}