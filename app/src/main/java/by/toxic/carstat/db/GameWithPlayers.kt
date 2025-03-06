package by.toxic.carstat.db

import androidx.room.Embedded
import androidx.room.Relation

data class GameWithPlayers(
    @Embedded val game: Game,
    @Relation(
        parentColumn = "id",
        entityColumn = "gameId",
        entity = GamePlayer::class
    )
    val gamePlayers: List<GamePlayer> // Возвращаем List вместо ArrayList
)