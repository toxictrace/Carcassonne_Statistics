package by.toxic.carstat.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Transaction
    @Query("SELECT * FROM games ORDER BY date DESC") // Сортировка по убыванию даты
    fun getAllGamesWithPlayers(): Flow<List<GameWithPlayers>>

    @Insert
    suspend fun insertGame(game: Game): Long

    @Insert
    suspend fun insertGamePlayers(gamePlayers: List<GamePlayer>)

    @Update
    suspend fun updateGame(game: Game)

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun deleteGame(gameId: Int)

    @Query("DELETE FROM game_players WHERE gameId = :gameId")
    suspend fun deleteGamePlayers(gameId: Int)
}