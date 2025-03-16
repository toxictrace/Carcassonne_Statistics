package by.toxic.carstat

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import by.toxic.carstat.databinding.FragmentStatisticsBinding
import by.toxic.carstat.db.GameWithPlayers
import by.toxic.carstat.db.Player
import kotlinx.coroutines.launch

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null")
    private val viewModel: GameViewModel by activityViewModels()
    private var players = listOf<Player>()
    private var games = listOf<GameWithPlayers>()
    private var selectedPlayers = mutableListOf<Player?>()
    private lateinit var player1Adapter: ArrayAdapter<String>
    private lateinit var player2Adapter: ArrayAdapter<String>
    private lateinit var player3Adapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedPlayers = mutableListOf(null, null, null)
        setupSpinners()
        observeData()
        setupCompareButton()
    }

    private fun setupSpinners() {
        player1Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mutableListOf(getString(R.string.select_player)))
        player2Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mutableListOf(getString(R.string.select_player)))
        player3Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mutableListOf(getString(R.string.select_player)))

        player1Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        player2Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        player3Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.player1Spinner.adapter = player1Adapter
        binding.player2Spinner.adapter = player2Adapter
        binding.player3Spinner.adapter = player3Adapter

        binding.player1Spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedName = player1Adapter.getItem(position)
                selectedPlayers[0] = if (selectedName != getString(R.string.select_player)) players.find { it.name == selectedName } else null
                updateSpinners()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.player2Spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedName = player2Adapter.getItem(position)
                selectedPlayers[1] = if (selectedName != getString(R.string.select_player)) players.find { it.name == selectedName } else null
                updateSpinners()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.player3Spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedName = player3Adapter.getItem(position)
                selectedPlayers[2] = if (selectedName != getString(R.string.select_player)) players.find { it.name == selectedName } else null
                updateSpinners()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateSpinners() {
        val selectedIds = selectedPlayers.filterNotNull().map { it.id }
        val availablePlayers = players.filter { !selectedIds.contains(it.id) }.map { it.name }

        player1Adapter.clear()
        player1Adapter.add(getString(R.string.select_player))
        player1Adapter.addAll(availablePlayers + (selectedPlayers[0]?.name ?: ""))
        player1Adapter.remove("")
        val player1Selection = selectedPlayers[0]?.name?.let { player1Adapter.getPosition(it) } ?: 0
        binding.player1Spinner.setSelection(player1Selection, false)

        player2Adapter.clear()
        player2Adapter.add(getString(R.string.select_player))
        player2Adapter.addAll(availablePlayers + (selectedPlayers[1]?.name ?: ""))
        player2Adapter.remove("")
        val player2Selection = selectedPlayers[1]?.name?.let { player2Adapter.getPosition(it) } ?: 0
        binding.player2Spinner.setSelection(player2Selection, false)

        player3Adapter.clear()
        player3Adapter.add(getString(R.string.select_player))
        player3Adapter.addAll(availablePlayers + (selectedPlayers[2]?.name ?: ""))
        player3Adapter.remove("")
        val player3Selection = selectedPlayers[2]?.name?.let { player3Adapter.getPosition(it) } ?: 0
        binding.player3Spinner.setSelection(player3Selection, false)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allPlayers.collect { playerList ->
                players = playerList
                updateSpinners()
                if (view != null) { // Проверяем, активно ли представление
                    updateGlobalStats()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allGames.collect { gameList ->
                games = gameList
                if (view != null) { // Проверяем, активно ли представление
                    updateGlobalStats()
                }
            }
        }
    }

    private fun updateGlobalStats() {
        _binding?.let { binding -> // Безопасный доступ к binding
            val totalGames = games.size
            val allScores = games.flatMap { it.gamePlayers.map { player -> player.score } }
            val avgScore = if (allScores.isNotEmpty()) allScores.average().toInt() else 0
            val maxScore = allScores.maxOrNull() ?: 0
            val minScore = allScores.minOrNull() ?: 0

            val playerWins = mutableMapOf<Int, Int>()
            games.forEach { game ->
                val winner = game.gamePlayers.maxByOrNull { it.score }
                winner?.playerId?.let { playerWins[it] = playerWins.getOrDefault(it, 0) + 1 }
            }
            val topPlayers = playerWins.entries.sortedByDescending { it.value }
                .take(3)
                .joinToString("\n") { entry ->
                    val player = players.find { it.id == entry.key }
                    "${player?.name ?: "Unknown (ID: ${entry.key})"}: ${entry.value}"
                }

            binding.totalGames.text = getString(R.string.total_games_stat, totalGames)
            binding.avgScore.text = getString(R.string.avg_score_stat, avgScore)
            binding.maxScore.text = getString(R.string.max_score_stat, maxScore)
            binding.minScore.text = getString(R.string.min_score_stat, minScore)
            binding.topPlayers.text = getString(R.string.top_players_stat, topPlayers)
        } ?: run {
            android.util.Log.w("StatisticsFragment", "Cannot update stats: binding is null")
        }
    }

    private fun setupCompareButton() {
        binding.compareButton.setOnClickListener {
            val validPlayers = selectedPlayers.filterNotNull()
            if (validPlayers.isEmpty()) {
                (activity as? MainActivity)?.showToast(getString(R.string.no_players_selected))
                return@setOnClickListener
            }
            updateComparisonTable(validPlayers)
        }
    }

    private fun updateComparisonTable(selectedPlayers: List<Player>) {
        _binding?.let { binding -> // Безопасный доступ к binding
            val playerStats = selectedPlayers.map { player ->
                val playerGames = games.filter { game -> game.gamePlayers.any { it.playerId == player.id } }
                val wins = playerGames.count { game -> game.gamePlayers.maxByOrNull { it.score }?.playerId == player.id }
                val scores = playerGames.flatMap { it.gamePlayers.filter { it.playerId == player.id }.map { it.score } }
                val avgScore = if (scores.isNotEmpty()) scores.average().toFloat() else 0f
                val totalGames = playerGames.size
                PlayerStats(player.name, wins, avgScore, totalGames)
            }

            binding.comparisonTable.removeAllViews()

            val headerRow = TableRow(context).apply {
                addView(TextView(context).apply { text = "" })
                playerStats.forEach { stats ->
                    addView(TextView(context).apply {
                        text = stats.name
                        setPadding(8, 8, 8, 8)
                        textSize = 16f
                        setTextColor(Color.BLACK)
                    })
                }
            }
            binding.comparisonTable.addView(headerRow)

            val winsRow = TableRow(context).apply {
                addView(TextView(context).apply {
                    text = getString(R.string.wins_header)
                    setPadding(8, 8, 8, 8)
                    textSize = 14f
                })
                playerStats.forEach { stats ->
                    addView(TextView(context).apply {
                        text = stats.wins.toString()
                        setPadding(8, 8, 8, 8)
                        textSize = 14f
                        setTextColor(getColorForValue(stats.wins, playerStats.map { it.wins }))
                    })
                }
            }
            binding.comparisonTable.addView(winsRow)

            val avgScoreRow = TableRow(context).apply {
                addView(TextView(context).apply {
                    text = getString(R.string.avg_score_header)
                    setPadding(8, 8, 8, 8)
                    textSize = 14f
                })
                playerStats.forEach { stats ->
                    addView(TextView(context).apply {
                        text = String.format("%.1f", stats.avgScore)
                        setPadding(8, 8, 8, 8)
                        textSize = 14f
                        setTextColor(getColorForValue(stats.avgScore, playerStats.map { it.avgScore }))
                    })
                }
            }
            binding.comparisonTable.addView(avgScoreRow)

            val totalGamesRow = TableRow(context).apply {
                addView(TextView(context).apply {
                    text = getString(R.string.total_games_header)
                    setPadding(8, 8, 8, 8)
                    textSize = 14f
                })
                playerStats.forEach { stats ->
                    addView(TextView(context).apply {
                        text = stats.totalGames.toString()
                        setPadding(8, 8, 8, 8)
                        textSize = 14f
                        setTextColor(getColorForValue(stats.totalGames, playerStats.map { it.totalGames }))
                    })
                }
            }
            binding.comparisonTable.addView(totalGamesRow)
        }
    }

    private fun getColorForValue(value: Number, values: List<Number>): Int {
        val floatValues = values.map { it.toFloat() }
        val max = floatValues.maxOrNull() ?: return Color.YELLOW
        val min = floatValues.minOrNull() ?: return Color.YELLOW
        return when (value.toFloat()) {
            max -> Color.GREEN
            min -> if (values.size > 1) Color.RED else Color.YELLOW
            else -> Color.YELLOW
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}