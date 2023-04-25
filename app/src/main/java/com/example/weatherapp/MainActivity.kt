package com.example.weatherapp


import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    //Setup the Main Activity viewbinding to use its xml layout objects
    private lateinit var binding: ActivityMainBinding

    //Step10 Prepare a variable for Location Client (needed for current location check in Step12)
    //and variables for location data
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var latitude: Double? = null
    private var longitude : Double? = null
    //prepare measurement units variable, used to set an API call query for proper units in API result
    private lateinit var units: String

    //Step28 Var to hold the Dialog for Custom Progress Bar
    private lateinit var customProgressDialog: Dialog

    //Step39 prepare a variable for storing simple data in the app storage
    //Idea is to store last weather data, when the app starts the UI has last info on the screen before updating it with current data
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Instantiate the MainActivity viewbinding object by filling it up with xml layout objects/items
        binding = ActivityMainBinding.inflate(layoutInflater)
        //Set the content of Main Activity screen using the viewbinding
        setContentView(binding.root)

        //Step11 Initialise the Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //Step40 Initialise storage for simple data in the app
        sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)//Private mode allows access only for current application


        //Step23c set measurement units for API call results, also used in main screen UI setup
        units = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //get user phone locale preferences and set measurement units accordingly
            getUnits(application.resources.configuration.locales[0].isO3Country.toString())
        }else{
            getUnits(application.resources.configuration.locale.isO3Country.toString())
        }

        //Step44 Populate a previously stored weather response (in case there is any) to avoid
        // blank user screen before updating it with a current weather data
        setupUI()

        //Step6 Check if location service is enabled on device
        if (!isLocationEnabled()) { //If no location settings enabled, send user to settings to enable it
            Toast.makeText(
                this,
                "Your location provider is off.\nPlease turn it on in settings",
                Toast.LENGTH_LONG
            ).show()
            lifecycleScope.launch {
                delay(2500)
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {//In case location provider is on, use the location and get the weather
            getCurrentLocation()
        }
    }

    //Step7 Function to verify if the user's phone has Location services enabled,
    //as location is required further to determine user's location
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    //Step8 Function to get user's current location
    private fun getCurrentLocation(){
        //Check if Location access is currently denied
        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION) || ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)){
            //If Location access is currently denied, run a method to request a user to allow permissions
            showRationaleDialogForPermissions("Location access required",
                "Location access must be granted to use this application."+
                "Please allow access in the App Settings")
        }else{ //If location is not denied, request it and set the current location
            requestPermissionCurrentLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    //Step12 Launcher to request current location permission and use the location if granted
    private val requestPermissionCurrentLocation: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                //Engage a Progress Dialog to notify a user to wait until the next action finishes
                showProgressDialog()
                requestLocation()
            } else {
                // If the permission is denied then show a text
                Toast.makeText(this, "Access to location was denied", Toast.LENGTH_SHORT).show()
            }
        }

    //Step13 Suppress "Missing Permission" warning of fusedLocationClient that the following function uses,
    //as before that function call, Location Permission is successfully checked in previous Step 12
    @SuppressLint("MissingPermission")
    //Step14 Function to get user Location
    private fun requestLocation(){
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).setMaxUpdates(1).build()
        val locationCallback= object : LocationCallback(){
            override fun onLocationResult(locatnReslt: LocationResult){
                //Remove the Progress Dialog that notifying a user to wait
                cancelProgressDialog()
                latitude = locatnReslt.lastLocation!!.latitude
                longitude = locatnReslt.lastLocation!!.longitude
                if (latitude != null && longitude != null) {
                getWeatherByLocation()
                }else{
                    Toast.makeText(this@MainActivity, "Unable to get the device location", Toast.LENGTH_LONG).show()
                }
            }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        //After all preparations to process a location data are configured, get a user-device location
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }

    //Step9 method to request a user to allow permissions
    private fun showRationaleDialogForPermissions(title: String, message: String){
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Go To Settings"){_,_ ->
                try { //Send a user to App settings to update allowed permissions
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    //Step22 Function to get the weather details at user location using Retrofit library
    private fun getWeatherByLocation(){
        if(Constants.isOnline(this)) { //If user's device has Internet connection
            //Prepare and make a Retrofit call to Weather API
                //build a retrofit instance connection
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)//pass the URL to call to
                .addConverterFactory(GsonConverterFactory.create())//support the Gson format for result body deserialization(datatype of an API  response set in the WeatherService interface used to set the API call URL)
                .build()
            //utilise WeatherService interface to create retrofit service
            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)
            //Prepare the retrofit API call
                val listCall: Call<WeatherResponseGSON> = service.getWeather(
                    latitude!!, longitude!!, Constants.APP_ID, units,
                )
            //Engage the Progress Dialog to notify a user to wait until the API call actions finish
            showProgressDialog()
                //make a retrofit API call, get a response
                listCall.enqueue(object : Callback<WeatherResponseGSON> {
                    //next two functions are mandatory to override with Callback object

                    //actions when call gets a response
                    override fun onResponse(
                        call: Call<WeatherResponseGSON>,
                        response: Response<WeatherResponseGSON>
                    ) {
                        //Remove the Progress Dialog that notifies a user to wait
                        cancelProgressDialog()
                        if (response.isSuccessful) {//when response is successful
                            //Step42 Save a response in a prepared Gson format (response is provided in that format as per WeatherService interface response format set previously)
                            val weatherList: WeatherResponseGSON? = response.body()
                            val weatherResponseJsonString = Gson().toJson(weatherList) //brings response into String format
                            val editor = sharedPreferences.edit()
                            editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)//name a data in the app storage (similar to subfolder)
                            editor.apply()
                            Log.i("Response Result", "$weatherList")
                            setupUI()
                        } else {//when response is not successful
                            when (response.code()) {
                                400 -> Log.e("Error 400", "Not connected to API")
                                404 -> Log.e("Error 404", "API not found")
                                else -> Log.e("Error", "Generic Error")
                            }
                        }
                    }
                    //what to do if the call fails
                    override fun onFailure(call: Call<WeatherResponseGSON>, t: Throwable) {
                        //Remove the Progress Dialog that notifies a user to wait
                        cancelProgressDialog()
                        Log.e("Error !", t.message.toString())
                    }
                }
                )
        } else {//If user's device has no Internet connection
                Toast.makeText(
                    this,
                    "You are offline.Cannot check the weather now",
                    Toast.LENGTH_LONG
                ).show()
            }

    }

    //Step29a Function to show custom progress dialog
    private fun showProgressDialog(){
        customProgressDialog = Dialog(this)
        //Set content view to be the progress bar with text as in the prepared layout
        customProgressDialog.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog.show()
    }

    //Step29b Function to dismiss the custom progress dialog
    private fun cancelProgressDialog(){
        customProgressDialog.dismiss()
    }

    //Step33 function to setup a User Interface
    private fun setupUI (){
        //prepare units to represent values properly
        val tempUnit = if (units=="metric") "°C" else "°F"
        val speedUnit = if (units=="metric") "km/h" else "Mph"
        //Step43 Receive the stored previously weather data from sharedPreferences and populate values to the UI (in case there is any data)
        //On the App start it populates the UI while new weather data being received from the Internet
        val weatherResponseJsonString = sharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "") //requires a default value, empty string in this case
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponseGSON::class.java)//convert stored string back into GSON format
            //first result in weatherList.weather[i] is PRIMARY, so use [0] index of it
            Log.i("Weather Name", weatherList.weather[0].toString())
            binding.tvMain.text = weatherList.weather[0].main
            binding.tvMainDescription.text = weatherList.weather[0].description

            binding.tvSunriseTime.text = humanTime(weatherList.sys.sunrise)
            binding.tvSunsetTime.text = humanTime(weatherList.sys.sunset)
            binding.tvHumidity.text = weatherList.main.humidity.toString() + "% humid"

            binding.tvSpeed.text = weatherList.wind.speed.toString()
            binding.tvSpeedUnit.text = speedUnit
            binding.tvTemp.text = weatherList.main.temp.toString() + tempUnit
            binding.tvMin.text = "min " + weatherList.main.temp_min.toString() + tempUnit
            binding.tvMax.text = "max " + weatherList.main.temp_max.toString() + tempUnit
            binding.tvCityName.text = weatherList.name
            binding.tvCountry.text = weatherList.sys.country
            binding.tvLastUpdated.text = "Last Updated: " + humanDateTime(weatherList.dt)
            when (weatherList.weather[0].icon) {
                "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "09d" -> binding.ivMain.setImageResource(R.drawable.rain)
                "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "50d" -> binding.ivMain.setImageResource(R.drawable.mist)
                "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "09n" -> binding.ivMain.setImageResource(R.drawable.rain)
                "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "50n" -> binding.ivMain.setImageResource(R.drawable.mist)
            }
        }
    }



    //Step34 Function to get provided in milliseconds sunrise/-set time values into human-readable format
    private fun humanTime(timeX:Long) : String{
        val date = Date(timeX*1000L)
        //simple date format helps to translate the date passed to it
        val sdf = SimpleDateFormat("HH:mm")//HH makes it 24h format (use hh for 12h)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
    //Step35 Function to get provided in milliseconds date-time value into human-readable format
    private fun humanDateTime(timeX:Long) : String{
        val date = Date(timeX*1000L)
        //simple date format helps to translate the date passed to it
        val sdf = SimpleDateFormat("MMM/dd/yyyy HH:mm")//HH makes it 24h format (use hh for 12h)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    //Step23b Function to get measurement units according to preset user phone preferences
    private fun getUnits(localesValue: String): String{
        var value = "metric"
        if(localesValue == "USA"||localesValue == "LBR"||localesValue == "MMR"){
            value = "imperial"
        }
        return value
    }

    //Step37 Engage a Menu of a top toolbar, use prepared in last step menu layout
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    //Step38 Actions to take when menu item is clicked
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                getCurrentLocation()//this action starts the steps to get weather and populate the UI with updated details
                true
            }  else -> return super.onOptionsItemSelected(item)
        }

    }
}

