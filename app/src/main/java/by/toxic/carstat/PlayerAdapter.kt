package by.toxic.carstat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import by.toxic.carstat.databinding.ItemPlayerBinding
import by.toxic.carstat.db.Player

class PlayerAdapter(
    private val onEdit: (Player) -> Unit,
    private val onDelete: (Player) -> Unit
) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    private var players: List<Player> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val binding = ItemPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(players[position])
    }

    override fun getItemCount(): Int = players.size

    fun updatePlayers(newPlayers: List<Player>) {
        players = newPlayers
        notifyDataSetChanged()
    }

    inner class PlayerViewHolder(private val binding: ItemPlayerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(player: Player) {
            binding.playerName.text = player.name

            // Используем сохранённый frameId
            val backgroundRes = when (player.frameId) {
                1 -> R.drawable.player_background1
                2 -> R.drawable.player_background2
                3 -> R.drawable.player_background3
                else -> R.drawable.player_background1 // По умолчанию
            }
            binding.playerBackground.setImageResource(backgroundRes)

            binding.root.setOnClickListener { onEdit(player) }
            binding.root.setOnLongClickListener {
                onDelete(player)
                true
            }
        }
    }
}