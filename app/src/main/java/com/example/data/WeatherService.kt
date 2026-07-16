package com.example.data

import com.squareup.moshi.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Calendar

data class WeatherResponse(
    @Json(name = "main") val main: MainData?,
    @Json(name = "weather") val weatherList: List<WeatherDescription>?
)

data class MainData(
    @Json(name = "temp") val temp: Double,
    @Json(name = "humidity") val humidity: Double
)

data class WeatherDescription(
    @Json(name = "main") val mainCond: String,
    @Json(name = "description") val description: String
)

interface OpenWeatherApi {
    @GET("weather")
    suspend fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse
}

class WeatherService {

    private val api: OpenWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    suspend fun fetchWeather(lat: Double, lon: Double, apiKey: String): WeatherInfo = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            // Generate deterministic offline mock weather depending on time & lat for real look&feel!
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val mockTemp = if (hour in 7..17) {
                (25 + (lat % 8) + (hour - 12) * 0.4).toInt()
            } else {
                (18 + (lat % 6) + (24 - hour) * 0.2).toInt()
            }
            val mockHumidity = (60 + (lon % 20)).toInt().coerceIn(10, 100)
            val cond = when {
                mockHumidity > 75 -> "Rainy 🌧"
                mockTemp > 28 -> "Sunny ☀️"
                mockTemp < 20 -> "Cool/Windy 💨"
                else -> "Partly Cloudy ⛅"
            }
            return@withContext WeatherInfo(
                tempStr = "${mockTemp}°C",
                humidityStr = "${mockHumidity}%",
                conditionStr = cond
            )
        }

        try {
            val response = api.getWeather(lat, lon, apiKey)
            val temp = response.main?.temp?.toInt() ?: 25
            val humidity = response.main?.humidity?.toInt() ?: 60
            val desc = response.weatherList?.firstOrNull()?.mainCond ?: "Clear"
            val emoji = when (desc.lowercase()) {
                "clear" -> "Sunny ☀️"
                "clouds" -> "Cloudy ☁️"
                "rain", "drizzle" -> "Rainy 🌧"
                "thunderstorm" -> "Stormy ⛈"
                "snow" -> "Snowy ❄️"
                "mist", "fog" -> "Foggy 🌫"
                else -> "$desc ⛅"
            }
            WeatherInfo(
                tempStr = "${temp}°C",
                humidityStr = "${humidity}%",
                conditionStr = emoji
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback mock weather on network failure
            WeatherInfo(
                tempStr = "26°C",
                humidityStr = "65%",
                conditionStr = "Partly Cloudy ⛅"
            )
        }
    }
}

data class WeatherInfo(
    val tempStr: String,
    val humidityStr: String,
    val conditionStr: String
)
