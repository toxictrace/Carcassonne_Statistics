package by.toxic.carstat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import by.toxic.carstat.databinding.FragmentViewGameBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ViewGameFragment : Fragment() {

    private var _binding: FragmentViewGameBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameViewModel by viewModels()
    private lateinit var adapter: ViewGamePlayerAdapter
    private var gameId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gameId = arguments?.getInt("gameId", -1) ?: -1
        if (gameId == -1) {
            findNavController().popBackStack()
            return
        }

        adapter = ViewGamePlayerAdapter()
        binding.playersRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.playersRecyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allGames.collectLatest { games ->
                val game = games.find { it.game.id == gameId }
                if (game != null) {
                    binding.dateLabel.text = viewModel.formatDateForDisplay(game.game.date)
                    viewModel.allPlayers.collectLatest { players ->
                        val playerData = game.gamePlayers.map { gp ->
                            val player = players.find { it.id == gp.playerId }
                            "${player?.name ?: "Unknown"} - ${gp.score}"
                        }
                        adapter.updatePlayers(playerData)
                    }
                } else {
                    findNavController().popBackStack()
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack(R.id.gamesFragment, false)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ViewGamePlayerAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ViewGamePlayerAdapter.ViewHolder>() {
    private var players: List<String> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(players[position])
    }

    override fun getItemCount(): Int = players.size

    fun updatePlayers(newPlayers: List<String>) {
        players = newPlayers
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        fun bind(playerData: String) {
            itemView.findViewById<android.widget.TextView>(android.R.id.text1).text = playerData
        }
    }
}