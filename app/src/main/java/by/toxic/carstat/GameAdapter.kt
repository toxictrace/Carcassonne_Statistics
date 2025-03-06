package by.toxic.carstat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import by.toxic.carstat.databinding.ItemGameBinding
import by.toxic.carstat.db.GameWithPlayers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameAdapter(
    private val fragment: Fragment,
    private val viewModel: GameViewModel,
    private val onEdit: (GameWithPlayers) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    private var games: List<GameWithPlayers> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(games[position])
    }

    override fun getItemCount(): Int = games.size

    fun updateGames(newGames: List<GameWithPlayers>) {
        games = newGames
        notifyDataSetChanged()
    }

    inner class GameViewHolder(private val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(gameWithPlayers: GameWithPlayers) {
            binding.gameDate.text = viewModel.formatDateForDisplay(gameWithPlayers.game.date)
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                viewModel.allPlayers.collectLatest { players ->
                    val sortedPlayers = gameWithPlayers.gamePlayers.sortedByDescending { it.score } // Сортировка по убыванию очков
                    val playerNamesWithScores = sortedPlayers.map { gamePlayer ->
                        val player = players.find { it.id == gamePlayer.playerId }
                        "${player?.name ?: "Unknown"} - ${gamePlayer.score}"
                    }.joinToString(", ")
                    binding.gamePlayers.text = playerNamesWithScores
                }
            }
            binding.root.setOnClickListener { onEdit(gameWithPlayers) }
            binding.root.setOnLongClickListener {
                AlertDialog.Builder(binding.root.context)
                    .setTitle(R.string.delete_game)
                    .setMessage(binding.root.context.getString(R.string.confirm_delete_game, gameWithPlayers.game.date))
                    .setPositiveButton(R.string.yes) { _, _ ->
                        onDelete(gameWithPlayers.game.id)
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
        }
    }
}