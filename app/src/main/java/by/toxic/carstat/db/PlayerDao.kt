package by.toxic.carstat.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players")
    fun getAllPlayers(): Flow<List<Player>>

    @Query("SELECT * FROM players")
    suspend fun getAllPlayersList(): List<Player>

    @Insert
    suspend fun insertPlayer(player: Player): Long

    @Insert
    suspend fun insertPlayers(players: List<Player>): List<Long>

    @Update
    suspend fun updatePlayer(player: Player)

    @Query("DELETE FROM players WHERE id = :playerId")
    suspend fun deletePlayer(playerId: Int)
}