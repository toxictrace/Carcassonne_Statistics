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
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

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
    private lateinit var comparisonTypeAdapter: ArrayAdapter<String>

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
        setupComparisonTypeSpinner()
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

    private fun setupComparisonTypeSpinner() {
        val comparisonTypes = listOf(
            getString(R.string.comparison_table),
            getString(R.string.skill_comparison),
            getString(R.string.experience_vs_skill_comparison),
            getString(R.string.stability_comparison),
            getString(R.string.score_trend_comparison),
            getString(R.string.wins_vs_losses_comparison),
            getString(R.string.score_gap_comparison),
            getString(R.string.last_place_comparison),
            getString(R.string.max_min_gap_comparison)
        )
        comparisonTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, comparisonTypes)
        comparisonTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.comparisonTypeSpinner.adapter = comparisonTypeAdapter
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
                if (view != null) {
                    updateGlobalStats()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allGames.collect { gameList ->
                games = gameList.sortedBy { it.game.date } // Сортировка по дате для трендов
                if (view != null) {
                    updateGlobalStats()
                }
            }
        }
    }

    private fun updateGlobalStats() {
        _binding?.let { binding ->
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

            // Longest Win Streak
            val winStreaks = mutableMapOf<Int, Int>()
            var currentStreak = 0
            var currentWinner: Int? = null
            games.forEach { game ->
                val winner = game.gamePlayers.maxByOrNull { it.score }?.playerId
                if (winner == currentWinner) {
                    currentStreak++
                } else {
                    currentWinner?.let { winStreaks[it] = maxOf(winStreaks.getOrDefault(it, 0), currentStreak) }
                    currentStreak = 1
                    currentWinner = winner
                }
            }
            currentWinner?.let { winStreaks[it] = maxOf(winStreaks.getOrDefault(it, 0), currentStreak) }
            val longestStreak = winStreaks.entries.maxByOrNull { it.value }
            val longestStreakText = longestStreak?.let { entry ->
                val player = players.find { it.id == entry.key }
                getString(R.string.longest_win_streak_stat, player?.name ?: "Unknown", entry.value)
            } ?: "Longest Win Streak: N/A"

            // Highest Average Position
            val playerPositions = mutableMapOf<Int, MutableList<Int>>()
            games.forEach { game ->
                val sortedPlayers = game.gamePlayers.sortedByDescending { it.score }
                sortedPlayers.forEachIndexed { index, gamePlayer ->
                    playerPositions.getOrPut(gamePlayer.playerId) { mutableListOf() }.add(index + 1)
                }
            }
            val avgPositions = playerPositions.map { (playerId, positions) ->
                val player = players.find { it.id == playerId }
                player?.name to (positions.average())
            }.filter { it.first != null }
            val highestAvgPosition = avgPositions.minByOrNull { it.second }
            val highestAvgPositionText = highestAvgPosition?.let {
                getString(R.string.highest_avg_position_stat, it.first, it.second)
            } ?: "Highest Avg Position: N/A"

            // Most Frequent Second Place (within 5 points)
            val secondPlaceCounts = mutableMapOf<Int, Int>()
            games.forEach { game ->
                val sortedPlayers = game.gamePlayers.sortedByDescending { it.score }
                if (sortedPlayers.size > 1) {
                    val winnerScore = sortedPlayers[0].score
                    val secondScore = sortedPlayers[1].score
                    if (winnerScore - secondScore <= 5) {
                        val secondId = sortedPlayers[1].playerId
                        secondPlaceCounts[secondId] = secondPlaceCounts.getOrDefault(secondId, 0) + 1
                    }
                }
            }
            val mostFrequentSecond = secondPlaceCounts.entries.maxByOrNull { it.value }
            val mostFrequentSecondText = mostFrequentSecond?.let { entry ->
                val player = players.find { it.id == entry.key }
                getString(R.string.most_frequent_second_stat, player?.name ?: "Unknown", entry.value)
            } ?: "Most Frequent 2nd: N/A"

            binding.totalGames.text = getString(R.string.total_games_stat, totalGames)
            binding.avgScore.text = getString(R.string.avg_score_stat, avgScore)
            binding.maxScore.text = getString(R.string.max_score_stat, maxScore)
            binding.minScore.text = getString(R.string.min_score_stat, minScore)
            binding.topPlayers.text = getString(R.string.top_players_stat, topPlayers)
            binding.longestWinStreak.text = longestStreakText
            binding.highestAvgPosition.text = highestAvgPositionText
            binding.mostFrequentSecond.text = mostFrequentSecondText
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
            when (binding.comparisonTypeSpinner.selectedItemPosition) {
                0 -> updateComparisonTable(validPlayers)
                1 -> updateSkillChart(validPlayers)
                2 -> updateExperienceVsSkillChart(validPlayers)
                3 -> updateStabilityChart(validPlayers)
                4 -> updateScoreTrendChart(validPlayers)
                5 -> updateWinsVsLossesChart(validPlayers)
                6 -> updateScoreGapChart(validPlayers)
                7 -> updateLastPlaceChart(validPlayers)
                8 -> updateMaxMinGapChart(validPlayers)
            }
        }
    }

    private fun calculatePlayerStats(selectedPlayers: List<Player>): List<PlayerStats> {
        return selectedPlayers.map { player ->
            val playerGames = games.filter { game -> game.gamePlayers.any { it.playerId == player.id } }
            val wins = playerGames.count { game -> game.gamePlayers.maxByOrNull { it.score }?.playerId == player.id }
            val scores = playerGames.flatMap { it.gamePlayers.filter { it.playerId == player.id }.map { it.score } }
            val avgScore = if (scores.isNotEmpty()) scores.average().toFloat() else 0f
            val totalGames = playerGames.size
            val skill = if (totalGames > 0) (wins.toFloat() / totalGames) * 100 else 0f
            val experience = totalGames
            val stability = if (scores.size > 1) {
                val mean = avgScore
                val variance = scores.map { (it - mean) * (it - mean) }.average()
                sqrt(variance).toFloat()
            } else 0f
            val losses = totalGames - wins
            val lastPlaceCount = playerGames.count { game ->
                game.gamePlayers.minByOrNull { it.score }?.playerId == player.id
            }
            val gaps = playerGames.map { game ->
                val sortedScores = game.gamePlayers.map { it.score }.sorted()
                val playerScore = game.gamePlayers.find { it.playerId == player.id }!!.score
                val index = sortedScores.indexOf(playerScore)
                if (index == 0) sortedScores[1] - playerScore
                else if (index == sortedScores.size - 1) playerScore - sortedScores[index - 1]
                else minOf(playerScore - sortedScores[index - 1], sortedScores[index + 1] - playerScore)
            }.map { it.toFloat() }
            val maxGap = gaps.maxOrNull() ?: 0f
            val minGap = gaps.minOrNull() ?: 0f
            val gapsFromWinner = playerGames.map { game ->
                val winnerScore = game.gamePlayers.maxByOrNull { it.score }!!.score
                val playerScore = game.gamePlayers.find { it.playerId == player.id }!!.score
                (winnerScore - playerScore).toFloat()
            }
            PlayerStats(player.name, wins, avgScore, totalGames, skill, experience, stability, scores, losses, lastPlaceCount, maxGap, minGap, gapsFromWinner)
        }
    }

    private fun updateComparisonTable(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.tableContainer)
            binding.comparisonTable.removeAllViews()

            val headerRow = TableRow(context).apply {
                addView(TextView(context).apply { text = "" })
                playerStats.forEach { stats ->
                    addView(TextView(context).apply {
                        text = stats.name
                        setPadding(8, 8, 8, 8)
                        textSize = 16f
                        setTextColor(resources.getColor(R.color.on_background, null))
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

    private fun updateSkillChart(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.skillChart)
            val entries = playerStats.mapIndexed { index, stats -> BarEntry(index.toFloat(), stats.skill) }
            val dataSet = BarDataSet(entries, getString(R.string.skill_chart_title)).apply {
                colors = playerStats.map { getColorForValue(it.skill, playerStats.map { s -> s.skill }) }
                valueTextColor = resources.getColor(R.color.on_background, null)
            }
            val barData = BarData(dataSet)
            binding.skillChart.data = barData
            configureChart(binding.skillChart, playerStats.map { it.name })
        }
    }

    private fun updateExperienceVsSkillChart(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.experienceSkillChart)
            val entries = mutableListOf<PieEntry>()
            playerStats.forEach { stats ->
                entries.add(PieEntry(stats.experience.toFloat(), "${stats.name} (Exp)"))
                entries.add(PieEntry(stats.avgScore, "${stats.name} (Skill)"))
            }
            val dataSet = PieDataSet(entries, getString(R.string.experience_vs_skill_chart_title)).apply {
                colors = playerStats.flatMap {
                    listOf(
                        getColorForValue(it.experience, playerStats.map { s -> s.experience }, true),
                        getColorForValue(it.avgScore, playerStats.map { s -> s.avgScore }, true)
                    )
                }
                valueTextColor = resources.getColor(R.color.on_background, null)
            }
            val pieData = PieData(dataSet)
            binding.experienceSkillChart.data = pieData
            binding.experienceSkillChart.description.isEnabled = false
            binding.experienceSkillChart.centerText = getString(R.string.experience_vs_skill_chart_title)
            binding.experienceSkillChart.setCenterTextSize(12f)
            binding.experienceSkillChart.setCenterTextColor(resources.getColor(R.color.on_background, null))
            binding.experienceSkillChart.invalidate()
        }
    }

    private fun updateStabilityChart(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.stabilityChart)
            val entries = playerStats.mapIndexed { index, stats -> Entry(index.toFloat(), stats.stability) }
            val dataSet = LineDataSet(entries, getString(R.string.stability_chart_title)).apply {
                colors = listOf(Color.YELLOW)
                valueTextColor = resources.getColor(R.color.on_background, null)
                setCircleColors(playerStats.map { getColorForValue(it.stability, playerStats.map { s -> s.stability }, false) })
            }
            val lineData = LineData(dataSet)
            binding.stabilityChart.data = lineData
            configureChart(binding.stabilityChart, playerStats.map { it.name })
        }
    }

    private fun updateScoreTrendChart(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.scoreTrendChart)
            val dataSets = playerStats.mapIndexed { index, stats ->
                val entries = stats.scoreTrend.mapIndexed { gameIndex, score ->
                    Entry(gameIndex.toFloat(), score.toFloat())
                }
                LineDataSet(entries, stats.name).apply {
                    colors = listOf(getColorForValue(stats.avgScore, playerStats.map { it.avgScore }))
                    valueTextColor = resources.getColor(R.color.on_background, null)
                    setCircleColor(getColorForValue(stats.avgScore, playerStats.map { it.avgScore }))
                }
            }
            val lineData = LineData(dataSets)
            binding.scoreTrendChart.data = lineData
            binding.scoreTrendChart.description.text = getString(R.string.score_trend_chart_title)
            binding.scoreTrendChart.description.textColor = resources.getColor(R.color.on_background, null)
            binding.scoreTrendChart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = resources.getColor(R.color.on_background, null)
            }
            binding.scoreTrendChart.axisLeft.setDrawGridLines(false)
            binding.scoreTrendChart.axisLeft.textColor = resources.getColor(R.color.on_background, null)
            binding.scoreTrendChart.axisRight.isEnabled = false
            binding.scoreTrendChart.invalidate()
        }
    }

    private fun updateWinsVsLossesChart(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.winsVsLossesChart)
            val entries = playerStats.flatMap { stats ->
                listOf(
                    PieEntry(stats.wins.toFloat(), "${stats.name} Wins"),
                    PieEntry(stats.losses.toFloat(), "${stats.name} Losses")
                )
            }
            val dataSet = PieDataSet(entries, getString(R.string.wins_vs_losses_chart_title)).apply {
                colors = playerStats.flatMap { listOf(Color.GREEN, Color.RED) }
                valueTextColor = resources.getColor(R.color.on_background, null)
            }
            val pieData = PieData(dataSet)
            binding.winsVsLossesChart.data = pieData
            binding.winsVsLossesChart.description.isEnabled = false
            binding.winsVsLossesChart.centerText = getString(R.string.wins_vs_losses_chart_title)
            binding.winsVsLossesChart.setCenterTextSize(12f)
            binding.winsVsLossesChart.setCenterTextColor(resources.getColor(R.color.on_background, null))
            binding.winsVsLossesChart.invalidate()
        }
    }

    private fun updateScoreGapChart(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.scoreGapChart)
            val entries = playerStats.mapIndexed { index, stats ->
                BarEntry(index.toFloat(), stats.gapsFromWinner.average().toFloat())
            }
            val dataSet = BarDataSet(entries, getString(R.string.score_gap_chart_title)).apply {
                colors = playerStats.map { getColorForValue(it.gapsFromWinner.average(), playerStats.map { s -> s.gapsFromWinner.average() }, false) }
                valueTextColor = resources.getColor(R.color.on_background, null)
            }
            val barData = BarData(dataSet)
            binding.scoreGapChart.data = barData
            configureChart(binding.scoreGapChart, playerStats.map { it.name })
        }
    }

    private fun updateLastPlaceChart(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.lastPlaceChart)
            val entries = playerStats.mapIndexed { index, stats ->
                BarEntry(index.toFloat(), stats.lastPlaceCount.toFloat())
            }
            val dataSet = BarDataSet(entries, getString(R.string.last_place_chart_title)).apply {
                colors = playerStats.map { getColorForValue(it.lastPlaceCount, playerStats.map { s -> s.lastPlaceCount }, false) }
                valueTextColor = resources.getColor(R.color.on_background, null)
            }
            val barData = BarData(dataSet)
            binding.lastPlaceChart.data = barData
            configureChart(binding.lastPlaceChart, playerStats.map { it.name })
        }
    }

    private fun updateMaxMinGapChart(selectedPlayers: List<Player>) {
        _binding?.let { binding ->
            val playerStats = calculatePlayerStats(selectedPlayers)
            setChartVisibility(binding.maxMinGapChart)
            val maxEntries = playerStats.mapIndexed { index, stats ->
                BarEntry(index.toFloat() - 0.2f, stats.maxGap)
            }
            val minEntries = playerStats.mapIndexed { index, stats ->
                BarEntry(index.toFloat() + 0.2f, stats.minGap)
            }
            val maxDataSet = BarDataSet(maxEntries, "Max Gap").apply {
                color = Color.GREEN
                valueTextColor = resources.getColor(R.color.on_background, null)
            }
            val minDataSet = BarDataSet(minEntries, "Min Gap").apply {
                color = Color.RED
                valueTextColor = resources.getColor(R.color.on_background, null)
            }
            val barData = BarData(maxDataSet, minDataSet)
            barData.barWidth = 0.4f
            binding.maxMinGapChart.data = barData
            configureChart(binding.maxMinGapChart, playerStats.map { it.name })
        }
    }

    private fun configureChart(chart: com.github.mikephil.charting.charts.Chart<*>, labels: List<String>) {
        when (chart) {
            is com.github.mikephil.charting.charts.BarChart -> {
                chart.xAxis.apply {
                    valueFormatter = IndexAxisValueFormatter(labels)
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    setDrawGridLines(false)
                    textColor = resources.getColor(R.color.on_background, null)
                }
                chart.axisLeft.setDrawGridLines(false)
                chart.axisLeft.textColor = resources.getColor(R.color.on_background, null)
                chart.axisRight.isEnabled = false
                chart.description.textColor = resources.getColor(R.color.on_background, null)
            }
            is com.github.mikephil.charting.charts.LineChart -> {
                chart.xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = resources.getColor(R.color.on_background, null)
                }
                chart.axisLeft.setDrawGridLines(false)
                chart.axisLeft.textColor = resources.getColor(R.color.on_background, null)
                chart.axisRight.isEnabled = false
                chart.description.textColor = resources.getColor(R.color.on_background, null)
            }
        }
        chart.description.isEnabled = true
        chart.invalidate()
    }

    private fun setChartVisibility(visibleView: View) {
        _binding?.let { binding ->
            binding.tableContainer.visibility = if (visibleView == binding.tableContainer) View.VISIBLE else View.GONE
            binding.skillChart.visibility = if (visibleView == binding.skillChart) View.VISIBLE else View.GONE
            binding.experienceSkillChart.visibility = if (visibleView == binding.experienceSkillChart) View.VISIBLE else View.GONE
            binding.stabilityChart.visibility = if (visibleView == binding.stabilityChart) View.VISIBLE else View.GONE
            binding.scoreTrendChart.visibility = if (visibleView == binding.scoreTrendChart) View.VISIBLE else View.GONE
            binding.winsVsLossesChart.visibility = if (visibleView == binding.winsVsLossesChart) View.VISIBLE else View.GONE
            binding.scoreGapChart.visibility = if (visibleView == binding.scoreGapChart) View.VISIBLE else View.GONE
            binding.lastPlaceChart.visibility = if (visibleView == binding.lastPlaceChart) View.VISIBLE else View.GONE
            binding.maxMinGapChart.visibility = if (visibleView == binding.maxMinGapChart) View.VISIBLE else View.GONE
        }
    }

    private fun getColorForValue(value: Number, values: List<Number>, higherIsBetter: Boolean = true): Int {
        val floatValues = values.map { it.toFloat() }
        val max = floatValues.maxOrNull() ?: return Color.YELLOW
        val min = floatValues.minOrNull() ?: return Color.YELLOW
        return if (higherIsBetter) {
            when (value.toFloat()) {
                max -> Color.GREEN
                min -> if (values.size > 1) Color.RED else Color.YELLOW
                else -> Color.YELLOW
            }
        } else {
            when (value.toFloat()) {
                min -> Color.GREEN
                max -> if (values.size > 1) Color.RED else Color.YELLOW
                else -> Color.YELLOW
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}