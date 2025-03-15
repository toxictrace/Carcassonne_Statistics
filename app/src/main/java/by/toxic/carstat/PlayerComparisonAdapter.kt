package by.toxic.carstat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import by.toxic.carstat.databinding.ItemPlayerComparisonBinding

class PlayerComparisonAdapter : RecyclerView.Adapter<PlayerComparisonAdapter.PlayerViewHolder>() {

    private var statsList: List<PlayerStats> = emptyList()

    fun submitList(stats: List<PlayerStats>) {
        statsList = stats
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val binding = ItemPlayerComparisonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(statsList[position])
    }

    override fun getItemCount(): Int = statsList.size

    inner class PlayerViewHolder(private val binding: ItemPlayerComparisonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(stats: PlayerStats) {
            binding.playerName.text = stats.name
            binding.playerStats.text = binding.root.context.getString(
                R.string.player_comparison_stats,
                stats.wins,
                stats.avgScore,
                stats.totalGames
            )

            val color = when {
                statsList.isNotEmpty() && stats.wins == statsList.maxByOrNull { it.wins }?.wins -> Color.GREEN
                statsList.size > 1 && stats.wins == statsList.minByOrNull { it.wins }?.wins -> Color.RED
                else -> Color.YELLOW
            }
            binding.playerName.setTextColor(color)
            binding.playerStats.setTextColor(color)
        }
    }
}