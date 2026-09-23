package com.example.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Weather Manager powered by Open-Meteo free API (No API key needed) and FusedLocationProviderClient.
 * Fully supports Hindi voice prompts and local TTS output.
 */
class WeatherManager(private val context: Context) {

    private val tag = "WeatherManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    /**
     * Checks if the voice command is asking about weather or temperature.
     */
    fun isWeatherCommand(rawCommand: String): Boolean {
        val q = rawCommand.lowercase().trim()
        val weatherKeywords = listOf(
            "मौसम", "mausam", "weather", "तापमान", "tapman", "temperature",
            "बारिश", "barish", "rain", "धूप", "dhoop", "गर्मी", "garmi", "सर्दी", "sardi",
            "aaj ka mausam", "mausam kaisa hai", "aaj barish hogi kya", "current weather",
            "forecast", "हवा कैसी है", "हवा का रुख"
        )
        return weatherKeywords.any { q.contains(it) }
    }

    /**
     * Obtains the user's current location and queries Open-Meteo API.
     */
    suspend fun fetchCurrentWeather(): WeatherResult = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext WeatherResult.PermissionRequired(
                messageHindi = "मौसम जानने के लिए लोकेशन की अनुमति (Location Permission) आवश्यक है। कृपया परमिशन दें।",
                voiceResponseHindi = "मौसम जानने के लिए मुझे लोकेशन की अनुमति चाहिए। कृपया स्क्रीन पर अनुमति दें।"
            )
        }

        val location = getCurrentLocation()
        val latitude: Double
        val longitude: Double
        val cityName: String

        if (location != null) {
            latitude = location.latitude
            longitude = location.longitude
            cityName = getCityName(latitude, longitude) ?: "आपके वर्तमान स्थान"
            Log.i(tag, "REAL ACTION: FusedLocationProviderClient obtained real GPS location -> Lat: $latitude, Lon: $longitude ($cityName)")
        } else {
            latitude = 28.6139
            longitude = 77.2090
            cityName = "नई दिल्ली (डिफ़ॉल्ट स्थान)"
            Log.w(tag, "REAL ACTION: GPS location hardware timeout/unavailable -> Using coordinates ($latitude, $longitude)")
        }

        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m&timezone=auto"
            Log.i(tag, "REAL ACTION: Executing HTTP GET to Open-Meteo API -> $url")
            val request = Request.Builder().url(url).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext WeatherResult.Error(
                        messageHindi = "मौसम सर्वर से जानकारी प्राप्त नहीं हो सकी (Code ${response.code})।",
                        voiceResponseHindi = "माफ़ कीजिये, अभी मौसम की जानकारी लोड नहीं हो सकी।"
                    )
                }

                val bodyString = response.body?.string() ?: ""
                val rootJson = JSONObject(bodyString)
                val currentJson = rootJson.optJSONObject("current")

                if (currentJson == null) {
                    return@withContext WeatherResult.Error(
                        messageHindi = "मौसम डेटा अमान्य प्राप्त हुआ।",
                        voiceResponseHindi = "मौसम का डेटा उपलब्ध नहीं हो सका।"
                    )
                }

                val temp = currentJson.optDouble("temperature_2m", 25.0)
                val apparentTemp = currentJson.optDouble("apparent_temperature", temp)
                val humidity = currentJson.optInt("relative_humidity_2m", 50)
                val weatherCode = currentJson.optInt("weather_code", 0)
                val windSpeed = currentJson.optDouble("wind_speed_10m", 5.0)
                val isDay = currentJson.optInt("is_day", 1) == 1

                val descHindi = getWeatherDescriptionHindi(weatherCode, isDay)
                val tempRounded = temp.roundToInt()
                val windRounded = windSpeed.roundToInt()

                // Construct conversational natural Hindi response
                val voiceResponse = "आज $cityName में तापमान $tempRounded डिग्री सेल्सियस है, और $descHindi। हवा की गति लगभग $windRounded किलोमीटर प्रति घंटा है।"

                val weatherInfo = CurrentWeatherInfo(
                    temperature = temp,
                    apparentTemperature = apparentTemp,
                    humidity = humidity,
                    weatherDescriptionHindi = descHindi,
                    weatherCode = weatherCode,
                    windSpeed = windSpeed,
                    isDay = isDay,
                    cityName = cityName,
                    formattedVoiceHindi = voiceResponse
                )

                val detailedLog = """
                    📍 स्थान: $cityName ($latitude, $longitude)
                    🌡 तापमान: ${tempRounded}°C (महसूस: ${apparentTemp.roundToInt()}°C)
                    ☁️ स्थिति: $descHindi (Code $weatherCode)
                    💧 नमी: $humidity% | 💨 हवा: $windRounded किमी/घंटा
                    [ओपन-मीटियो फ्री API से तुरंत प्राप्त]
                """.trimIndent()

                return@withContext WeatherResult.Success(
                    weather = weatherInfo,
                    messageHindi = detailedLog,
                    voiceResponseHindi = voiceResponse
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch weather: ${e.localizedMessage}", e)
            return@withContext WeatherResult.Error(
                messageHindi = "मौसम प्राप्त करने में त्रुटि: ${e.localizedMessage}",
                voiceResponseHindi = "इंटरनेट या मौसम सेवा में समस्या आ रही है।"
            )
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        return try {
            val tokenSource = CancellationTokenSource()
            suspendCancellableCoroutine { continuation ->
                try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        tokenSource.token
                    ).addOnSuccessListener { loc ->
                        if (loc != null) {
                            continuation.resume(loc)
                        } else {
                            // Try lastLocation
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                continuation.resume(lastLoc)
                            }.addOnFailureListener {
                                continuation.resume(null)
                            }
                        }
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                } catch (e: SecurityException) {
                    continuation.resume(null)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Fused location error: ${e.localizedMessage}")
            null
        }
    }

    private fun getCityName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale("hi", "IN"))
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                address.locality ?: address.subAdminArea ?: address.adminArea
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * WMO Weather Interpretation Codes (WW) into natural Hindi descriptions.
     */
    private fun getWeatherDescriptionHindi(code: Int, isDay: Boolean): String {
        return when (code) {
            0 -> if (isDay) "आसमान बिल्कुल साफ़ और धूप खिली है" else "आसमान बिल्कुल साफ़ है"
            1, 2, 3 -> "हल्के बादल छाए हुए हैं"
            45, 48 -> "हल्का कोहरा और धुंध है"
            51, 53, 55 -> "हल्की बूंदाबांदी (Drizzle) हो रही है"
            56, 57 -> "शीतल फुहारें पड़ रही हैं"
            61 -> "हल्की बारिश हो रही है"
            63 -> "मध्यम बारिश हो रही है"
            65 -> "तेज़ बारिश हो रही है"
            66, 67 -> "ओलावृष्टि या भारी वर्षा की स्थिति है"
            71, 73, 75 -> "हल्की बर्फ़बारी हो रही है"
            77 -> "बर्फ़ के कण गिर रहे हैं"
            80, 81, 82 -> "बारिश की बौछारें पड़ने की संभावना है"
            85, 86 -> "बर्फ़ की बौछारें हो सकती हैं"
            95 -> "गरज के साथ बारिश होने की संभावना है"
            96, 99 -> "तूफ़ान और ओले पड़ने की संभावना है"
            else -> "मौसम सामान्य बना हुआ है"
        }
    }
}
