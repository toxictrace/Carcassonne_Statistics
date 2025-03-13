package by.toxic.carstat

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import by.toxic.carstat.databinding.FragmentSettingsBinding
import by.toxic.carstat.db.Game
import by.toxic.carstat.db.GamePlayer
import by.toxic.carstat.db.GameWithPlayers
import by.toxic.carstat.db.Player
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mainActivity: MainActivity
    private val gameViewModel: GameViewModel by viewModels({ requireActivity() })

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainActivity = context as MainActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_follow_system)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.themeSpinner.adapter = adapter

        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val savedTheme = sharedPref.getString("theme", getString(R.string.theme_follow_system))
        val themePosition = themes.indexOf(savedTheme)
        if (themePosition != -1) {
            binding.themeSpinner.setSelection(themePosition)
        }

        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTheme = when (position) {
                    0 -> getString(R.string.theme_light)
                    1 -> getString(R.string.theme_dark)
                    2 -> getString(R.string.theme_follow_system)
                    else -> getString(R.string.theme_follow_system)
                }
                sharedPref.edit().putString("theme", selectedTheme).apply()
                mainActivity.applyTheme(selectedTheme)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val isBackgroundEnabled = sharedPref.getBoolean("background_enabled", false)
        binding.backgroundSwitch.isChecked = isBackgroundEnabled
        mainActivity.enableCustomBackgrounds(isBackgroundEnabled)

        binding.backgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("background_enabled", isChecked).apply()
            mainActivity.enableCustomBackgrounds(isChecked)
        }

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        updateUI(account)

        binding.googleAccountText.setOnClickListener {
            val accountCheck = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (accountCheck == null) {
                mainActivity.signInWithGoogle()
            } else {
                mainActivity.signOutFromGoogle()
            }
        }
    }

    fun handleSignInResult(task: Task<GoogleSignInAccount>?) {
        if (task == null) {
            updateUI(null)
            return
        }
        try {
            val account = task.getResult(ApiException::class.java)
            updateUI(account)
        } catch (e: ApiException) {
            Log.w("SettingsFragment", "signInResult:failed code=" + e.statusCode)
            updateUI(null)
            if (isAdded) {
                Toast.makeText(requireContext(), getString(R.string.sign_in_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            binding.googleAccountText.text = getString(R.string.sign_out)
            binding.googleAccountText.visibility = View.VISIBLE
            binding.googleAvatar.visibility = View.VISIBLE
            binding.googleAccountName.visibility = View.VISIBLE
            binding.googleAccountName.text = account.displayName
            binding.dataOptionsLayout.visibility = View.VISIBLE
            account.photoUrl?.let { photoUrl ->
                Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(binding.googleAvatar)
            }
        } else {
            binding.googleAccountText.text = getString(R.string.sign_in_google)
            binding.googleAccountText.visibility = View.VISIBLE
            binding.googleAvatar.visibility = View.GONE
            binding.googleAccountName.visibility = View.GONE
            binding.dataOptionsLayout.visibility = View.GONE
        }

        binding.saveDataButton.setOnClickListener {
            saveDataToGoogleDrive(account)
        }
        binding.loadDataButton.setOnClickListener {
            loadDataFromGoogleDrive(account)
        }
    }

    private fun saveDataToGoogleDrive(account: GoogleSignInAccount?) {
        if (account == null) {
            if (isAdded) {
                Toast.makeText(requireContext(), getString(R.string.sign_in_first), Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (isAdded) {
            Toast.makeText(requireContext(), getString(R.string.saving_data), Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    requireContext(), listOf(DriveScopes.DRIVE_FILE)
                ).apply { selectedAccount = account.account!! }

                val driveService = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName(getString(R.string.app_name)).build()

                val (players, games) = withContext(Dispatchers.IO) {
                    val playersList: List<Player> = gameViewModel.getAllPlayersList().first()
                    val gamesList: List<GameWithPlayers> = gameViewModel.allGames.first()
                    Pair(playersList, gamesList)
                }

                val jsonObject = JSONObject().apply {
                    put("players", JSONArray().apply {
                        players.forEach { player: Player ->
                            put(JSONObject().apply {
                                put("id", player.id)
                                put("name", player.name)
                                put("frameId", player.frameId)
                            })
                        }
                    })
                    put("games", JSONArray().apply {
                        games.forEach { gameWithPlayers: GameWithPlayers ->
                            put(JSONObject().apply {
                                put("id", gameWithPlayers.game.id)
                                put("date", gameWithPlayers.game.date)
                                put("gamePlayers", JSONArray().apply {
                                    gameWithPlayers.gamePlayers.forEach { gamePlayer: GamePlayer ->
                                        put(JSONObject().apply {
                                            put("gameId", gamePlayer.gameId)
                                            put("playerId", gamePlayer.playerId)
                                            put("score", gamePlayer.score)
                                            put("color", gamePlayer.color)
                                        })
                                    }
                                })
                            })
                        }
                    })
                }

                val fileMetadata = File().apply {
                    name = "carcassonne_data.json"
                    mimeType = "application/json"
                }

                Log.d("SettingsFragment", "Attempting to list files created by this app")
                val fileListRequest = driveService.files().list()
                    .setSpaces("drive")
                    .setFields("files(id, name, trashed)")
                val existingFile = withContext(Dispatchers.IO) {
                    val files = fileListRequest.execute().files
                    Log.d("SettingsFragment", "Files found: ${files.size}, details: ${
                        files.joinToString { "name=${it.name}, id=${it.id}, trashed=${it.trashed}" }
                    }")
                    files.firstOrNull { it.name == "carcassonne_data.json" && it.trashed != true }
                }

                val content = ByteArrayContent.fromString("application/json", jsonObject.toString())
                withContext(Dispatchers.IO) {
                    if (existingFile != null) {
                        Log.d("SettingsFragment", "Updating existing file with ID: ${existingFile.id}")
                        driveService.files().update(existingFile.id, fileMetadata, content).execute()
                    } else {
                        Log.d("SettingsFragment", "Creating new file")
                        driveService.files().create(fileMetadata, content).execute()
                    }
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.data_saved_success), Toast.LENGTH_SHORT).show()
                    } else {
                        mainActivity.showToast(getString(R.string.data_saved_success))
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Failed to save data to Google Drive: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.data_save_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        mainActivity.showToast(getString(R.string.data_save_failed))
                    }
                }
            }
        }
    }

    private fun loadDataFromGoogleDrive(account: GoogleSignInAccount?) {
        if (account == null) {
            if (isAdded) {
                Toast.makeText(requireContext(), getString(R.string.sign_in_first), Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (isAdded) {
            Toast.makeText(requireContext(), getString(R.string.loading_data), Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    requireContext(), listOf(DriveScopes.DRIVE_FILE)
                ).apply { selectedAccount = account.account!! }

                val driveService = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName(getString(R.string.app_name)).build()

                Log.d("SettingsFragment", "Attempting to list files created by this app")
                val fileListRequest = driveService.files().list()
                    .setSpaces("drive")
                    .setFields("files(id, name, trashed)")
                val file = withContext(Dispatchers.IO) {
                    val files = fileListRequest.execute().files
                    Log.d("SettingsFragment", "Files found: ${files.size}, details: ${
                        files.joinToString { "name=${it.name}, id=${it.id}, trashed=${it.trashed}" }
                    }")
                    files.firstOrNull { it.name == "carcassonne_data.json" && it.trashed != true }
                }

                if (file == null) {
                    Log.d("SettingsFragment", "No file named 'carcassonne_data.json' found or it is trashed")
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), getString(R.string.no_data_file_found), Toast.LENGTH_SHORT).show()
                        } else {
                            mainActivity.showToast(getString(R.string.no_data_file_found))
                        }
                    }
                    return@launch
                }

                Log.d("SettingsFragment", "Found file with ID: ${file.id}, attempting to load content")
                val inputStream = withContext(Dispatchers.IO) {
                    driveService.files().get(file.id).executeMediaAsInputStream()
                }
                val jsonString = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                Log.d("SettingsFragment", "File content loaded: $jsonString")

                val jsonObject = JSONObject(jsonString)
                val playersArray = jsonObject.getJSONArray("players")
                val gamesArray = jsonObject.getJSONArray("games")

                val players = mutableListOf<Player>()
                for (i in 0 until playersArray.length()) {
                    val playerJson = playersArray.getJSONObject(i)
                    players.add(Player(
                        id = playerJson.getInt("id"),
                        name = playerJson.getString("name"),
                        frameId = playerJson.getInt("frameId")
                    ))
                }

                val games = mutableListOf<Game>()
                val gamePlayers = mutableListOf<GamePlayer>()
                for (i in 0 until gamesArray.length()) {
                    val gameJson = gamesArray.getJSONObject(i)
                    games.add(Game(
                        id = gameJson.getInt("id"),
                        date = gameJson.getString("date")
                    ))
                    val gamePlayersArray = gameJson.getJSONArray("gamePlayers")
                    for (j in 0 until gamePlayersArray.length()) {
                        val gpJson = gamePlayersArray.getJSONObject(j)
                        gamePlayers.add(GamePlayer(
                            gameId = gpJson.getInt("gameId"),
                            playerId = gpJson.getInt("playerId"),
                            score = gpJson.getInt("score"),
                            color = if (gpJson.has("color")) gpJson.getString("color") else null
                        ))
                    }
                }

                // Обновляем базу данных в фоновом потоке
                withContext(Dispatchers.IO) {
                    gameViewModel.insertData(players, games, gamePlayers)
                }

                // Показываем тост об успехе, остаёмся на Settings
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.data_loaded_success), Toast.LENGTH_SHORT).show()
                    } else {
                        mainActivity.showToast(getString(R.string.data_loaded_success))
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Failed to load data from Google Drive: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.data_load_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        mainActivity.showToast(getString(R.string.data_load_failed))
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}