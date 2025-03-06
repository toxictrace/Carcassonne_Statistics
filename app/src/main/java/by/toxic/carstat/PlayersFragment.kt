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
import androidx.recyclerview.widget.LinearLayoutManager
import by.toxic.carstat.databinding.FragmentPlayersBinding
import by.toxic.carstat.db.Player
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayersFragment : Fragment() {

    private var _binding: FragmentPlayersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PlayerAdapter
    private val viewModel: GameViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PlayerAdapter(
            onEdit = { player ->
                val bundle = Bundle().apply { putInt("playerId", player.id) }
                findNavController().navigate(R.id.action_playersFragment_to_playerProfileFragment, bundle)
            },
            onDelete = { player -> showDeletePlayerDialog(player) }
        )
        binding.playersRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.playersRecyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allPlayers.collectLatest { players ->
                adapter.updatePlayers(players)
                println("DEBUG: Players list updated: ${players.size}")
            }
        }

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

    private fun showDeletePlayerDialog(player: Player) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_player_title)
            .setMessage(getString(R.string.delete_player_message, player.name))
            .setPositiveButton(R.string.yes) { dialogInterface, _ ->
                viewModel.deletePlayer(
                    requireContext(),
                    player.id,
                    onSuccess = {
                        dialogInterface.dismiss()
                        Toast.makeText(requireContext(), getString(R.string.player_deleted, player.name), Toast.LENGTH_SHORT).show()
                    },
                    onError = { errorMessage ->
                        dialogInterface.dismiss()
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton(R.string.no) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}