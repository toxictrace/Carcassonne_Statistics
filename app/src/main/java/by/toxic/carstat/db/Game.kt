package by.toxic.carstat.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "games")
data class Game(
    @PrimaryKey val id: Int,
    val date: String,
    val frameId: Int = 1 // Добавлено поле frameId с значением по умолчанию
) : Parcelable