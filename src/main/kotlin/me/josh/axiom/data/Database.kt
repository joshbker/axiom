package me.josh.axiom.data

import com.badlogic.gdx.Gdx
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ktx.async.AsyncExecutorDispatcher
import ktx.async.KtxAsync
import ktx.async.newSingleThreadAsyncContext
import org.bson.Document
import org.bson.types.ObjectId
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

/**
 * Database singleton for MongoDB operations.
 *
 * Design Pattern: Singleton (via Kotlin object)
 * - Manages single database connection
 * - Provides centralized data access methods
 * - All operations run on a dedicated IO thread to avoid blocking the game
 *
 * Handles:
 * - Player authentication (login/register)
 * - Player profile CRUD
 * - Leaderboard operations
 */
object Database {

    private const val CONNECTION_STRING = "mongodb://localhost:27017"
    private const val DATABASE_NAME = "axiom"

    private var mongoDatabase: MongoDatabase? = null

    // Dedicated thread for database operations
    private lateinit var dbContext: AsyncExecutorDispatcher
    private val activeJobs = mutableListOf<Job>()

    private val players: MongoCollection<Document>?
        get() = mongoDatabase?.getCollection("players")

    private val leaderboard: MongoCollection<Document>?
        get() = mongoDatabase?.getCollection("leaderboard_entries")

    /**
     * Initialize the async system. Call this in AxiomGame.create()
     */
    fun initialize() {
        dbContext = newSingleThreadAsyncContext("DatabaseThread")
        KtxAsync.initiate()
        Gdx.app.log("Database", "Async database system initialized")
    }

    /**
     * Shutdown and cancel all pending operations
     */
    fun shutdown() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        if (::dbContext.isInitialized) {
            dbContext.dispose()
        }
    }

    /**
     * Initialize database connection.
     * Called lazily on first database operation.
     */
    private fun ensureConnected() {
        if (mongoDatabase == null) {
            try {
                val client = MongoClients.create(CONNECTION_STRING)
                mongoDatabase = client.getDatabase(DATABASE_NAME)
                Gdx.app.log("Database", "Connected to MongoDB")
            } catch (e: Exception) {
                Gdx.app.error("Database", "Failed to connect to MongoDB", e)
                throw e
            }
        }
    }

    // ============================================
    // Async helpers
    // ============================================

    private fun launchAsync(block: suspend () -> Unit): Job {
        val job = CoroutineScope(dbContext).launch { block() }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
        return job
    }

    private fun onMainThread(block: () -> Unit) {
        Gdx.app.postRunnable(block)
    }

    // ============================================
    // Authentication (Async)
    // ============================================

    /**
     * Register a new player asynchronously.
     */
    fun registerPlayer(
        username: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        launchAsync {
            try {
                val success = registerPlayerSync(username, password)
                onMainThread {
                    if (success) onSuccess()
                    else onFailure("Username already exists")
                }
            } catch (e: Exception) {
                Gdx.app.error("Database", "Registration error", e)
                onMainThread { onFailure("Connection error: ${e.message}") }
            }
        }
    }

    private fun registerPlayerSync(username: String, password: String): Boolean {
        ensureConnected()

        val collection = players ?: return false

        // Check if username already exists
        val existing = collection.find(Filters.eq("username", username.lowercase())).first()
        if (existing != null) {
            return false
        }

        // Hash password
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

        // Create player document
        val playerDoc = Document()
            .append("username", username.lowercase())
            .append("passwordHash", passwordHash)
            .append("profile", Document()
                .append("displayName", username)
                .append("createdAt", Date())
            )
            .append("stats", Document()
                .append("totalKills", 0)
                .append("totalDeaths", 0)
                .append("totalPlayTime", 0.0)
                .append("highestScore", 0)
            )

        collection.insertOne(playerDoc)
        Gdx.app.log("Database", "Registered new player: $username")
        return true
    }

    /**
     * Authenticate a player asynchronously.
     */
    fun authenticatePlayer(
        username: String,
        password: String,
        onSuccess: (PlayerData) -> Unit,
        onFailure: (String) -> Unit
    ) {
        launchAsync {
            try {
                val player = authenticatePlayerSync(username, password)
                onMainThread {
                    if (player != null) onSuccess(player)
                    else onFailure("Invalid credentials")
                }
            } catch (e: Exception) {
                Gdx.app.error("Database", "Authentication error", e)
                onMainThread { onFailure("Connection error: ${e.message}") }
            }
        }
    }

    private fun authenticatePlayerSync(username: String, password: String): PlayerData? {
        ensureConnected()

        val collection = players ?: return null

        val doc = collection.find(Filters.eq("username", username.lowercase())).first()
            ?: return null

        val storedHash = doc.getString("passwordHash")
        if (!BCrypt.checkpw(password, storedHash)) {
            return null
        }

        return documentToPlayerData(doc)
    }

    /**
     * Get player by ID.
     */
    fun getPlayer(playerId: String): PlayerData? {
        ensureConnected()

        val collection = players ?: return null

        val doc = try {
            collection.find(Filters.eq("_id", ObjectId(playerId))).first()
        } catch (e: Exception) {
            null
        }

        return doc?.let { documentToPlayerData(it) }
    }

    /**
     * Update player statistics after a game.
     */
    private fun updatePlayerStats(playerId: String, kills: Int, survivalTime: Float, score: Int) {
        ensureConnected()

        val collection = players ?: return

        try {
            val objectId = ObjectId(playerId)
            collection.updateOne(
                Filters.eq("_id", objectId),
                Updates.combine(
                    Updates.inc("stats.totalKills", kills),
                    Updates.inc("stats.totalDeaths", 1),
                    Updates.inc("stats.totalPlayTime", survivalTime.toDouble()),
                    Updates.max("stats.highestScore", score)
                )
            )
        } catch (e: Exception) {
            Gdx.app.debug("Database", "Could not update stats for $playerId: ${e.message}")
        }
    }

    // ============================================
    // Leaderboard (Async)
    // ============================================

    /**
     * Save a score to the leaderboard asynchronously.
     * Fire-and-forget style - doesn't block game over screen.
     */
    fun saveScore(
        playerId: String,
        playerName: String,
        kills: Int,
        survivalTime: Float,
        score: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        launchAsync {
            try {
                saveScoreSync(playerId, playerName, kills, survivalTime, score)
                onComplete?.let { callback -> onMainThread { callback(true) } }
            } catch (e: Exception) {
                Gdx.app.error("Database", "Failed to save score", e)
                onComplete?.let { callback -> onMainThread { callback(false) } }
            }
        }
    }

    private fun saveScoreSync(playerId: String, playerName: String, kills: Int, survivalTime: Float, score: Int) {
        ensureConnected()

        val collection = leaderboard ?: return

        val entryDoc = Document()
            .append("playerId", playerId)
            .append("playerName", playerName)
            .append("score", score)
            .append("kills", kills)
            .append("survivalTime", survivalTime.toDouble())
            .append("achievedAt", Date())

        collection.insertOne(entryDoc)
        updatePlayerStats(playerId, kills, survivalTime, score)
        Gdx.app.log("Database", "Saved score: $playerName - $score")
    }

    /**
     * Get top scores from the leaderboard asynchronously.
     */
    fun getTopScores(
        limit: Int = 10,
        onSuccess: (List<LeaderboardEntry>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        launchAsync {
            try {
                val scores = getTopScoresSync(limit)
                onMainThread { onSuccess(scores) }
            } catch (e: Exception) {
                Gdx.app.error("Database", "Failed to load leaderboard", e)
                onMainThread { onFailure("Failed to load leaderboard") }
            }
        }
    }

    private fun getTopScoresSync(limit: Int): List<LeaderboardEntry> {
        ensureConnected()

        val collection = leaderboard ?: return emptyList()

        return collection
            .find()
            .sort(Sorts.descending("score"))
            .limit(limit)
            .map { documentToLeaderboardEntry(it) }
            .toList()
    }

    /**
     * Get a player's best scores asynchronously.
     */
    fun getPlayerScores(
        playerId: String,
        limit: Int = 10,
        onSuccess: (List<LeaderboardEntry>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        launchAsync {
            try {
                val scores = getPlayerScoresSync(playerId, limit)
                onMainThread { onSuccess(scores) }
            } catch (e: Exception) {
                Gdx.app.error("Database", "Failed to load player scores", e)
                onMainThread { onFailure("Failed to load scores") }
            }
        }
    }

    private fun getPlayerScoresSync(playerId: String, limit: Int): List<LeaderboardEntry> {
        ensureConnected()

        val collection = leaderboard ?: return emptyList()

        return collection
            .find(Filters.eq("playerId", playerId))
            .sort(Sorts.descending("score"))
            .limit(limit)
            .map { documentToLeaderboardEntry(it) }
            .toList()
    }

    // ============================================
    // Conversion helpers
    // ============================================

    private fun documentToPlayerData(doc: Document): PlayerData {
        val profileDoc = doc.get("profile", Document::class.java) ?: Document()
        val statsDoc = doc.get("stats", Document::class.java) ?: Document()

        return PlayerData(
            id = doc.getObjectId("_id").toString(),
            username = doc.getString("username") ?: "",
            passwordHash = doc.getString("passwordHash") ?: "",
            profile = PlayerProfile(
                displayName = profileDoc.getString("displayName") ?: "",
                dateOfBirth = profileDoc.getDate("dateOfBirth"),
                createdAt = profileDoc.getDate("createdAt") ?: Date()
            ),
            stats = PlayerStats(
                totalKills = statsDoc.getInteger("totalKills") ?: 0,
                totalDeaths = statsDoc.getInteger("totalDeaths") ?: 0,
                totalPlayTime = (statsDoc.getDouble("totalPlayTime") ?: 0.0).toFloat(),
                highestScore = statsDoc.getInteger("highestScore") ?: 0
            )
        )
    }

    private fun documentToLeaderboardEntry(doc: Document): LeaderboardEntry {
        return LeaderboardEntry(
            id = doc.getObjectId("_id")?.toString() ?: "",
            playerId = doc.getString("playerId") ?: "",
            playerName = doc.getString("playerName") ?: "Unknown",
            score = doc.getInteger("score") ?: 0,
            kills = doc.getInteger("kills") ?: 0,
            survivalTime = (doc.getDouble("survivalTime") ?: 0.0).toFloat(),
            achievedAt = doc.getDate("achievedAt") ?: Date()
        )
    }
}
