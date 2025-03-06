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
import by.toxic.carstat.databinding.FragmentPlayerProfileBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PlayerProfileFragment : Fragment() {

    private var _binding: FragmentPlayerProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameViewModel by viewModels()
    private var playerId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerId = arguments?.getInt("playerId", -1) ?: -1
        if (playerId == -1) {
            findNavController().popBackStack(R.id.playersFragment, false)
            return
        }

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Bundle>("playerResult")?.observe(viewLifecycleOwner) { result ->
            val updatedName = result.getString("updatedName")
            if (updatedName != null) {
                binding.playerName.text = updatedName
                println("DEBUG: Player name updated from EditPlayerFragment: $updatedName")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allPlayers.collectLatest { players ->
                val player = players.find { it.id == playerId }
                if (player != null) {
                    binding.playerName.text = player.name
                    println("DEBUG: Player loaded: ${player.name}")

                    binding.playerName.setOnClickListener {
                        val bundle = Bundle().apply { putInt("playerId", playerId) }
                        findNavController().navigate(R.id.action_playerProfileFragment_to_editPlayerFragment, bundle)
                    }

                    viewModel.allGames.collectLatest { games ->
                        println("DEBUG: Games loaded: ${games.size}")
                        val playerGames = games.mapNotNull { game ->
                            game.gamePlayers.find { it.playerId == playerId }?.let { gp ->
                                Pair(game.game.date, gp.score)
                            }
                        }
                        println("DEBUG: Player games: ${playerGames.size}")

                        val gameCount = playerGames.size
                        val maxScore = playerGames.maxOfOrNull { it.second } ?: 0
                        val totalScore = playerGames.sumOf { it.second }
                        val avgScore = if (gameCount > 0) (totalScore.toDouble() / gameCount).roundToInt() else 0

                        val placeCounts = mutableMapOf<Int, Int>()
                        games.forEach { game ->
                            val sortedPlayers = game.gamePlayers.sortedByDescending { it.score }
                            sortedPlayers.forEachIndexed { index, gp ->
                                if (gp.playerId == playerId) {
                                    placeCounts[index + 1] = placeCounts.getOrDefault(index + 1, 0) + 1
                                }
                            }
                        }

                        binding.gamesCount.text = getString(R.string.games_count, gameCount)
                        binding.maxScore.text = getString(R.string.max_score, maxScore)
                        binding.totalScore.text = getString(R.string.total_score, totalScore)
                        binding.avgScore.text = getString(R.string.avg_score, avgScore)
                        binding.firstPlace.text = getString(R.string.first_place, placeCounts.getOrDefault(1, 0))
                        binding.secondPlace.text = getString(R.string.second_place, placeCounts.getOrDefault(2, 0))
                        binding.thirdPlace.text = getString(R.string.third_place, placeCounts.getOrDefault(3, 0))

                        val maxPlace = placeCounts.keys.maxOrNull() ?: 3
                        binding.fourthPlace.visibility = if (maxPlace >= 4) View.VISIBLE else View.GONE
                        binding.fifthPlace.visibility = if (maxPlace >= 5) View.VISIBLE else View.GONE
                        binding.sixthPlace.visibility = if (maxPlace >= 6) View.VISIBLE else View.GONE

                        if (maxPlace >= 4) binding.fourthPlace.text = getString(R.string.fourth_place, placeCounts.getOrDefault(4, 0))
                        if (maxPlace >= 5) binding.fifthPlace.text = getString(R.string.fifth_place, placeCounts.getOrDefault(5, 0))
                        if (maxPlace >= 6) binding.sixthPlace.text = getString(R.string.sixth_place, placeCounts.getOrDefault(6, 0))
                    }
                } else {
                    println("DEBUG: Player with ID $playerId not found")
                    findNavController().popBackStack(R.id.playersFragment, false)
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack(R.id.playersFragment, false)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}