package com.example.weatherapp

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

//Step24 Interface to use for "service" Retrofit object in Main Activity, it used to complete the URL-query to API, adding queries to base URL tail
interface WeatherService {
    @GET("2.5/weather") // this is an API request method, "GET" in this case
    fun getWeather( //declare the request parameters to be included in an API request (URL query portion)
        @Query("lat", ) lat: Double,
        @Query("lon", ) lon: Double,
        @Query("appid", ) appid: String?,
        @Query("units") units: String?
    ) : Call<WeatherResponseGSON> //This is an Response and its intended datatype
}
