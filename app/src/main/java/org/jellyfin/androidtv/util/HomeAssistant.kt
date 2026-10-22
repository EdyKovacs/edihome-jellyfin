package org.jellyfin.androidtv.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fires a Home Assistant webhook when the user presses the "lights off" button on the details screen.
 *
 * The URL and optional token come from `ha.webhook.url` / `ha.token` in gradle.properties and end up in
 * [BuildConfig]. When the URL is blank the button is not shown at all.
 */
object HomeAssistant {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	/** True when a webhook URL was configured at build time. */
	@JvmStatic
	fun isConfigured(): Boolean = BuildConfig.HA_WEBHOOK_URL.isNotBlank()

	/**
	 * Sends the webhook in the background and reports the result with a toast.
	 * Safe to call from the UI thread.
	 */
	@JvmStatic
	fun turnOffLights(context: Context) {
		if (!isConfigured()) {
			Utils.showToast(context, context.getString(R.string.msg_lights_off_not_configured))
			return
		}

		val appContext = context.applicationContext
		scope.launch {
			val success = runCatching { post(BuildConfig.HA_WEBHOOK_URL) }
				.getOrDefault(false)

			withContext(Dispatchers.Main) {
				Utils.showToast(
					appContext,
					appContext.getString(
						if (success) R.string.msg_lights_off_sent else R.string.msg_lights_off_failed
					)
				)
			}
		}
	}

	private fun post(url: String): Boolean {
		val connection = URL(url).openConnection() as HttpURLConnection
		return try {
			connection.requestMethod = "POST"
			connection.connectTimeout = TIMEOUT_MS
			connection.readTimeout = TIMEOUT_MS
			connection.doOutput = true
			connection.setRequestProperty("Content-Type", "application/json")
			if (BuildConfig.HA_TOKEN.isNotBlank()) {
				connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.HA_TOKEN}")
			}

			// Webhook triggers ignore the body, but the REST API needs a JSON object.
			connection.outputStream.use { it.write("{}".toByteArray()) }

			connection.responseCode in 200..299
		} finally {
			connection.disconnect()
		}
	}

	private const val TIMEOUT_MS = 5000
}
