package by.toxic.carstat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import by.toxic.carstat.databinding.FragmentGamesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GamesFragment : Fragment() {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: GameAdapter
    private val viewModel: GameViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = GameAdapter(
            fragment = this,
            viewModel = viewModel,
            onEdit = { gameWithPlayers ->
                val bundle = Bundle().apply {
                    putInt("gameId", gameWithPlayers.game.id)
                }
                findNavController().navigate(R.id.action_gamesFragment_to_viewGameFragment, bundle)
            },
            onDelete = { gameId ->
                viewModel.deleteGame(gameId)
            }
        )
        binding.gamesRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.gamesRecyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allGames.collectLatest { games ->
                adapter.updateGames(games)
                adapter.notifyDataSetChanged() // Явное обновление для надёжности
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}