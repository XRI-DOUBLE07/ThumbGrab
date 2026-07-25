package com.immu.thumbgrab

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.splashLogo)
        val ring = findViewById<View>(R.id.ring)
        val glow = findViewById<View>(R.id.glow)
        val title = findViewById<TextView>(R.id.splashTitle)

        // Neon text glow on title
        title.setShadowLayer(24f, 0f, 0f, Color.parseColor("#FF2D55"))

        // Logo pop-in
        logo.scaleX = 0f; logo.scaleY = 0f
        logo.animate().scaleX(1f).scaleY(1f)
            .setDuration(650)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()

        // Glow breathing
        glow.alpha = 0f
        glow.animate().alpha(1f).setDuration(650).withEndAction {
            ObjectAnimator.ofFloat(glow, View.ALPHA, 1f, 0.45f, 1f).apply {
                duration = 1400
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }.start()

        // Expanding ring loop
        ring.alpha = 0f
        ring.postDelayed({
            ObjectAnimator.ofPropertyValuesHolder(
                ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 2.6f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 2.6f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0f)
            ).apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }, 700)

        // Title: neon flicker in
        title.alpha = 0f
        title.postDelayed({
            ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f, 0.3f, 1f, 0.5f, 1f).apply {
                duration = 900
                start()
            }
        }, 500)

        logo.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2500)
    }
}
