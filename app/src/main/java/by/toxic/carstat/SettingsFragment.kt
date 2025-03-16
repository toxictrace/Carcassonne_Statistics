package by.toxic.carstat

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File as JavaIoFile

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mainActivity: MainActivity
    private val gameViewModel: GameViewModel by viewModels({ requireActivity() })
    private val BACKUP_FILE_NAME = "CarcassonneStatistics.backup"
    private var lastAccountState: Boolean = false

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

        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        lastAccountState = sharedPref.getBoolean("is_account_signed_in", false)

        setupThemeSpinner(sharedPref)

        val isBackgroundEnabled = sharedPref.getBoolean("background_enabled", false)
        binding.backgroundSwitch.isChecked = isBackgroundEnabled
        mainActivity.enableCustomBackgrounds(isBackgroundEnabled)

        binding.backgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("background_enabled", isChecked).apply()
            mainActivity.enableCustomBackgrounds(isChecked)
        }

        updateStorageOptions(sharedPref)

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        updateUI(account)

        binding.googleAccountText.setOnClickListener {
            Log.d("SettingsFragment", "Initiating Google sign-in")
            mainActivity.signInWithGoogle()
        }

        binding.googleSignOutButton.setOnClickListener {
            Log.d("SettingsFragment", "Initiating Google sign-out")
            mainActivity.signOutFromGoogle()
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        updateStorageOptions(sharedPref)
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        updateUI(account)
    }

    private fun setupThemeSpinner(sharedPref: android.content.SharedPreferences) {
        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_follow_system)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.themeSpinner.adapter = adapter

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
    }

    private fun updateStorageOptions(sharedPref: android.content.SharedPreferences) {
        val hasStoragePermission = mainActivity.hasStoragePermission()
        val isLocalSaveEnabled = sharedPref.getBoolean("local_save_enabled", false)

        if (hasStoragePermission) {
            binding.localSaveSwitch.visibility = View.VISIBLE
            binding.localSaveLabel.visibility = View.VISIBLE
            binding.saveDataButton.visibility = View.VISIBLE
            binding.loadDataButton.visibility = View.VISIBLE
            binding.localSaveSwitch.isChecked = isLocalSaveEnabled

            binding.localSaveSwitch.setOnCheckedChangeListener { _, isChecked ->
                sharedPref.edit().putBoolean("local_save_enabled", isChecked).apply()
                Log.d("SettingsFragment", "Local save switch changed to: $isChecked")
            }
        } else {
            binding.localSaveSwitch.visibility = View.GONE
            binding.localSaveLabel.visibility = View.GONE
            binding.saveDataButton.visibility = View.GONE
            binding.loadDataButton.visibility = View.GONE
        }
        Log.d("SettingsFragment", "Storage options updated: hasStoragePermission = $hasStoragePermission, isLocalSaveEnabled = $isLocalSaveEnabled")
    }

    fun handleSignInResult(task: Task<GoogleSignInAccount>?) {
        if (task == null) {
            Log.d("SettingsFragment", "Handle sign-in result: Task is null, treating as sign-out")
            updateUI(null)
            return
        }
        try {
            val account = task.getResult(ApiException::class.java)
            Log.d("SettingsFragment", "Sign-in successful for account: ${account.displayName}")
            updateUI(account)
        } catch (e: ApiException) {
            Log.w("SettingsFragment", "Sign-in failed with code: ${e.statusCode}")
            updateUI(null)
            if (isAdded) {
                Toast.makeText(requireContext(), getString(R.string.sign_in_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val hasStoragePermission = mainActivity.hasStoragePermission()
        val isLocalSaveEnabled = sharedPref.getBoolean("local_save_enabled", false)

        val isAccountSignedIn = account != null
        sharedPref.edit().putBoolean("is_account_signed_in", isAccountSignedIn).apply()

        if (account != null) {
            binding.googleAccountText.visibility = View.GONE
            binding.googleAvatar.visibility = View.VISIBLE
            binding.googleAccountName.visibility = View.VISIBLE
            binding.googleSignOutButton.visibility = View.VISIBLE
            binding.dataOptionsLayout.visibility = View.VISIBLE
            binding.googleAccountName.text = account.displayName
            account.photoUrl?.let { photoUrl ->
                Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(binding.googleAvatar)
            }
            if (hasStoragePermission) {
                binding.localSaveSwitch.isEnabled = true
                if (!lastAccountState && isAccountSignedIn) {
                    binding.localSaveSwitch.isChecked = false
                    sharedPref.edit().putBoolean("local_save_enabled", false).apply()
                    Log.d("SettingsFragment", "Google account signed in, local save set to false")
                } else {
                    binding.localSaveSwitch.isChecked = isLocalSaveEnabled
                }
            }
        } else {
            binding.googleAccountText.visibility = View.VISIBLE
            binding.googleAvatar.visibility = View.GONE
            binding.googleAccountName.visibility = View.GONE
            binding.googleSignOutButton.visibility = View.GONE
            binding.dataOptionsLayout.visibility = View.VISIBLE

            if (hasStoragePermission) {
                if (lastAccountState && !isAccountSignedIn) {
                    binding.localSaveSwitch.isChecked = true
                    binding.localSaveSwitch.isEnabled = false
                    sharedPref.edit().putBoolean("local_save_enabled", true).apply()
                    Log.d("SettingsFragment", "Google account signed out, local save forced to true")
                } else {
                    binding.localSaveSwitch.isChecked = true
                    binding.localSaveSwitch.isEnabled = false
                    sharedPref.edit().putBoolean("local_save_enabled", true).apply()
                }
            }
        }

        lastAccountState = isAccountSignedIn

        if (hasStoragePermission) {
            binding.saveDataButton.setOnClickListener {
                if (binding.localSaveSwitch.isChecked) {
                    saveDataLocally()
                } else {
                    saveDataToGoogleDrive(account)
                }
            }
            binding.loadDataButton.setOnClickListener {
                if (binding.localSaveSwitch.isChecked) {
                    loadDataLocally()
                } else {
                    loadDataFromGoogleDrive(account)
                }
            }
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
                                            put("color", gamePlayer.color ?: "")
                                        })
                                    }
                                })
                            })
                        }
                    })
                }

                val jsonString = jsonObject.toString()
                val encodedData = Base64.encodeToString(jsonString.toByteArray(), Base64.DEFAULT)
                Log.d("SettingsFragment", "Data encoded to Base64: $encodedData")

                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = BACKUP_FILE_NAME
                    mimeType = "application/octet-stream"
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
                    files.firstOrNull { it.name == BACKUP_FILE_NAME && it.trashed != true }
                }

                val content = ByteArrayContent.fromString("application/octet-stream", encodedData)
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

    private fun saveDataLocally() {
        if (isAdded) {
            Toast.makeText(requireContext(), getString(R.string.saving_data_locally), Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            try {
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
                                            put("color", gamePlayer.color ?: "")
                                        })
                                    }
                                })
                            })
                        }
                    })
                }

                val jsonString = jsonObject.toString()
                val encodedData = Base64.encodeToString(jsonString.toByteArray(), Base64.DEFAULT)
                Log.d("SettingsFragment", "Data encoded to Base64 for local save: $encodedData")

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val backupFile = JavaIoFile(downloadsDir, BACKUP_FILE_NAME)
                withContext(Dispatchers.IO) {
                    backupFile.writeText(encodedData)
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.data_saved_locally_success), Toast.LENGTH_SHORT).show()
                    } else {
                        mainActivity.showToast(getString(R.string.data_saved_locally_success))
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Failed to save data locally: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.data_save_locally_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        mainActivity.showToast(getString(R.string.data_save_locally_failed))
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

                val fileListRequest = driveService.files().list()
                    .setSpaces("drive")
                    .setFields("files(id, name, trashed)")
                val existingFile = withContext(Dispatchers.IO) {
                    val files = fileListRequest.execute().files
                    Log.d("SettingsFragment", "Files found: ${files.size}, details: ${
                        files.joinToString { "name=${it.name}, id=${it.id}, trashed=${it.trashed}" }
                    }")
                    files.firstOrNull { it.name == BACKUP_FILE_NAME && it.trashed != true }
                }

                if (existingFile == null) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), getString(R.string.no_data_file_found), Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                val encodedData = withContext(Dispatchers.IO) {
                    val inputStream = driveService.files().get(existingFile.id).executeMediaAsInputStream()
                    inputStream.bufferedReader().use { it.readText() }
                }
                val jsonString = String(Base64.decode(encodedData, Base64.DEFAULT))
                val jsonObject = JSONObject(jsonString)

                val playersArray = jsonObject.getJSONArray("players")
                val gamesArray = jsonObject.getJSONArray("games")

                val players = mutableListOf<Player>()
                val playerIds = mutableSetOf<Int>()
                for (i in 0 until playersArray.length()) {
                    val playerJson = playersArray.getJSONObject(i)
                    val id = playerJson.getInt("id")
                    players.add(
                        Player(
                            id = id,
                            name = playerJson.getString("name"),
                            frameId = playerJson.getInt("frameId")
                        )
                    )
                    playerIds.add(id)
                }

                val games = mutableListOf<Game>()
                val gameIds = mutableSetOf<Int>()
                for (i in 0 until gamesArray.length()) {
                    val gameJson = gamesArray.getJSONObject(i)
                    val id = gameJson.getInt("id")
                    games.add(
                        Game(
                            id = id,
                            date = gameJson.getString("date")
                        )
                    )
                    gameIds.add(id)
                }

                val gamePlayers = mutableListOf<GamePlayer>()
                for (i in 0 until gamesArray.length()) {
                    val gameJson = gamesArray.getJSONObject(i)
                    val gamePlayersArray = gameJson.getJSONArray("gamePlayers")
                    for (j in 0 until gamePlayersArray.length()) {
                        val gpJson = gamePlayersArray.getJSONObject(j)
                        val gameId = gpJson.getInt("gameId")
                        val playerId = gpJson.getInt("playerId")
                        if (gameIds.contains(gameId) && playerIds.contains(playerId)) {
                            gamePlayers.add(
                                GamePlayer(
                                    gameId = gameId,
                                    playerId = playerId,
                                    score = gpJson.getInt("score"),
                                    color = gpJson.getString("color").ifEmpty { null }
                                )
                            )
                        } else {
                            Log.w("SettingsFragment", "Skipping invalid GamePlayer: gameId=$gameId, playerId=$playerId not found in games or players")
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    gameViewModel.insertData(players, games, gamePlayers)
                }

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

    private fun loadDataLocally() {
        if (isAdded) {
            Toast.makeText(requireContext(), getString(R.string.loading_data_locally), Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val backupFile = JavaIoFile(downloadsDir, BACKUP_FILE_NAME)
                if (!backupFile.exists()) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), getString(R.string.no_local_data_file_found), Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                val encodedData = withContext(Dispatchers.IO) {
                    backupFile.readText()
                }
                val jsonString = String(Base64.decode(encodedData, Base64.DEFAULT))
                val jsonObject = JSONObject(jsonString)

                val playersArray = jsonObject.getJSONArray("players")
                val gamesArray = jsonObject.getJSONArray("games")

                val players = mutableListOf<Player>()
                for (i in 0 until playersArray.length()) {
                    val playerJson = playersArray.getJSONObject(i)
                    players.add(
                        Player(
                            id = playerJson.getInt("id"),
                            name = playerJson.getString("name"),
                            frameId = playerJson.getInt("frameId")
                        )
                    )
                }

                val games = mutableListOf<Game>()
                val gamePlayers = mutableListOf<GamePlayer>()
                for (i in 0 until gamesArray.length()) {
                    val gameJson = gamesArray.getJSONObject(i)
                    games.add(
                        Game(
                            id = gameJson.getInt("id"),
                            date = gameJson.getString("date")
                        )
                    )
                    val gamePlayersArray = gameJson.getJSONArray("gamePlayers")
                    for (j in 0 until gamePlayersArray.length()) {
                        val gpJson = gamePlayersArray.getJSONObject(j)
                        gamePlayers.add(
                            GamePlayer(
                                gameId = gpJson.getInt("gameId"),
                                playerId = gpJson.getInt("playerId"),
                                score = gpJson.getInt("score"),
                                color = gpJson.getString("color").ifEmpty { null }
                            )
                        )
                    }
                }

                withContext(Dispatchers.IO) {
                    gameViewModel.insertData(players, games, gamePlayers)
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.data_loaded_locally_success), Toast.LENGTH_SHORT).show()
                    } else {
                        mainActivity.showToast(getString(R.string.data_loaded_locally_success))
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Failed to load data locally: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.data_load_locally_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        mainActivity.showToast(getString(R.string.data_load_locally_failed))
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