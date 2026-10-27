package fr.baptiste.bank.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room ne sait stocker que des types simples. Les dates sont enregistrées en nombre
 * de jours depuis le 1er janvier 1970 : la valeur reste triable et comparable en SQL.
 * Les enums sont gérées nativement par Room (stockées par leur nom).
 */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
