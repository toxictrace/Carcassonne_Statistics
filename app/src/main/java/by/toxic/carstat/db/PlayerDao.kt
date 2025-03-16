package by.toxic.carstat.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players")
    fun getAllPlayers(): Flow<List<Player>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: Player): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<Player>): List<Long>

    @Update
    suspend fun updatePlayer(player: Player)

    @Query("DELETE FROM players WHERE id = :playerId")
    suspend fun deletePlayer(playerId: Int)

    @Query("SELECT MAX(id) FROM players")
    suspend fun getMaxPlayerId(): Int?

    @Query("DELETE FROM players")
    suspend fun deleteAllPlayers()
}