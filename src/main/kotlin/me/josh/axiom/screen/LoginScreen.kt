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
import me.josh.axiom.event.PlayerLoginEvent

/**
 * Login screen for player authentication.
 *
 * Provides simple text-based input for username and password.
 * Validates credentials against MongoDB database asynchronously.
 */
class LoginScreen(
    private val game: AxiomGame
) : Screen {

    private val layout = GlyphLayout()

    // Input state
    private var username = ""
    private var password = ""
    private var activeField = InputField.USERNAME
    private var errorMessage = ""
    private var successMessage = ""

    // UI state
    private var isRegistering = false
    private var isLoading = false

    private enum class InputField {
        USERNAME, PASSWORD
    }

    override fun show() {
        Gdx.input.inputProcessor = null
        errorMessage = ""
        successMessage = ""
        isLoading = false
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        if (!isLoading) {
            handleInput()
        }

        game.batch.begin()
        drawUI()
        game.batch.end()
    }

    private fun handleInput() {
        // Tab to switch fields
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            activeField = if (activeField == InputField.USERNAME) {
                InputField.PASSWORD
            } else {
                InputField.USERNAME
            }
        }

        // Enter to submit
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (isRegistering) {
                attemptRegister()
            } else {
                attemptLogin()
            }
        }

        // R to toggle register/login mode
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            isRegistering = !isRegistering
            errorMessage = ""
            successMessage = ""
        }

        handleTextInput()
    }

    private fun handleTextInput() {
        // Backspace
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            when (activeField) {
                InputField.USERNAME -> {
                    if (username.isNotEmpty()) username = username.dropLast(1)
                }
                InputField.PASSWORD -> {
                    if (password.isNotEmpty()) password = password.dropLast(1)
                }
            }
        }

        // Character input
        for (i in Input.Keys.A..Input.Keys.Z) {
            if (Gdx.input.isKeyJustPressed(i)) {
                val char = ('a' + (i - Input.Keys.A))
                appendToActiveField(char.toString())
            }
        }
        for (i in Input.Keys.NUM_0..Input.Keys.NUM_9) {
            if (Gdx.input.isKeyJustPressed(i)) {
                val char = ('0' + (i - Input.Keys.NUM_0))
                appendToActiveField(char.toString())
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            appendToActiveField(" ")
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD)) {
            appendToActiveField(".")
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) {
            appendToActiveField("-")
        }
    }

    private fun appendToActiveField(char: String) {
        when (activeField) {
            InputField.USERNAME -> {
                if (username.length < 20) username += char
            }
            InputField.PASSWORD -> {
                if (password.length < 30) password += char
            }
        }
    }

    private fun drawUI() {
        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        val centerX = screenWidth / 2

        // Title
        Fonts.title.color = Color.CYAN
        layout.setText(Fonts.title, "AXIOM")
        Fonts.title.draw(game.batch, "AXIOM", centerX - layout.width / 2, screenHeight - 100)

        // Mode indicator
        val mode = if (isRegistering) "REGISTER" else "LOGIN"
        Fonts.heading.color = Color.YELLOW
        layout.setText(Fonts.heading, mode)
        Fonts.heading.draw(game.batch, mode, centerX - layout.width / 2, screenHeight - 160)

        // Username field
        val usernameLabel = "Username: "
        val usernameDisplay = username + if (activeField == InputField.USERNAME && !isLoading) "_" else ""
        Fonts.body.color = if (activeField == InputField.USERNAME) Color.GREEN else Color.WHITE
        Fonts.body.draw(game.batch, usernameLabel + usernameDisplay, centerX - 180, screenHeight - 260)

        // Password field
        val passwordLabel = "Password: "
        val passwordDisplay = "*".repeat(password.length) + if (activeField == InputField.PASSWORD && !isLoading) "_" else ""
        Fonts.body.color = if (activeField == InputField.PASSWORD) Color.GREEN else Color.WHITE
        Fonts.body.draw(game.batch, passwordLabel + passwordDisplay, centerX - 180, screenHeight - 310)

        // Loading indicator
        if (isLoading) {
            Fonts.body.color = Color.CYAN
            layout.setText(Fonts.body, "Connecting...")
            Fonts.body.draw(game.batch, "Connecting...", centerX - layout.width / 2, screenHeight - 390)
        } else {
            // Error/Success message
            if (errorMessage.isNotEmpty()) {
                Fonts.body.color = Color.RED
                layout.setText(Fonts.body, errorMessage)
                Fonts.body.draw(game.batch, errorMessage, centerX - layout.width / 2, screenHeight - 390)
            }
            if (successMessage.isNotEmpty()) {
                Fonts.body.color = Color.GREEN
                layout.setText(Fonts.body, successMessage)
                Fonts.body.draw(game.batch, successMessage, centerX - layout.width / 2, screenHeight - 390)
            }
        }

        // Instructions
        Fonts.small.color = Color.GRAY
        layout.setText(Fonts.small, "[TAB] Switch field  [ENTER] Submit  [R] Toggle Register/Login")
        Fonts.small.draw(game.batch, "[TAB] Switch field  [ENTER] Submit  [R] Toggle Register/Login",
            centerX - layout.width / 2, screenHeight - 460)

        // Skip login hint
        Fonts.small.color = Color.DARK_GRAY
        Fonts.small.draw(game.batch, "[ESC] Skip login (dev mode)", centerX - 100, 50f)

        // Dev skip
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !isLoading) {
            game.currentPlayerId = "dev"
            game.currentPlayerName = "Developer"
            game.showMenu()
        }
    }

    private fun attemptLogin() {
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "Please enter username and password"
            return
        }

        isLoading = true
        errorMessage = ""
        successMessage = ""

        Database.authenticatePlayer(
            username = username,
            password = password,
            onSuccess = { player ->
                isLoading = false
                game.currentPlayerId = player.id
                game.currentPlayerName = player.username
                game.eventBus.emit(PlayerLoginEvent(player.id, player.username))
                successMessage = "Login successful!"
                game.showMenu()
            },
            onFailure = { error ->
                isLoading = false
                if (error.contains("Connection error")) {
                    errorMessage = "Connection error - using offline mode"
                    game.currentPlayerId = "offline_${username}"
                    game.currentPlayerName = username
                    game.showMenu()
                } else {
                    errorMessage = error
                }
            }
        )
    }

    private fun attemptRegister() {
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "Please enter username and password"
            return
        }

        if (username.length < 3) {
            errorMessage = "Username must be at least 3 characters"
            return
        }

        if (password.length < 4) {
            errorMessage = "Password must be at least 4 characters"
            return
        }

        isLoading = true
        errorMessage = ""
        successMessage = ""

        Database.registerPlayer(
            username = username,
            password = password,
            onSuccess = {
                isLoading = false
                successMessage = "Registration successful! Press ENTER to login."
                isRegistering = false
            },
            onFailure = { error ->
                isLoading = false
                errorMessage = error
            }
        )
    }

    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() {}
}
