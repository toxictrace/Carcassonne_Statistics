package by.toxic.carstat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
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

    private val gamesList = mutableListOf<GameWithPlayers>()

    fun updateGames(newGames: List<GameWithPlayers>) {
        val diffResult = DiffUtil.calculateDiff(GameDiffCallback(gamesList, newGames))
        gamesList.clear()
        gamesList.addAll(newGames)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(gamesList[position])
    }

    override fun getItemCount(): Int = gamesList.size

    inner class GameViewHolder(private val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(gameWithPlayers: GameWithPlayers) {
            // Установка фрейма в ImageView
            binding.gameFrame.setImageResource(
                when (gameWithPlayers.game.frameId) {
                    1 -> R.drawable.frame1
                    2 -> R.drawable.frame2
                    else -> R.drawable.frame1
                }
            )

            // Дата посередине
            binding.gameDate.text = viewModel.formatDateForDisplay(gameWithPlayers.game.date)

            // Игроки с очками, по два на строку с цветами
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                viewModel.allPlayers.collectLatest { players ->
                    val sortedPlayers = gameWithPlayers.gamePlayers.sortedByDescending { it.score }
                    val playerNamesWithScores = sortedPlayers.map { gamePlayer ->
                        val player = players.find { it.id == gamePlayer.playerId }
                        val color = gamePlayer.color?.let { getColorFromName(it) } ?: Color.WHITE
                        "<font color='#${Integer.toHexString(color).substring(2)}'>${player?.name ?: "Unknown"} - ${gamePlayer.score}</font>"
                    }

                    binding.playersRow1.text = fromHtml(playerNamesWithScores.take(2).joinToString("   "))
                    binding.playersRow2.text = if (playerNamesWithScores.size > 2) {
                        fromHtml(playerNamesWithScores.drop(2).take(2).joinToString("   "))
                    } else ""
                    binding.playersRow3.text = if (playerNamesWithScores.size > 4) {
                        fromHtml(playerNamesWithScores.drop(4).joinToString("   "))
                    } else ""

                    binding.playersRow2.visibility = if (binding.playersRow2.text.isEmpty()) View.GONE else View.VISIBLE
                    binding.playersRow3.visibility = if (binding.playersRow3.text.isEmpty()) View.GONE else View.VISIBLE
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

        // Метод для преобразования HTML-цветов
        private fun fromHtml(html: String): android.text.Spanned {
            return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
        }

        // Получение цвета по имени из EditGameFragment
        private fun getColorFromName(colorName: String): Int {
            return when (colorName) {
                "Yellow" -> Color.YELLOW
                "Red" -> Color.RED
                "Green" -> Color.GREEN
                "Blue" -> Color.BLUE
                "Black" -> Color.BLACK
                "Gray" -> Color.GRAY
                else -> Color.WHITE
            }
        }
    }

    inner class GameDiffCallback(
        private val oldList: List<GameWithPlayers>,
        private val newList: List<GameWithPlayers>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].game.id == newList[newItemPosition].game.id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}