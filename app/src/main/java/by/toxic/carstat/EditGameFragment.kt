package by.toxic.carstat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.toxic.carstat.databinding.FragmentEditGameBinding
import by.toxic.carstat.databinding.ItemGamePlayerBinding
import by.toxic.carstat.db.Player
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditGameFragment : Fragment() {

    private var _binding: FragmentEditGameBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GameViewModel by viewModels()
    private lateinit var playerAdapter: GamePlayerAdapter
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val tempGame = mutableListOf<TempGame>()
    private val tempPlayers = mutableListOf<TempPlayer>()
    private val tempGamePlayers = mutableListOf<TempGamePlayer>()
    private var gameId: Int? = null

    data class TempGame(val id: Int = -1, val date: String)
    data class TempPlayer(val id: Int, val name: String, var visible: Boolean)
    data class TempGamePlayer(var playerId: Int, var score: Int = 0)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gameId = arguments?.getInt("gameId", -1)?.takeIf { it != -1 }

        tempGame.clear()
        tempPlayers.clear()
        tempGamePlayers.clear()

        playerAdapter = GamePlayerAdapter(viewModel, tempPlayers, tempGamePlayers, this)
        playerAdapter.setHasStableIds(true)
        binding.playersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = playerAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val players = viewModel.allPlayers.first()
            tempPlayers.addAll(players.map { TempPlayer(it.id, it.name, true) })
            println("DEBUG: Players loaded: ${players.size}, TempPlayers: ${tempPlayers.size}")

            if (tempPlayers.isEmpty() && gameId == null) {
                Toast.makeText(requireContext(), R.string.no_players_available, Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
                return@launch
            }

            if (gameId == null) {
                tempGame.add(TempGame(date = dateFormat.format(Date())))
                binding.dateLabel.text = tempGame[0].date
            } else {
                val games = viewModel.allGames.first()
                val game = games.find { it.game.id == gameId }
                if (game != null) {
                    val dbDate = game.game.date
                    val displayDate = viewModel.formatDateForDisplay(dbDate)
                    tempGame.add(TempGame(game.game.id, displayDate))
                    tempGamePlayers.addAll(game.gamePlayers.map { TempGamePlayer(it.playerId, it.score) })
                    binding.dateLabel.text = displayDate
                } else {
                    tempGame.add(TempGame(date = dateFormat.format(Date())))
                    binding.dateLabel.text = tempGame[0].date
                }
            }
            playerAdapter.notifyDataSetChanged()
        }

        binding.calendarIcon.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.select_date)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                val newDate = dateFormat.format(Date(selection))
                if (tempGame.isNotEmpty()) {
                    tempGame[0] = TempGame(tempGame[0].id, newDate)
                    binding.dateLabel.text = newDate
                } else {
                    tempGame.add(TempGame(date = newDate))
                    binding.dateLabel.text = newDate
                }
            }
            datePicker.show(parentFragmentManager, "DATE_PICKER")
        }

        binding.saveButton.setOnClickListener {
            if (tempGamePlayers.isEmpty()) {
                Toast.makeText(requireContext(), R.string.add_player_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (tempGamePlayers.any { it.playerId == 0 }) {
                Toast.makeText(requireContext(), R.string.select_all_players, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (tempGamePlayers.any { it.score == 0 }) {
                Toast.makeText(requireContext(), R.string.enter_scores_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedDate = tempGame.firstOrNull()?.date
            val date = try {
                selectedDate?.let { dateFormat.parse(it)?.let { d -> dbDateFormat.format(d) } } ?: dbDateFormat.format(Date())
            } catch (e: Exception) {
                dbDateFormat.format(Date())
            }
            viewModel.saveGame(
                date,
                tempGamePlayers.map { Pair(it.playerId, it.score) },
                gameId,
                onSuccess = { findNavController().popBackStack() },
                onError = { error -> Toast.makeText(requireContext(), getString(R.string.save_game_error, error), Toast.LENGTH_SHORT).show() }
            )
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.confirm_exit_title)
                    .setMessage(R.string.confirm_exit_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        binding.playersRecyclerView.adapter = null
                        tempGame.clear()
                        tempPlayers.clear()
                        tempGamePlayers.clear()
                        findNavController().popBackStack()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
        })
    }

    fun addPlayerFromNavBar() {
        if (tempGamePlayers.size < 6) {
            tempGamePlayers.add(TempGamePlayer(0))
            val position = tempGamePlayers.size - 1
            playerAdapter.notifyItemInserted(position)
            binding.playersRecyclerView.scrollToPosition(position)
            println("DEBUG: Player added at position $position")
        } else {
            Toast.makeText(requireContext(), R.string.max_players_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.playersRecyclerView.adapter = null
        _binding = null
    }

    inner class GamePlayerAdapter(
        private val viewModel: GameViewModel,
        private val tempPlayers: MutableList<TempPlayer>,
        private val tempGamePlayers: MutableList<TempGamePlayer>,
        private val fragment: Fragment
    ) : RecyclerView.Adapter<GamePlayerViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GamePlayerViewHolder {
            val binding = ItemGamePlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return GamePlayerViewHolder(binding)
        }

        override fun onBindViewHolder(holder: GamePlayerViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = tempGamePlayers.size

        override fun getItemId(position: Int): Long = tempGamePlayers[position].playerId.toLong()
    }

    inner class GamePlayerViewHolder(val binding: ItemGamePlayerBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isBinding = false

        fun bind(position: Int) {
            isBinding = true
            println("DEBUG: Binding position $position, playerId=${tempGamePlayers[position].playerId}")

            val selectedIds = tempGamePlayers.mapIndexed { index, pair -> if (index != position && pair.playerId != 0) pair.playerId else null }.filterNotNull()
            val availablePlayers = tempPlayers.filter { it.id !in selectedIds }
            println("DEBUG: Available players: ${availablePlayers.map { it.name }}")

            val playerOptions = listOf("") + availablePlayers.map { it.name }
            val adapter = ArrayAdapter(binding.root.context, android.R.layout.simple_spinner_item, playerOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.playerSpinner.adapter = adapter
            binding.playerSpinner.isEnabled = true

            val currentPlayerId = tempGamePlayers[position].playerId
            val selectedIndex = if (currentPlayerId == 0) 0 else availablePlayers.indexOfFirst { it.id == currentPlayerId } + 1
            binding.playerSpinner.setSelection(selectedIndex, false)

            binding.scoreEditText.setText(if (tempGamePlayers[position].score > 0) tempGamePlayers[position].score.toString() else "")
            binding.scoreEditText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val score = s.toString().toIntOrNull() ?: 0
                    tempGamePlayers[position].score = score
                    println("DEBUG: Score updated for position $position: $score")
                }
            })

            binding.playerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    if (!isBinding && pos in playerOptions.indices) {
                        println("DEBUG: Spinner selected at position $position, selectedPos=$pos")
                        val oldPlayerId = tempGamePlayers[position].playerId
                        if (pos == 0) {
                            tempGamePlayers[position].playerId = 0
                            if (oldPlayerId != 0) tempPlayers.find { it.id == oldPlayerId }?.visible = true
                        } else if (availablePlayers.isNotEmpty() && pos - 1 in availablePlayers.indices) {
                            val selectedPlayer = availablePlayers[pos - 1]
                            tempGamePlayers[position].playerId = selectedPlayer.id
                            if (oldPlayerId != 0 && oldPlayerId != selectedPlayer.id) {
                                tempPlayers.find { it.id == oldPlayerId }?.visible = true
                            }
                            println("DEBUG: Player selected: ${selectedPlayer.name}, id=${selectedPlayer.id}")
                        }
                        // Убираем notifyItemChanged здесь, чтобы избежать циклических вызовов
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            binding.removeButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val removedPlayerId = tempGamePlayers[pos].playerId
                    tempGamePlayers.removeAt(pos)
                    if (removedPlayerId != 0) tempPlayers.find { it.id == removedPlayerId }?.visible = true
                    playerAdapter.notifyItemRemoved(pos)
                    playerAdapter.notifyItemRangeChanged(pos, playerAdapter.getItemCount())
                    println("DEBUG: Player removed at position $pos")
                }
            }
            isBinding = false
        }
    }
}