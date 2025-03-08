package by.toxic.carstat

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.toxic.carstat.databinding.FragmentEditGameBinding
import by.toxic.carstat.databinding.ItemGamePlayerBinding
import by.toxic.carstat.db.GamePlayer
import by.toxic.carstat.db.Player
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class EditGameFragment : Fragment() {
    private var _binding: FragmentEditGameBinding? = null
    private val binding get() = _binding!!
    private val gameViewModel: GameViewModel by viewModels()
    private lateinit var playerAdapter: PlayerAdapter
    private val tempGamePlayers = mutableListOf<GamePlayer>()
    private val tempPlayers = mutableListOf<Player>()
    private var gameId: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerAdapter = PlayerAdapter()
        binding.playersRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.playersRecyclerView.adapter = playerAdapter

        gameId = arguments?.getInt("gameId")
        val initialDate = arguments?.getString("date") ?: gameViewModel.formatDateForDisplay("")
        binding.dateLabel.text = initialDate

        binding.calendarIcon.setOnClickListener {
            showDatePickerDialog()
        }

        runBlocking {
            tempPlayers.clear()
            tempPlayers.addAll(gameViewModel.allPlayers.first())
            if (gameId != null) {
                val gameWithPlayers = gameViewModel.allGames.first().find { it.game.id == gameId }
                gameWithPlayers?.let {
                    tempGamePlayers.clear()
                    tempGamePlayers.addAll(it.gamePlayers.map { gp ->
                        GamePlayer(gameId!!, gp.playerId, gp.score)
                    })
                    binding.dateLabel.text = gameViewModel.formatDateForDisplay(it.game.date)
                }
            } else {
                tempGamePlayers.clear()
                tempGamePlayers.add(GamePlayer(0, 0, 0)) // Только одна строка для новой игры
            }
            playerAdapter.notifyDataSetChanged()
        }

        binding.saveButton.setOnClickListener {
            val date = binding.dateLabel.text.toString()
            val players = tempGamePlayers.map { Pair(it.playerId, it.score) }
            gameViewModel.saveGame(
                date,
                players,
                gameId,
                onSuccess = { findNavController().navigateUp() },
                onError = { error ->
                    Toast.makeText(context, getString(R.string.save_game_error, error), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = String.format("%02d.%02d.%d", selectedDay, selectedMonth + 1, selectedYear)
            binding.dateLabel.text = selectedDate
        }, year, month, day).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun addPlayerFromNavBar() {
        val selectedIds = tempGamePlayers.map { it.playerId }.filter { it != 0 }
        val remainingPlayers = tempPlayers.filter { !selectedIds.contains(it.id) }
        if (remainingPlayers.isNotEmpty()) {
            tempGamePlayers.add(GamePlayer(0, 0, 0))
            playerAdapter.notifyItemInserted(tempGamePlayers.size - 1)
        } else {
            Toast.makeText(context, getString(R.string.no_more_players), Toast.LENGTH_SHORT).show()
        }
    }

    inner class PlayerAdapter : RecyclerView.Adapter<GamePlayerViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GamePlayerViewHolder {
            val binding = ItemGamePlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return GamePlayerViewHolder(binding)
        }

        override fun onBindViewHolder(holder: GamePlayerViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = tempGamePlayers.size
    }

    inner class GamePlayerViewHolder(private val binding: ItemGamePlayerBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isBinding = false
        private var isUpdatingText = false
        private var currentPosition: Int = -1
        private lateinit var playerOptions: List<String>
        private lateinit var availablePlayers: List<Player>
        private val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUpdatingText && adapterPosition >= 0) {
                    val score = s.toString().toIntOrNull() ?: 0
                    tempGamePlayers[adapterPosition].score = score
                    println("DEBUG: Score updated for position $adapterPosition: $score")
                }
            }
        }

        fun bind(position: Int) {
            isBinding = true
            currentPosition = position
            println("DEBUG: Binding position $position, playerId=${tempGamePlayers[position].playerId}")

            val selectedIds = tempGamePlayers.mapIndexed { index, pair ->
                if (index != position && pair.playerId != 0) pair.playerId else null
            }.filterNotNull()
            val currentPlayerId = tempGamePlayers[position].playerId
            availablePlayers = tempPlayers.filter { !selectedIds.contains(it.id) || it.id == currentPlayerId }
            println("DEBUG: Available players: ${availablePlayers.map { it.name }}, selectedIds=$selectedIds")

            playerOptions = listOf("") + availablePlayers.map { it.name }
            val adapter = ArrayAdapter(binding.root.context, android.R.layout.simple_spinner_item, playerOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.playerSpinner.adapter = adapter
            binding.playerSpinner.isEnabled = true

            val selectedIndex = if (currentPlayerId == 0) 0 else availablePlayers.indexOfFirst { it.id == currentPlayerId } + 1
            binding.playerSpinner.setSelection(selectedIndex, false)

            binding.playerSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    if (!isBinding && pos in playerOptions.indices) {
                        println("DEBUG: Spinner selected at position $currentPosition, selectedPos=$pos")
                        val oldPlayerId = tempGamePlayers[currentPosition].playerId
                        val newPlayerId = if (pos == 0) 0 else availablePlayers[pos - 1].id
                        if (oldPlayerId != newPlayerId) {
                            tempGamePlayers[currentPosition].playerId = newPlayerId
                            println("DEBUG: Player selected: ${if (pos == 0) "None" else availablePlayers[pos - 1].name}, id=$newPlayerId")
                            playerAdapter.notifyItemChanged(currentPosition)
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            })

            binding.scoreEditText.removeTextChangedListener(textWatcher)
            isUpdatingText = true
            binding.scoreEditText.setText(if (tempGamePlayers[position].score > 0) tempGamePlayers[position].score.toString() else "")
            isUpdatingText = false
            binding.scoreEditText.isEnabled = true
            binding.scoreEditText.isFocusable = true
            binding.scoreEditText.isFocusableInTouchMode = true
            binding.scoreEditText.addTextChangedListener(textWatcher)

            binding.removeButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    tempGamePlayers.removeAt(pos)
                    playerAdapter.notifyItemRemoved(pos)
                    playerAdapter.notifyItemRangeChanged(pos, playerAdapter.itemCount)
                }
            }
            isBinding = false
        }
    }
}