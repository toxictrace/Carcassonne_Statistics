package by.toxic.carstat

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.toxic.carstat.databinding.FragmentEditGameBinding
import by.toxic.carstat.databinding.ItemGamePlayerBinding
import by.toxic.carstat.db.GamePlayer
import by.toxic.carstat.db.Player
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class EditGameFragment : Fragment() {
    private var _binding: FragmentEditGameBinding? = null
    private val binding get() = _binding!!
    private val gameViewModel: GameViewModel by viewModels()
    private lateinit var playerAdapter: PlayerAdapter
    private val tempGamePlayers = mutableListOf<GamePlayer>()
    private val tempPlayers = mutableListOf<Player>()
    private var gameId: Int? = null

    private val availableColors = listOf(
        "Yellow" to Color.YELLOW,
        "Red" to Color.RED,
        "Green" to Color.GREEN,
        "Blue" to Color.BLUE,
        "Black" to Color.BLACK,
        "Gray" to Color.GRAY
    )

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

        viewLifecycleOwner.lifecycleScope.launch {
            tempPlayers.clear()
            tempPlayers.addAll(gameViewModel.allPlayers.first())
            if (gameId != null) {
                val gameWithPlayers = gameViewModel.allGames.first().find { it.game.id == gameId }
                gameWithPlayers?.let {
                    tempGamePlayers.clear()
                    tempGamePlayers.addAll(it.gamePlayers.map { gp ->
                        GamePlayer(gameId!!, gp.playerId, gp.score, gp.color)
                    })
                    binding.dateLabel.text = gameViewModel.formatDateForDisplay(it.game.date)
                }
            } else {
                tempGamePlayers.clear()
                tempGamePlayers.add(GamePlayer(0, 0, 0))
            }
            playerAdapter.updatePlayers(tempGamePlayers)
            Log.d("EditGameFragment", "Data loaded: ${tempGamePlayers.size} players")
        }

        binding.saveButton.setOnClickListener {
            val date = binding.dateLabel.text.toString()
            val players = tempGamePlayers.map { Triple(it.playerId, it.score, it.color) }
            Log.d("EditGameFragment", "Save button clicked, players: $players")
            if (players.isEmpty() || players.all { it.first == 0 }) {
                Toast.makeText(context, getString(R.string.no_players_error), Toast.LENGTH_SHORT).show()
                Log.e("EditGameFragment", "Cannot save: no valid players")
                return@setOnClickListener
            }
            if (tempGamePlayers.any { it.color == null && it.playerId != 0 }) {
                Toast.makeText(context, getString(R.string.select_color_error), Toast.LENGTH_SHORT).show()
                Log.e("EditGameFragment", "Cannot save: color not selected for some players")
                return@setOnClickListener
            }
            gameViewModel.saveGame(
                date,
                players,
                gameId,
                onSuccess = {
                    Log.d("EditGameFragment", "Game saved successfully")
                    findNavController().navigateUp()
                },
                onError = { error ->
                    Log.e("EditGameFragment", "Save error: $error")
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
            val newList = tempGamePlayers.toMutableList().apply { add(GamePlayer(0, 0, 0)) }
            playerAdapter.updatePlayers(newList)
            tempGamePlayers.clear()
            tempGamePlayers.addAll(newList)
            Log.d("EditGameFragment", "Player added, new count: ${tempGamePlayers.size}")
        } else {
            Toast.makeText(context, getString(R.string.no_more_players), Toast.LENGTH_SHORT).show()
        }
    }

    inner class PlayerAdapter : RecyclerView.Adapter<GamePlayerViewHolder>() {
        private val playersList = mutableListOf<GamePlayer>()

        fun updatePlayers(newPlayers: List<GamePlayer>) {
            val diffResult = DiffUtil.calculateDiff(PlayerDiffCallback(playersList, newPlayers))
            playersList.clear()
            playersList.addAll(newPlayers)
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GamePlayerViewHolder {
            val binding = ItemGamePlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return GamePlayerViewHolder(binding, this)
        }

        override fun onBindViewHolder(holder: GamePlayerViewHolder, position: Int) {
            holder.bind(playersList[position])
        }

        override fun getItemCount(): Int = playersList.size
    }

    inner class PlayerDiffCallback(
        private val oldList: List<GamePlayer>,
        private val newList: List<GamePlayer>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].playerId == newList[newItemPosition].playerId
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition] == newList[newItemPosition]
    }

    inner class GamePlayerViewHolder(
        private val binding: ItemGamePlayerBinding,
        private val adapter: PlayerAdapter
    ) : RecyclerView.ViewHolder(binding.root) {
        private val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val score = s.toString().toIntOrNull() ?: 0
                    tempGamePlayers[pos].score = score
                    Log.d("EditGameFragment", "Score updated at position $pos: $score")
                }
            }
        }

        fun bind(gamePlayer: GamePlayer) {
            val position = bindingAdapterPosition
            val selectedIds = tempGamePlayers.mapIndexed { index, pair ->
                if (index != position && pair.playerId != 0) pair.playerId else null
            }.filterNotNull()
            val currentPlayerId = gamePlayer.playerId
            val availablePlayers = tempPlayers.filter { !selectedIds.contains(it.id) || it.id == currentPlayerId }

            val playerOptions = listOf("") + availablePlayers.map { it.name }
            val playerAdapter = ArrayAdapter(binding.root.context, android.R.layout.simple_spinner_item, playerOptions)
            playerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.playerSpinner.adapter = playerAdapter
            binding.playerSpinner.isEnabled = true

            val selectedIndex = if (currentPlayerId == 0) 0 else availablePlayers.indexOfFirst { it.id == currentPlayerId } + 1
            binding.playerSpinner.setSelection(selectedIndex, false)

            binding.playerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    val posInList = bindingAdapterPosition
                    if (posInList != RecyclerView.NO_POSITION && pos in playerOptions.indices) {
                        val oldPlayerId = tempGamePlayers[posInList].playerId
                        val newPlayerId = if (pos == 0) 0 else availablePlayers[pos - 1].id
                        if (oldPlayerId != newPlayerId) {
                            tempGamePlayers[posInList].playerId = newPlayerId
                            if (newPlayerId == 0) tempGamePlayers[posInList].color = null
                            binding.colorSquare.visibility = if (newPlayerId != 0) View.VISIBLE else View.GONE
                            updateColorSquare()
                            Log.d("EditGameFragment", "Player selected at $posInList: $newPlayerId")
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            binding.colorSquare.visibility = if (currentPlayerId != 0) View.VISIBLE else View.GONE
            updateColorSquare()

            binding.colorSquare.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val popup = PopupMenu(requireContext(), binding.colorSquare)
                    popup.menu.add(0, 0, 0, "None")
                    val usedColors = tempGamePlayers.mapNotNull { it.color }.filter { it != tempGamePlayers[pos].color }
                    val available = availableColors.filter { it.first !in usedColors }
                    available.forEachIndexed { index, (colorName, _) ->
                        popup.menu.add(0, index + 1, 0, colorName)
                    }
                    popup.setOnMenuItemClickListener { item ->
                        val selectedColor = if (item.itemId == 0) null else available[item.itemId - 1].first
                        tempGamePlayers[pos].color = selectedColor
                        updateColorSquare()
                        adapter.updatePlayers(tempGamePlayers.toList())
                        Log.d("EditGameFragment", "Color selected at $pos: $selectedColor")
                        true
                    }
                    popup.show()
                }
            }

            binding.scoreEditText.removeTextChangedListener(textWatcher)
            binding.scoreEditText.setText(if (gamePlayer.score > 0) gamePlayer.score.toString() else "")
            binding.scoreEditText.isEnabled = true
            binding.scoreEditText.isFocusable = true
            binding.scoreEditText.isFocusableInTouchMode = true
            binding.scoreEditText.addTextChangedListener(textWatcher)

            binding.scoreEditText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(binding.scoreEditText, InputMethodManager.SHOW_IMPLICIT)
                }
            }

            binding.removeButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val newList = tempGamePlayers.toMutableList().apply { removeAt(pos) }
                    adapter.updatePlayers(newList)
                    tempGamePlayers.clear()
                    tempGamePlayers.addAll(newList)
                    Log.d("EditGameFragment", "Player removed at position $pos")
                }
            }
        }

        private fun updateColorSquare() {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val colorName = tempGamePlayers[pos].color
                binding.colorSquare.setBackgroundColor(
                    colorName?.let { name ->
                        availableColors.find { it.first == name }?.second ?: Color.BLACK
                    } ?: Color.LTGRAY
                )
            }
        }
    }
}