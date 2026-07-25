package com.immu.thumbgrab

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.splashLogo)
        val title = findViewById<View>(R.id.splashTitle)
        val sub = findViewById<View>(R.id.splashSub)

        // Logo: pop-in with rotation
        logo.scaleX = 0f; logo.scaleY = 0f; logo.rotation = -25f
        logo.animate()
            .scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                // Gentle infinite pulse
                ObjectAnimator.ofPropertyValuesHolder(
                    logo,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f, 1f)
                ).apply {
                    duration = 1200
                    repeatCount = ObjectAnimator.INFINITE
                    start()
                }
            }
            .start()

        // Title + subtitle: fade up, staggered
        fadeUp(title, 350)
        fadeUp(sub, 550)

        // Move to main screen
        logo.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2300)
    }

    private fun fadeUp(v: View, delay: Long) {
        v.alpha = 0f
        v.translationY = 40f
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(delay).setDuration(600).start()
    }
}
