package by.toxic.carstat

import android.app.DatePickerDialog
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.toxic.carstat.databinding.FragmentGamesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GamesFragment : Fragment() {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: GameAdapter
    private val viewModel: GameViewModel by viewModels()
    private lateinit var savedStateHandle: SavedStateHandle
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    companion object {
        private const val KEY_START_DATE = "start_date"
        private const val KEY_END_DATE = "end_date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateHandle = SavedStateHandle()
        if (savedInstanceState != null) {
            savedStateHandle.set(KEY_START_DATE, savedInstanceState.getString(KEY_START_DATE))
            savedStateHandle.set(KEY_END_DATE, savedInstanceState.getString(KEY_END_DATE))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = GameAdapter(
            fragment = this,
            viewModel = viewModel,
            onEdit = { gameWithPlayers ->
                val bundle = Bundle().apply {
                    putInt("gameId", gameWithPlayers.game.id)
                }
                findNavController().navigate(R.id.action_gamesFragment_to_viewGameFragment, bundle)
            },
            onDelete = { gameId ->
                viewModel.deleteGame(gameId)
            }
        )
        binding.gamesRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.gamesRecyclerView.adapter = adapter

        binding.gamesRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.bottom = 16
            }
        })

        binding.filterButton.setOnClickListener {
            showDateRangePicker()
        }

        binding.clearFilterButton.setOnClickListener {
            savedStateHandle.set(KEY_START_DATE, null)
            savedStateHandle.set(KEY_END_DATE, null)
            updateFilterInfo()
            applyFilter()
        }

        updateFilterInfo()
        applyFilter()
    }

    private fun showDateRangePicker() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, startYear, startMonth, startDay ->
                val startDate = Calendar.getInstance().apply {
                    set(startYear, startMonth, startDay)
                }.time
                val startDateStr = dateFormat.format(startDate)

                DatePickerDialog(
                    requireContext(),
                    { _, endYear, endMonth, endDay ->
                        val endDate = Calendar.getInstance().apply {
                            set(endYear, endMonth, endDay)
                        }.time
                        val endDateStr = dateFormat.format(endDate)

                        if (endDate.before(startDate)) {
                            val temp = startDateStr
                            savedStateHandle.set(KEY_START_DATE, endDateStr)
                            savedStateHandle.set(KEY_END_DATE, temp)
                        } else {
                            savedStateHandle.set(KEY_START_DATE, startDateStr)
                            savedStateHandle.set(KEY_END_DATE, endDateStr)
                        }
                        updateFilterInfo()
                        applyFilter()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setTitle(getString(R.string.select_date_range))
                    datePicker.maxDate = System.currentTimeMillis()
                    datePicker.minDate = startDate.time
                }.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle(getString(R.string.select_date_range))
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun updateFilterInfo() {
        val startDate = savedStateHandle.get<String>(KEY_START_DATE)
        val endDate = savedStateHandle.get<String>(KEY_END_DATE)
        if (startDate != null && endDate != null) {
            val startDisplay = viewModel.formatDateForDisplay(startDate)
            val endDisplay = viewModel.formatDateForDisplay(endDate)
            binding.dateRangeText.text = getString(R.string.date_range, startDisplay, endDisplay)
            binding.filterInfoLayout.visibility = View.VISIBLE
        } else {
            binding.filterInfoLayout.visibility = View.GONE
        }
    }

    private fun applyFilter() {
        val startDate = savedStateHandle.get<String>(KEY_START_DATE)
        val endDate = savedStateHandle.get<String>(KEY_END_DATE)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filterGamesByDateRange(startDate, endDate).collectLatest { games ->
                adapter.updateGames(games)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_START_DATE, savedStateHandle.get<String>(KEY_START_DATE))
        outState.putString(KEY_END_DATE, savedStateHandle.get<String>(KEY_END_DATE))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}