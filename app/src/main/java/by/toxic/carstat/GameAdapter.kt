package by.toxic.carstat

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
            binding.gameDate.text = viewModel.formatDateForDisplay(gameWithPlayers.game.date)
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                viewModel.allPlayers.collectLatest { players ->
                    val sortedPlayers = gameWithPlayers.gamePlayers.sortedByDescending { it.score }
                    val playerNamesWithScores = sortedPlayers.map { gamePlayer ->
                        val player = players.find { it.id == gamePlayer.playerId }
                        val name = player?.name ?: "Unknown"
                        val score = gamePlayer.score
                        val color = when (gamePlayer.color) {
                            "Yellow" -> Color.YELLOW
                            "Red" -> Color.RED
                            "Green" -> Color.GREEN
                            "Blue" -> Color.BLUE
                            "Black" -> Color.BLACK
                            "Gray" -> Color.GRAY
                            else -> Color.BLACK
                        }
                        val fullText = "$name - $score"
                        SpannableString(fullText).apply {
                            setSpan(ForegroundColorSpan(color), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    val separator = ", "
                    val totalText = playerNamesWithScores.joinToString(separator) { it.toString() }
                    val spannableText = SpannableString(totalText)
                    var start = 0
                    playerNamesWithScores.forEach { spannable ->
                        val nameLength = spannable.toString().indexOf(" - ")
                        spannable.getSpans(0, nameLength, ForegroundColorSpan::class.java).forEach { span ->
                            spannableText.setSpan(
                                ForegroundColorSpan(span.foregroundColor),
                                start,
                                start + nameLength,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        start += spannable.length + separator.length
                    }
                    binding.gamePlayers.text = spannableText
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
            binding.editButton.visibility = View.GONE
            binding.deleteButton.visibility = View.GONE
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