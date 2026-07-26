package com.immu.thumbgrab

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    data class Quality(val key: String, val name: String, val size: String, val badge: String)

    private val qualities = listOf(
        Quality("maxresdefault", "Full HD", "1280 × 720+", "HD"),
        Quality("hqdefault", "360p", "480 × 360", "360p")
    )

    private val executor = Executors.newFixedThreadPool(3)

    private lateinit var input: EditText
    private lateinit var list: LinearLayout
    private lateinit var errorView: TextView
    private lateinit var videoIdView: TextView
    private lateinit var resultsHeader: View
    private lateinit var metaCard: View
    private lateinit var metaTitle: TextView
    private lateinit var btnCopyTitle: Button
    private lateinit var btnCopyDesc: Button

    private var videoTitle: String? = null
    private var videoDesc: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.inputUrl)
        list = findViewById(R.id.thumbList)
        errorView = findViewById(R.id.errorText)
        videoIdView = findViewById(R.id.videoId)
        resultsHeader = findViewById(R.id.resultsHeader)
        metaCard = findViewById(R.id.metaCard)
        metaTitle = findViewById(R.id.metaTitle)
        btnCopyTitle = findViewById(R.id.btnCopyTitle)
        btnCopyDesc = findViewById(R.id.btnCopyDesc)

        findViewById<Button>(R.id.btnGo).setOnClickListener { go() }

        btnCopyTitle.setOnClickListener {
            videoTitle?.let {
                copyToClipboard("Video title", it)
                Toast.makeText(this, getString(R.string.copied_title), Toast.LENGTH_SHORT).show()
            }
        }
        btnCopyDesc.setOnClickListener {
            videoDesc?.let {
                copyToClipboard("Video description", it)
                Toast.makeText(this, getString(R.string.copied_desc), Toast.LENGTH_SHORT).show()
            }
        }

        handleShared(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShared(intent)
    }

    /** Handles links shared into the app from YouTube (or any app). */
    private fun handleShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        input.setText(shared.trim())
        input.post { go() }
    }

    private fun go() {
        val id = extractId(input.text.toString())
        if (id == null) {
            errorView.visibility = View.VISIBLE
            resultsHeader.visibility = View.GONE
            metaCard.visibility = View.GONE
            list.removeAllViews()
            return
        }
        errorView.visibility = View.GONE
        hideKeyboard()
        render(id)
        fetchMetadata(id)
    }

    private fun extractId(raw: String): String? {
        val text = raw.trim()
        idFromToken(text)?.let { return it }
        // Shared text often looks like: "Watch this 😀 https://youtu.be/ID?si=xyz"
        Regex("https?://\\S+").findAll(text).forEach { m ->
            idFromToken(m.value)?.let { return it }
        }
        return null
    }

    private fun idFromToken(token: String): String? {
        val idRegex = Regex("^[a-zA-Z0-9_-]{11}$")
        if (idRegex.matches(token)) return token

        val uri = try {
            Uri.parse(if (token.startsWith("http")) token else "https://$token")
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
            val btnDl = card.findViewById<Button>(R.id.btnDownload)
            val btnShare = card.findViewById<Button>(R.id.btnShare)
            val na = card.findViewById<TextView>(R.id.naOverlay)

            badge.text = q.badge
            name.text = q.name
            size.text = q.size

            // Holder so the fallback URL is what the buttons use
            val current = arrayOf("https://img.youtube.com/vi/$videoId/${q.key}.jpg")

            fun disable() {
                na.visibility = View.VISIBLE
                btnDl.isEnabled = false; btnDl.alpha = 0.4f
                btnShare.isEnabled = false; btnShare.alpha = 0.4f
            }

            fun useFallback() {
                current[0] = "https://img.youtube.com/vi/$videoId/sddefault.jpg"
                size.text = "640 × 480"
                Glide.with(this@MainActivity).load(current[0]).into(img)
            }

            Glide.with(this).load(current[0])
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?,
                        target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean
                    ): Boolean {
                        if (q.key == "maxresdefault") runOnUiThread { useFallback() } else disable()
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable, model: Any,
                        target: Target<android.graphics.drawable.Drawable>?,
                        dataSource: DataSource, isFirstResource: Boolean
                    ): Boolean {
                        if (q.key == "maxresdefault" && resource.intrinsicWidth <= 120) {
                            runOnUiThread { useFallback() }
                        }
                        return false
                    }
                })
                .into(img)

            btnDl.setOnClickListener { download(current[0], "${videoId}_${q.key}.jpg") }
            btnShare.setOnClickListener { shareImage(current[0], "${videoId}_${q.key}.jpg", btnShare) }

            card.alpha = 0f
            card.translationY = 30f
            list.addView(card)
            card.animate().alpha(1f).translationY(0f)
                .setStartDelay((list.childCount * 90).toLong())
                .setDuration(400).start()
        }
    }

    /** Downloads the image into the cache and opens the system share sheet. */
    private fun shareImage(url: String, filename: String, btn: Button) {
        val label = btn.text
        btn.isEnabled = false
        btn.text = getString(R.string.preparing)

        executor.execute {
            var uri: Uri? = null
            try {
                val dir = File(cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, filename)
                openConnection(url).inputStream.use { inp ->
                    file.outputStream().use { out -> inp.copyTo(out) }
                }
                uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            } catch (e: Exception) {
                // handled below
            }

            val shareUri = uri
            runOnUiThread {
                btn.isEnabled = true
                btn.text = label
                if (shareUri == null) {
                    Toast.makeText(this, getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    videoTitle?.let { putExtra(Intent.EXTRA_TEXT, it) }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.share_via)))
            }
        }
    }

    private fun fetchMetadata(videoId: String) {
        videoTitle = null
        videoDesc = null
        metaCard.visibility = View.VISIBLE
        metaTitle.text = getString(R.string.meta_loading)
        btnCopyTitle.isEnabled = false; btnCopyTitle.alpha = 0.4f
        btnCopyDesc.isEnabled = false; btnCopyDesc.alpha = 0.4f

        executor.execute {
            val title = try {
                val watch = URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
                JSONObject(httpGet("https://www.youtube.com/oembed?url=$watch&format=json"))
                    .getString("title")
            } catch (e: Exception) { null }

            val desc = fetchDescription(videoId)

            runOnUiThread {
                videoTitle = title
                videoDesc = desc

                if (title != null) {
                    metaTitle.text = title
                    btnCopyTitle.isEnabled = true; btnCopyTitle.alpha = 1f
                } else {
                    metaTitle.text = getString(R.string.meta_failed)
                }

                if (desc != null) {
                    btnCopyDesc.isEnabled = true; btnCopyDesc.alpha = 1f
                } else {
                    Toast.makeText(this, getString(R.string.desc_unavailable), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Description is never displayed — only made available to the copy button. */
    private fun fetchDescription(videoId: String): String? {
        val html = try {
            httpGet("https://www.youtube.com/watch?v=$videoId")
        } catch (e: Exception) { return null }

        // Primary: full description held in the player response
        try {
            val m = Regex("\"shortDescription\":\"((?:\\\\.|[^\"\\\\])*)\"").find(html)
            if (m != null) {
                val decoded = JSONObject("{\"d\":\"${m.groupValues[1]}\"}").getString("d")
                if (decoded.isNotBlank()) return decoded
            }
        } catch (e: Exception) { /* fall through */ }

        // Fallback: og:description meta tag
        try {
            val m = Regex("<meta property=\"og:description\" content=\"([^\"]*)\"").find(html)
            if (m != null) {
                val decoded = unescapeHtml(m.groupValues[1])
                if (decoded.isNotBlank()) return decoded
            }
        } catch (e: Exception) { /* fall through */ }

        return null
    }

    private fun unescapeHtml(s: String): String = s
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&amp;", "&")

    private fun openConnection(urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        )
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        // Skips the EU consent interstitial that otherwise replaces the page
        conn.setRequestProperty("Cookie", "CONSENT=YES+cb; SOCS=CAI")
        return conn
    }

    private fun httpGet(urlStr: String): String =
        openConnection(urlStr).inputStream.bufferedReader().use { it.readText() }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
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
