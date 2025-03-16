package by.toxic.carstat.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "game_players",
    primaryKeys = ["gameId", "playerId"],
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"]
        )
    ],
    indices = [Index(value = ["playerId"])]
)
data class GamePlayer(
    var gameId: Int,
    var playerId: Int,
    var score: Int,
    var color: String? = null
)