package by.toxic.carstat.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "players")
data class Player(
    @PrimaryKey val id: Int, // Убрано autoGenerate = true, теперь id задается вручную
    val name: String,
    val frameId: Int = 1 // По умолчанию 1, будет перезаписываться случайным значением
) : Parcelable