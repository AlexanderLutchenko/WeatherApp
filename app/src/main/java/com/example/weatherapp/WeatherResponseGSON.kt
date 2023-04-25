package com.example.weatherapp

import java.io.Serializable



//Step19 Create a data class that will be used to receive a JSON object with Weather Response
//of its own specific model

//GSON will map the prepared here variables to the JSON file variables(keys) names

data class WeatherResponseGSON (
    val coord: Coord,
    val weather: List<Weather>,
    val base: String,
    val main: Main,
    val visibility: Int,
    val wind: Wind,
    val clouds: Clouds,
    val dt: Long,
    val sys: Sys,
    val id: Int,
    val name: String,
    val cod: Int
    //Step20 Transform this data class to the datatype format that can be passed from one activity/class to another
    ) : Serializable

data class Coord(
    val lon: Double,
    val lat: Double
    //Step21 Transform this data class to the datatype format that can be passed from one activity/class to another
) : Serializable

data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
) : Serializable

data class Main(
    val temp: Double,
    val pressure: Double,
    val humidity: Int,
    val temp_min: Double,
    val temp_max: Double,
    val sea_level: Double,
    val grnd_level: Double
) : Serializable

data class Wind(
    val speed: Double,
    val deg: Int
) : Serializable

data class Clouds(
    val all: Int
) : Serializable

data class Sys(
    val type: Int,
    val message: Double,
    val country: String,
    val sunrise: Long,
    val sunset: Long
) : Serializable