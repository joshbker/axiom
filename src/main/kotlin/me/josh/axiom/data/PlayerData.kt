package me.josh.axiom.data

import java.util.Date

/**
 * Data class representing a player in the system.
 *
 * Maps to the 'players' MongoDB collection.
 * Contains authentication credentials and profile information.
 */
data class PlayerData(
    val id: String,
    val username: String,
    val passwordHash: String,
    val profile: me.josh.axiom.data.PlayerProfile,
    val stats: me.josh.axiom.data.PlayerStats
)

/**
 * Player profile information.
 */
data class PlayerProfile(
    val displayName: String,
    val dateOfBirth: Date? = null,
    val createdAt: Date = Date()
)

/**
 * Aggregated player statistics.
 */
data class PlayerStats(
    val totalKills: Int = 0,
    val totalDeaths: Int = 0,
    val totalPlayTime: Float = 0f,
    val highestScore: Int = 0
)

/**
 * Leaderboard entry representing a single game session score.
 *
 * Maps to the 'leaderboard_entries' MongoDB collection.
 */
data class LeaderboardEntry(
    val id: String,
    val playerId: String,
    val playerName: String,
    val score: Int,
    val kills: Int,
    val survivalTime: Float,
    val achievedAt: Date = Date()
)
