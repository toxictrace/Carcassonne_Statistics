package by.toxic.carstat.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games")
    fun getAllGames(): Flow<List<Game>>

    @Transaction
    @Query("SELECT * FROM games")
    fun getAllGamesWithPlayers(): Flow<List<GameWithPlayers>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: Game): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<Game>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGamePlayers(gamePlayers: List<GamePlayer>)

    @Update
    suspend fun updateGame(game: Game)

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun deleteGame(gameId: Int)

    @Query("DELETE FROM game_players WHERE gameId = :gameId")
    suspend fun deleteGamePlayers(gameId: Int)

    @Query("SELECT MAX(id) FROM games")
    suspend fun getMaxGameId(): Int?

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()

    @Query("DELETE FROM game_players")
    suspend fun deleteAllGamePlayers()
}