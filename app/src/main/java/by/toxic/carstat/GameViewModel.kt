package by.toxic.carstat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import by.toxic.carstat.db.AppDatabase
import by.toxic.carstat.db.Game
import by.toxic.carstat.db.GamePlayer
import by.toxic.carstat.db.GameWithPlayers
import by.toxic.carstat.db.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "carcassonne_database"
    ).fallbackToDestructiveMigration()
        .build()

    val allGames: Flow<List<GameWithPlayers>> = db.gameDao().getAllGamesWithPlayers()
    val allPlayers: Flow<List<Player>> = db.playerDao().getAllPlayers()

    fun addGame(date: String, playerNames: List<String>) {
        viewModelScope.launch {
            try {
                val formattedDate = formatDateForStorage(date)
                Log.d("GameViewModel", "Adding game with date: $formattedDate")
                val gameId = db.gameDao().insertGame(Game(date = formattedDate))
                val players = playerNames.map { Player(name = it) }
                val playerIds = db.playerDao().insertPlayers(players)
                val gamePlayers = playerIds.mapIndexed { index, playerId ->
                    GamePlayer(gameId.toInt(), playerId.toInt(), 0)
                }
                db.gameDao().insertGamePlayers(gamePlayers)
                Log.d("GameViewModel", "Game added successfully with ID: $gameId")
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to add game: ${e.message}", e)
            }
        }
    }

    suspend fun doesPlayerExist(name: String): Boolean {
        return try {
            db.playerDao().getAllPlayers().first().any { it.name == name } // Изменено с equals(name, ignoreCase = true) на ==
        } catch (e: Exception) {
            Log.e("GameViewModel", "Error checking player existence: ${e.message}", e)
            false
        }
    }

    fun getAllPlayersList(): Flow<List<Player>> = allPlayers

    fun addPlayer(context: Context, name: String, frameId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            if (doesPlayerExist(name)) {
                onError(context.getString(R.string.player_exists_error, name))
            } else {
                try {
                    db.playerDao().insertPlayer(Player(name = name, frameId = frameId))
                    Log.d("GameViewModel", "Player '$name' with frameId '$frameId' added successfully")
                    onSuccess()
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Failed to add player '$name': ${e.message}", e)
                    onError("Failed to add player: ${e.message}")
                }
            }
        }
    }

    fun saveGame(
        date: String,
        players: List<Triple<Int, Int, String?>>,
        gameId: Int? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val formattedDate = formatDateForStorage(date)
                Log.d("GameViewModel", "Saving game with date: $formattedDate, gameId: $gameId, players: $players")

                if (players.isEmpty()) {
                    onError("No players selected")
                    return@launch
                }

                if (players.any { it.first == 0 }) {
                    onError("All players must be selected")
                    return@launch
                }

                if (players.any { it.third == null && it.first != 0 }) {
                    onError("All players must have a color selected")
                    return@launch
                }

                val playerIds = players.map { it.first }
                if (playerIds.distinct().size != playerIds.size) {
                    onError("Duplicate players are not allowed")
                    return@launch
                }

                if (gameId != null) {
                    db.gameDao().updateGame(Game(id = gameId, date = formattedDate))
                    db.gameDao().deleteGamePlayers(gameId)
                    val gamePlayers = players.map { (playerId, score, color) ->
                        GamePlayer(gameId, playerId, score, color)
                    }
                    db.gameDao().insertGamePlayers(gamePlayers)
                    Log.d("GameViewModel", "Game updated successfully with ID: $gameId")
                } else {
                    val newGameId = db.gameDao().insertGame(Game(date = formattedDate))
                    val gamePlayers = players.map { (playerId, score, color) ->
                        GamePlayer(newGameId.toInt(), playerId, score, color)
                    }
                    db.gameDao().insertGamePlayers(gamePlayers)
                    Log.d("GameViewModel", "New game saved with ID: $newGameId")
                }
                onSuccess()
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to save game: ${e.message}", e)
                onError("Failed to save game: ${e.message}")
            }
        }
    }

    fun updateGame(game: Game) {
        viewModelScope.launch {
            try {
                val formattedDate = formatDateForStorage(game.date)
                db.gameDao().updateGame(game.copy(date = formattedDate))
                Log.d("GameViewModel", "Game updated with ID: ${game.id}")
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to update game with ID ${game.id}: ${e.message}", e)
            }
        }
    }

    fun deleteGame(gameId: Int) {
        viewModelScope.launch {
            try {
                Log.d("GameViewModel", "Attempting to delete game with ID: $gameId")
                db.gameDao().deleteGame(gameId)
                Log.d("GameViewModel", "Game with ID $gameId deleted successfully")
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to delete game with ID $gameId: ${e.message}", e)
            }
        }
    }

    fun deletePlayer(context: Context, playerId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("GameViewModel", "Attempting to delete player with ID: $playerId")
                db.playerDao().deletePlayer(playerId)
                Log.d("GameViewModel", "Player with ID $playerId deleted successfully")
                onSuccess()
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to delete player with ID $playerId: ${e.message}", e)
                onError(context.getString(R.string.delete_player_error, e.message))
            }
        }
    }

    suspend fun updatePlayerName(playerId: Int, newName: String) {
        try {
            val player = db.playerDao().getAllPlayers().first().find { it.id == playerId }
            if (player != null) {
                db.playerDao().updatePlayer(player.copy(name = newName))
                Log.d("GameViewModel", "Player $playerId name updated to $newName in DB")
            } else {
                Log.w("GameViewModel", "Player $playerId not found for update")
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "Failed to update player name for ID $playerId: ${e.message}", e)
            throw e
        }
    }

    private fun formatDateForStorage(date: String): String {
        return try {
            val parsedDate = if (date.contains(".")) displayDateFormat.parse(date) else dateFormat.parse(date)
            dateFormat.format(parsedDate ?: Date())
        } catch (e: Exception) {
            Log.w("GameViewModel", "Date parsing failed for $date, using current date: ${e.message}")
            dateFormat.format(Date())
        }
    }

    fun formatDateForDisplay(date: String): String {
        return try {
            val parsedDate = dateFormat.parse(date)
            displayDateFormat.format(parsedDate ?: Date())
        } catch (e: Exception) {
            Log.w("GameViewModel", "Display date parsing failed for $date, using current date: ${e.message}")
            displayDateFormat.format(Date())
        }
    }

    suspend fun deleteAllData() {
        db.playerDao().getAllPlayers().first().forEach { player ->
            db.playerDao().deletePlayer(player.id)
        }
        db.gameDao().getAllGamesWithPlayers().first().forEach { game ->
            db.gameDao().deleteGame(game.game.id)
        }
    }

    suspend fun insertData(players: List<Player>, games: List<Game>, gamePlayers: List<GamePlayer>) {
        db.clearAllTables() // Очистка всех таблиц
        db.playerDao().insertPlayers(players)
        games.forEach { game ->
            val gameId = db.gameDao().insertGame(game)
            val updatedGamePlayers = gamePlayers.filter { it.gameId == game.id }.map {
                it.copy(gameId = gameId.toInt())
            }
            db.gameDao().insertGamePlayers(updatedGamePlayers)
        }
    }
}