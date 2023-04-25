package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

//Step16 Create Constants object to use in different activities of the project
object Constants {


    //Step17 Create a function to check if the phone is connected to the Internet
    fun isOnline(context: Context) : Boolean{
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        //Check the user phone SDK version, if it's higher than M, use newer version of Network check
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            val network = connectivityManager.activeNetwork ?: return false // if network=null -> return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }else{ //If SDK is lower than M, use old method of Network check
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }

    //Step23a Prepare constants for Retrofit API call
    // There are two ways of using the API key in Android Studio - open and secure(safe to upload to GitHub and hide the API key)
    // 1) For open way: paste it into res/values/strings.xml "api_key"(as an example) string value
        //Inside any project activity the API Key can be accessed as a variable:
        // val apiKey = resources.getString(R.string.api_key) (need to import com.example.weatherapp.R into activity)
    // 2) For secure way:
        // 2.a) Add a Secret Gradle Plugin that allows to create and read properties from Gradle's "local.properties" file
        // This file is not checked into GitHub version control system, meaning its content kept on yor local device but its properties can be
        // exposed as variables to Gradle-generated BuildConfig.java and AndroidManifest.xml files.
        // 2.b) In your project's root build.gradle file add a dependency, then synchronise Gradle:
        //classpath "com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1"
        //2.c) In your module build.gradle file add a plugin, then synchronise Gradle:
        //id 'com.google.android.libraries.mapsplatform.secrets-gradle-plugin'
        //2.d) In your Gradle local.properties file add
        //ApiKEY=YOUR_API_KEY_VALUE (Insert the real value instead of YOUR_API_KEY_VALUE, no quotes or brackets)
        //2.e) Now use it as a variable in AndroidManifest.xml file. Create meta-data record within <appication/>:
                /*<meta-data
                android:name="weatherAppKey"
                android:value="${ApiKEY}" />*/
        //2.f)Start the app to rebuild the BuildConfig.java file with the previously added variable
        //Inside any project activity the API Key can be accessed as a variable:
        //val apiKey = BuildConfig.ApiKEY
    const val APP_ID: String = BuildConfig.ApiKEY
    //constant for retrofit URL to call
    const val BASE_URL: String = "https://api.openweathermap.org/data/"

    //Step41 Constants for SharedPreferences - storage of simple data in the app
    const val PREFERENCE_NAME = "WeatherAppPreferences"
    const val WEATHER_RESPONSE_DATA = "weather_response_data"


}

