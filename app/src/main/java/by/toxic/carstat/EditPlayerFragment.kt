package by.toxic.carstat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import by.toxic.carstat.databinding.FragmentEditPlayerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EditPlayerFragment : Fragment() {

    private var _binding: FragmentEditPlayerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameViewModel by viewModels()
    private var playerId: Int = -1

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

        if (playerId != -1) {
            viewLifecycleOwner.lifecycleScope.launch {
                val player = viewModel.allPlayers.first().find { it.id == playerId }
                if (player != null) {
                    binding.nameInput.setText(player.name)
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

        binding.saveButton.setOnClickListener {
            val newName = binding.nameInput.text.toString().trim()
            if (newName.isNotBlank()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        if (playerId == -1) {
                            if (viewModel.doesPlayerExist(newName)) {
                                Toast.makeText(requireContext(), getString(R.string.player_exists_error, newName), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addPlayer(
                                    requireContext(),
                                    newName,
                                    onSuccess = {
                                        findNavController().popBackStack()
                                    },
                                    onError = { error ->
                                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        } else {
                            viewModel.updatePlayerName(playerId, newName)
                            val bundle = Bundle().apply { putString("updatedName", newName) }
                            findNavController().previousBackStackEntry?.savedStateHandle?.set("playerResult", bundle)
                            findNavController().popBackStack()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), getString(R.string.edit_player_error, e.message), Toast.LENGTH_SHORT).show()
                        println("DEBUG: Failed to update/add player: ${e.message}")
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Player name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}