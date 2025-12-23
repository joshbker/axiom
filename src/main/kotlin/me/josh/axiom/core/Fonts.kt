package me.josh.axiom.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter

/**
 * Centralized font management using FreeType for crisp text rendering.
 *
 * Generates bitmap fonts from TTF at runtime at exact sizes needed,
 * avoiding the blurry scaling of default BitmapFont.
 */
object Fonts {

    lateinit var title: BitmapFont
        private set
    lateinit var heading: BitmapFont
        private set
    lateinit var body: BitmapFont
        private set
    lateinit var small: BitmapFont
        private set
    lateinit var ui: BitmapFont
        private set

    private var generator: FreeTypeFontGenerator? = null

    fun initialize() {
        generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/roboto.ttf"))

        val baseParam = FreeTypeFontParameter().apply {
            color = Color.WHITE
            borderWidth = 1f
            borderColor = Color(0f, 0f, 0f, 0.5f)
            shadowOffsetX = 1
            shadowOffsetY = 1
            shadowColor = Color(0f, 0f, 0f, 0.4f)
        }

        title = generator!!.generateFont(FreeTypeFontParameter().apply {
            size = 48
            color = Color.WHITE
            borderWidth = 2f
            borderColor = Color(0f, 0f, 0f, 0.7f)
        })

        heading = generator!!.generateFont(FreeTypeFontParameter().apply {
            size = 32
            color = Color.WHITE
            borderWidth = 1.5f
            borderColor = Color(0f, 0f, 0f, 0.6f)
        })

        body = generator!!.generateFont(FreeTypeFontParameter().apply {
            size = 20
            color = Color.WHITE
            borderWidth = 1f
            borderColor = Color(0f, 0f, 0f, 0.5f)
        })

        small = generator!!.generateFont(FreeTypeFontParameter().apply {
            size = 16
            color = Color.WHITE
            borderWidth = 0.5f
            borderColor = Color(0f, 0f, 0f, 0.4f)
        })

        ui = generator!!.generateFont(FreeTypeFontParameter().apply {
            size = 18
            color = Color.WHITE
            borderWidth = 1f
            borderColor = Color(0f, 0f, 0f, 0.5f)
        })

        Gdx.app.log("Fonts", "Fonts initialized")
    }

    fun dispose() {
        title.dispose()
        heading.dispose()
        body.dispose()
        small.dispose()
        ui.dispose()
        generator?.dispose()
    }
}
