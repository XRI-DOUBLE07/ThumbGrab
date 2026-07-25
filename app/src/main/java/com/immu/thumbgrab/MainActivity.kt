package com.immu.thumbgrab

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

class MainActivity : AppCompatActivity() {

    data class Quality(val key: String, val name: String, val size: String, val badge: String)

    private val qualities = listOf(
        Quality("maxresdefault", "Max-Res HD", "1280 × 720+", "HD"),
        Quality("sddefault", "Standard", "640 × 480", "480p"),
        Quality("hqdefault", "High", "480 × 360", "360p"),
        Quality("mqdefault", "Medium", "320 × 180", "180p"),
        Quality("default", "Preview", "120 × 90", "90p")
    )

    private lateinit var input: EditText
    private lateinit var list: LinearLayout
    private lateinit var errorView: TextView
    private lateinit var videoIdView: TextView
    private lateinit var resultsHeader: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.inputUrl)
        list = findViewById(R.id.thumbList)
        errorView = findViewById(R.id.errorText)
        videoIdView = findViewById(R.id.videoId)
        resultsHeader = findViewById(R.id.resultsHeader)

        findViewById<Button>(R.id.btnGo).setOnClickListener { go() }
    }

    private fun go() {
        val id = extractId(input.text.toString())
        if (id == null) {
            errorView.visibility = View.VISIBLE
            resultsHeader.visibility = View.GONE
            list.removeAllViews()
            return
        }
        errorView.visibility = View.GONE
        hideKeyboard()
        render(id)
    }

    private fun extractId(raw: String): String? {
        val t = raw.trim()
        val idRegex = Regex("^[a-zA-Z0-9_-]{11}$")
        if (idRegex.matches(t)) return t

        val uri = try {
            Uri.parse(if (t.startsWith("http")) t else "https://$t")
        } catch (e: Exception) { return null }

        val host = (uri.host ?: return null).removePrefix("www.").removePrefix("m.")

        if (host == "youtu.be") {
            val id = uri.pathSegments.firstOrNull() ?: return null
            return if (idRegex.matches(id)) id else null
        }
        if (host.endsWith("youtube.com") || host == "youtube-nocookie.com") {
            uri.getQueryParameter("v")?.let { if (idRegex.matches(it)) return it }
            val segs = uri.pathSegments
            if (segs.size >= 2 && segs[0] in listOf("shorts", "embed", "live", "v")) {
                if (idRegex.matches(segs[1])) return segs[1]
            }
        }
        return null
    }

    private fun render(videoId: String) {
        list.removeAllViews()
        resultsHeader.visibility = View.VISIBLE
        videoIdView.text = videoId

        val inflater = LayoutInflater.from(this)
        for (q in qualities) {
            val card = inflater.inflate(R.layout.item_quality, list, false)
            val img = card.findViewById<ImageView>(R.id.thumbImage)
            val badge = card.findViewById<TextView>(R.id.badge)
            val name = card.findViewById<TextView>(R.id.qName)
            val size = card.findViewById<TextView>(R.id.qSize)
            val btn = card.findViewById<Button>(R.id.btnDownload)
            val na = card.findViewById<TextView>(R.id.naOverlay)

            badge.text = q.badge
            name.text = q.name
            size.text = q.size

            val url = "https://img.youtube.com/vi/$videoId/${q.key}.jpg"

            Glide.with(this).load(url)
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?,
                        target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean
                    ): Boolean {
                        na.visibility = View.VISIBLE
                        btn.isEnabled = false
                        btn.alpha = 0.4f
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable, model: Any,
                        target: Target<android.graphics.drawable.Drawable>?,
                        dataSource: DataSource, isFirstResource: Boolean
                    ): Boolean {
                        // YouTube serves a 120x90 placeholder for missing qualities
                        if (q.key != "default" && resource.intrinsicWidth <= 120) {
                            na.visibility = View.VISIBLE
                            btn.isEnabled = false
                            btn.alpha = 0.4f
                        }
                        return false
                    }
                })
                .into(img)

            btn.setOnClickListener { download(url, "${videoId}_${q.key}.jpg") }

            // Staggered entry animation
            card.alpha = 0f
            card.translationY = 30f
            list.addView(card)
            card.animate().alpha(1f).translationY(0f)
                .setStartDelay((list.childCount * 90).toLong())
                .setDuration(400).start()
        }
    }

    private fun download(url: String, filename: String) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDescription("ThumbGrab download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ThumbGrab/$filename")
            dm.enqueue(req)
            Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }
}
