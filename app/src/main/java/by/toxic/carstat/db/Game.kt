package by.toxic.carstat.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "games")
data class Game(
    @PrimaryKey val id: Int, // Убрано autoGenerate = true, теперь id задается вручную
    val date: String
) : Parcelable