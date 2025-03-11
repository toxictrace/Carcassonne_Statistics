package by.toxic.carstat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import by.toxic.carstat.databinding.FragmentEditPlayerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

class EditPlayerFragment : Fragment() {

    private var _binding: FragmentEditPlayerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameViewModel by viewModels()
    private var playerId: Int = -1
    private var originalName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerId = arguments?.getInt("playerId", -1) ?: -1

        // Загрузка существующего игрока для редактирования
        if (playerId != -1) {
            viewLifecycleOwner.lifecycleScope.launch {
                val players = viewModel.allPlayers.first()
                val player = players.find { it.id == playerId }
                if (player != null) {
                    binding.nameInput.setText(player.name)
                    originalName = player.name
                    println("DEBUG: Loaded player name for editing: ${player.name}")
                } else {
                    println("DEBUG: Player with ID $playerId not found")
                    findNavController().popBackStack()
                }
            }
        } else {
            binding.nameInput.setText("")
            println("DEBUG: Creating new player")
        }

        // Настройка валидации в реальном времени
        binding.nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                validateInput()
            }
        })

        // Кнопка "Подтверждение"
        binding.confirmButton.setOnClickListener {
            val newName = binding.nameInput.text.toString().trim()
            viewLifecycleOwner.lifecycleScope.launch {
                if (isInputValid(newName)) {
                    try {
                        if (playerId == -1) {
                            // Добавление нового игрока с случайным frameId
                            val frameId = Random.nextInt(1, 4) // Случайный номер: 1, 2 или 3
                            viewModel.addPlayer(
                                requireContext(),
                                newName,
                                frameId,
                                onSuccess = {
                                    findNavController().popBackStack()
                                },
                                onError = { error ->
                                    binding.errorMessage.text = error
                                    binding.errorMessage.visibility = View.VISIBLE
                                }
                            )
                        } else {
                            // Обновление существующего игрока
                            viewModel.updatePlayerName(playerId, newName)
                            val bundle = Bundle().apply { putString("updatedName", newName) }
                            findNavController().previousBackStackEntry?.savedStateHandle?.set("playerResult", bundle)
                            findNavController().popBackStack()
                        }
                    } catch (e: Exception) {
                        binding.errorMessage.text = getString(R.string.edit_player_error, e.message)
                        binding.errorMessage.visibility = View.VISIBLE
                        println("DEBUG: Failed to update/add player: ${e.message}")
                    }
                }
            }
        }

        // Кнопка "Отмена"
        binding.cancelButton.setOnClickListener {
            findNavController().popBackStack()
        }

        // Обработка "Назад"
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.confirm_exit_title)
                    .setMessage(R.string.confirm_exit_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        findNavController().popBackStack()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
        })
    }

    private fun validateInput() {
        val name = binding.nameInput.text.toString().trim()
        binding.errorMessage.visibility = View.GONE
        binding.confirmButton.isEnabled = true

        if (name.isEmpty()) {
            binding.errorMessage.text = getString(R.string.empty_name_error)
            binding.errorMessage.visibility = View.VISIBLE
            binding.confirmButton.isEnabled = false
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val doesExist = viewModel.doesPlayerExist(name)
            if (doesExist && name != originalName) {
                binding.errorMessage.text = getString(R.string.player_exists_error, name)
                binding.errorMessage.visibility = View.VISIBLE
                binding.confirmButton.isEnabled = false
            }
        }
    }

    private suspend fun isInputValid(name: String): Boolean {
        if (name.isEmpty()) {
            binding.errorMessage.text = getString(R.string.empty_name_error)
            binding.errorMessage.visibility = View.VISIBLE
            return false
        }

        val doesExist = viewModel.doesPlayerExist(name)
        if (doesExist && name != originalName) {
            binding.errorMessage.text = getString(R.string.player_exists_error, name)
            binding.errorMessage.visibility = View.VISIBLE
            return false
        }

        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}