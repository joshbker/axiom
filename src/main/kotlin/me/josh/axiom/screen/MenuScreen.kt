package me.josh.axiom.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import me.josh.axiom.core.AxiomGame
import me.josh.axiom.core.Fonts
import me.josh.axiom.data.Database
import me.josh.axiom.data.LeaderboardEntry

/**
 * Main menu screen with navigation options.
 *
 * Provides access to:
 * - Start new game
 * - View leaderboard (loaded asynchronously)
 * - Logout
 */
class MenuScreen(
    private val game: AxiomGame
) : Screen {

    private val layout = GlyphLayout()

    private var selectedOption = 0
    private val menuOptions = listOf("Play Game", "Leaderboard", "Logout")

    // Leaderboard display
    private var showingLeaderboard = false
    private var leaderboardEntries: List<LeaderboardEntry> = emptyList()
    private var isLoadingLeaderboard = false
    private var leaderboardError = ""

    override fun show() {
        showingLeaderboard = false
        isLoadingLeaderboard = false
        leaderboardError = ""
    }

    private fun loadLeaderboard() {
        if (isLoadingLeaderboard) return

        isLoadingLeaderboard = true
        leaderboardError = ""

        Database.getTopScores(
            limit = 10,
            onSuccess = { scores ->
                leaderboardEntries = scores
                isLoadingLeaderboard = false
            },
            onFailure = { error ->
                leaderboardError = error
                isLoadingLeaderboard = false
            }
        )
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        handleInput()

        game.batch.begin()
        if (showingLeaderboard) {
            drawLeaderboard()
        } else {
            drawMenu()
        }
        game.batch.end()
    }

    private fun handleInput() {
        if (showingLeaderboard) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
                Gdx.input.isKeyJustPressed(Input.Keys.ENTER) ||
                Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
                showingLeaderboard = false
            }
            return
        }

        // Menu navigation
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            selectedOption = (selectedOption - 1 + menuOptions.size) % menuOptions.size
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            selectedOption = (selectedOption + 1) % menuOptions.size
        }

        // Selection
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            when (selectedOption) {
                0 -> game.startGame()
                1 -> {
                    showingLeaderboard = true
                    loadLeaderboard()
                }
                2 -> game.logout()
            }
        }
    }

    private fun drawMenu() {
        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        val centerX = screenWidth / 2

        // Title
        Fonts.title.color = Color.CYAN
        layout.setText(Fonts.title, "AXIOM")
        Fonts.title.draw(game.batch, "AXIOM", centerX - layout.width / 2, screenHeight - 100)

        // Welcome message
        Fonts.body.color = Color.LIGHT_GRAY
        val welcome = "Welcome, ${game.currentPlayerName ?: "Player"}!"
        layout.setText(Fonts.body, welcome)
        Fonts.body.draw(game.batch, welcome, centerX - layout.width / 2, screenHeight - 160)

        // Menu options
        var yPos = screenHeight - 280

        for ((index, option) in menuOptions.withIndex()) {
            Fonts.heading.color = if (index == selectedOption) Color.YELLOW else Color.WHITE
            val prefix = if (index == selectedOption) "> " else "  "
            layout.setText(Fonts.heading, prefix + option)
            Fonts.heading.draw(game.batch, prefix + option, centerX - layout.width / 2, yPos)
            yPos -= 60
        }

        // Instructions
        Fonts.small.color = Color.GRAY
        layout.setText(Fonts.small, "[W/S or UP/DOWN] Navigate  [ENTER] Select")
        Fonts.small.draw(game.batch, "[W/S or UP/DOWN] Navigate  [ENTER] Select", centerX - layout.width / 2, 80f)
    }

    private fun drawLeaderboard() {
        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        val centerX = screenWidth / 2

        // Title
        Fonts.title.color = Color.GOLD
        layout.setText(Fonts.title, "LEADERBOARD")
        Fonts.title.draw(game.batch, "LEADERBOARD", centerX - layout.width / 2, screenHeight - 80)

        // Loading state
        if (isLoadingLeaderboard) {
            Fonts.body.color = Color.CYAN
            Fonts.body.draw(game.batch, "Loading...", centerX - 50, screenHeight - 200)
        } else if (leaderboardError.isNotEmpty()) {
            Fonts.body.color = Color.RED
            Fonts.body.draw(game.batch, leaderboardError, centerX - 100, screenHeight - 200)
        } else {
            // Column headers
            Fonts.ui.color = Color.LIGHT_GRAY
            Fonts.ui.draw(game.batch, "RANK", centerX - 280, screenHeight - 150)
            Fonts.ui.draw(game.batch, "PLAYER", centerX - 180, screenHeight - 150)
            Fonts.ui.draw(game.batch, "KILLS", centerX + 60, screenHeight - 150)
            Fonts.ui.draw(game.batch, "SCORE", centerX + 160, screenHeight - 150)

            // Entries
            var yPos = screenHeight - 190

            if (leaderboardEntries.isEmpty()) {
                Fonts.body.color = Color.GRAY
                Fonts.body.draw(game.batch, "No scores yet - be the first!", centerX - 130, yPos)
            } else {
                for ((index, entry) in leaderboardEntries.withIndex()) {
                    Fonts.ui.color = when (index) {
                        0 -> Color.GOLD
                        1 -> Color.LIGHT_GRAY
                        2 -> Color(0.8f, 0.5f, 0.2f, 1f) // Bronze
                        else -> Color.WHITE
                    }

                    val rank = "${index + 1}."
                    Fonts.ui.draw(game.batch, rank, centerX - 280, yPos)
                    Fonts.ui.draw(game.batch, entry.playerName, centerX - 180, yPos)
                    Fonts.ui.draw(game.batch, entry.kills.toString(), centerX + 60, yPos)
                    Fonts.ui.draw(game.batch, entry.score.toString(), centerX + 160, yPos)

                    yPos -= 35
                }
            }
        }

        // Back instruction
        Fonts.small.color = Color.GRAY
        layout.setText(Fonts.small, "[ESC] or [ENTER] Back to menu")
        Fonts.small.draw(game.batch, "[ESC] or [ENTER] Back to menu", centerX - layout.width / 2, 50f)
    }

    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() {}
}
