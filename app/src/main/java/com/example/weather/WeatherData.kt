package com.example.weather

data class CurrentWeatherInfo(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val weatherDescriptionHindi: String,
    val weatherCode: Int,
    val windSpeed: Double,
    val isDay: Boolean,
    val cityName: String,
    val formattedVoiceHindi: String
)

sealed class WeatherResult {
    data class Success(
        val weather: CurrentWeatherInfo,
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : WeatherResult()

    data class PermissionRequired(
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : WeatherResult()

    data class Error(
        val messageHindi: String,
        val voiceResponseHindi: String
    ) : WeatherResult()
}
