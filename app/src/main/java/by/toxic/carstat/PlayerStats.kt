package by.toxic.carstat

import kotlin.math.sqrt

data class PlayerStats(
    val name: String,
    val wins: Int,
    val avgScore: Float,
    val totalGames: Int,
    val skill: Float, // Win percentage
    val experience: Int, // Number of games
    val stability: Float, // Standard deviation
    val scoreTrend: List<Int>, // Scores per game for trend
    val losses: Int, // Number of non-wins
    val lastPlaceCount: Int, // Number of last places
    val maxGap: Float, // Max gap from nearest rival
    val minGap: Float, // Min gap from nearest rival
    val gapsFromWinner: List<Float> // Gaps from winner for bar chart
)