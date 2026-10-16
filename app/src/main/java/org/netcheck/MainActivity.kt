package org.netcheck

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// --- Список сайтов для проверки ---
private val SITES = listOf(
    "t.me", "x.com", "instagram.com", "youtube.com", "gosuslugi.ru",
    "ya.ru", "vk.ru", "max.ru", "sberbank.ru", "vtb.ru", "tbank.ru", "alfabank.ru",
    "ozon.ru", "wildberries.ru", "rutracker.org", "pikabu.com", "yaplakal.com",
)

// --- Поля информации об IP ---
private val IP_FIELDS = listOf(
    "ip" to "IP",
    "hostname" to "Hostname",
    "city" to "City",
    "region" to "Region",
    "country" to "Country",
    "org" to "Org",
)

private const val TIMEOUT_MS = 5000
private const val IP_TIMEOUT_MS = 8000
private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

private val COLOR_BG = Color.rgb(28, 30, 36)
private val COLOR_PANEL = Color.rgb(41, 43, 51)
private val COLOR_BORDER = Color.rgb(97, 107, 128)
private val COLOR_TEXT = Color.rgb(235, 237, 242)
private val COLOR_DIM = Color.rgb(166, 173, 189)
private val COLOR_WAIT = Color.rgb(176, 136, 0)
private val COLOR_OK = Color.rgb(46, 160, 67)
private val COLOR_NOT = Color.rgb(218, 54, 51)

class MainActivity : Activity() {

    private val pool: ExecutorService = Executors.newCachedThreadPool()

    private val statusViews = LinkedHashMap<String, TextView>()
    private val ipViews = LinkedHashMap<String, TextView>()
    private lateinit var counterView: TextView
    private lateinit var refreshButton: Button

    // Эти поля читаются и меняются только в главном потоке
    private var checking = false
    private var done = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        checkAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
    }

    // ---------- Интерфейс ----------
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun label(
        text: String,
        sizeSp: Float = 14f,
        color: Int = COLOR_TEXT,
        bold: Boolean = false,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        // --- Рамка с информацией об IP (сверху) ---
        val ipFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_PANEL)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), COLOR_BORDER)
            }
            setPadding(dp(10), dp(6), dp(10), dp(8))
        }
        ipFrame.addView(label("IP info", 13f, COLOR_DIM, bold = true))
        for ((key, title) in IP_FIELDS) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(1), 0, dp(1))
            }
            row.addView(
                label("$title:", 13f, bold = true),
                LinearLayout.LayoutParams(dp(82), WRAP_CONTENT),
            )
            val value = label("…", 13f).apply { setTextIsSelectable(true) }
            row.addView(value, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            ipViews[key] = value
            ipFrame.addView(row)
        }
        root.addView(ipFrame, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // --- Список сайтов (под рамкой) ---
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }
        for (site in SITES) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(label(site), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            val status = label("wait", 14f, COLOR_WAIT, bold = true).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.END
            }
            row.addView(status, LinearLayout.LayoutParams(dp(56), WRAP_CONTENT))
            statusViews[site] = status
            list.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) },
        )

        // --- Нижняя панель ---
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        counterView = label("", 13f, COLOR_DIM)
        bottom.addView(counterView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        refreshButton = Button(this).apply {
            text = "Проверить снова"
            setAllCaps(false)
            setOnClickListener { checkAll() }
        }
        bottom.addView(refreshButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        root.addView(
            bottom,
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) },
        )

        return root
    }

    private fun setStatus(view: TextView, text: String, color: Int) {
        view.text = text
        view.setTextColor(color)
    }

    // ---------- Запуск проверки ----------
    private fun checkAll() {
        if (checking) return
        checking = true
        done = 0
        refreshButton.isEnabled = false
        counterView.text = "Проверка… 0/${SITES.size}"

        statusViews.values.forEach { setStatus(it, "wait", COLOR_WAIT) }
        ipViews.values.forEach { it.text = "…" }

        pool.execute { loadIp() }
        for (site in SITES) {
            pool.execute {
                val ok = probe(site)
                runOnUiThread { onSiteDone(site, ok) }
            }
        }
    }

    // ---------- IP info (в фоновом потоке) ----------
    private fun loadIp() {
        val data: JSONObject? = try {
            val conn = URL("https://ipinfo.io/json").openConnection() as HttpURLConnection
            conn.connectTimeout = IP_TIMEOUT_MS
            conn.readTimeout = IP_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", UA)
            try {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
        runOnUiThread {
            for ((key, view) in ipViews) {
                view.text = if (data == null) {
                    if (key == "ip") "ошибка" else "—"
                } else {
                    data.optString(key, "N/A")
                }
            }
        }
    }

    // ---------- Проверка сайта (в фоновом потоке) ----------
    /** Трёхэтапная проверка: DNS → TCP → HTTP. */
    private fun probe(host: String): Boolean {
        // 1. DNS
        try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return false
        }

        // 2. TCP
        try {
            Socket().use { it.connect(InetSocketAddress(host, 443), TIMEOUT_MS) }
        } catch (e: Exception) {
            return false
        }

        // 3. HTTP (как verify=False в исходной программе: сертификат не проверяется,
        //    важна только доступность; настройка действует лишь на это соединение)
        return try {
            val conn = URL("https://$host").openConnection() as HttpsURLConnection
            conn.sslSocketFactory = trustAllContext.socketFactory
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            try {
                conn.responseCode < 500
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }

    private val trustAllContext: SSLContext by lazy {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
    }

    // ---------- Обновление строки (в главном потоке) ----------
    private fun onSiteDone(site: String, ok: Boolean) {
        statusViews[site]?.let {
            setStatus(it, if (ok) "ok" else "not", if (ok) COLOR_OK else COLOR_NOT)
        }
        done++
        if (done >= SITES.size) {
            checking = false
            refreshButton.isEnabled = true
            val okCount = statusViews.values.count { it.text.toString() == "ok" }
            counterView.text = "Доступно: $okCount/${SITES.size}"
        } else {
            counterView.text = "Проверка… $done/${SITES.size}"
        }
    }
}
