package by.toxic.carstat.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Game::class, Player::class, GamePlayer::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun playerDao(): PlayerDao
}