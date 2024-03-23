package com.example.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.OverlayItem
import retrofit2.http.GET
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.telephony.TelephonyManager
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte


class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private val PERMISSIONS_REQUEST_CODE = 101


    private lateinit var fusedLocationClient: FusedLocationProviderClient
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switch: Switch
    private lateinit var mapController: IMapController


    private fun setPoint(lat: Double, lon: Double, color: Int = Color.RED, setCenter: Boolean = false) {
        mapController.setZoom(18.0)
        val myLocation = GeoPoint(
            lat,
            lon
        )
        if (setCenter) {
            mapController.setCenter(myLocation)
        }
        val myMarker = OverlayItem("Title", "Description", myLocation)
        val d = ShapeDrawable(OvalShape())
        d.intrinsicHeight = 50
        d.intrinsicWidth = 50
        d.paint.color = color
        myMarker.setMarker(d)

        val markersOverlay = ItemizedOverlayWithFocus<OverlayItem>(
            this,
            listOf(myMarker),
            object :
                ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                override fun onItemSingleTapUp(
                    index: Int,
                    item: OverlayItem
                ): Boolean {
                    // Реакция на одиночный тап по маркеру
                    return true
                }

                override fun onItemLongPress(
                    index: Int,
                    item: OverlayItem
                ): Boolean {
                    // Реакция на долгий тап по маркеру
                    return false
                }
            }
        )
        markersOverlay.setFocusItemsOnTap(true)
        map.overlays.add(markersOverlay)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
//            // Все разрешения предоставлены, можно сканировать вышки
//            scanCellTowers()
//        }
    }

    private fun scanCellTowers(): List<GsmResultV0> {
        val results = mutableListOf<GsmResultV0>()

        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo
            cellInfoList?.forEach { cellInfo ->
                Log.e("r", cellInfo.toString())
                if (cellInfo is CellInfoGsm) {
                    val cellIdentity = cellInfo.cellIdentity
                    val cellSignalStrength = cellInfo.cellSignalStrength
                    results.add(
                        GsmResultV0(
                            mcc = cellIdentity.mcc,
                            mnc = cellIdentity.mnc,
                            lac = cellIdentity.lac,
                            cellId = cellIdentity.cid,
                            level = cellSignalStrength.dbm
                        )
                    )
                } else if (cellInfo is CellInfoLte) {
                    val cellIdentity = cellInfo.cellIdentity
                    val cellSignalStrength = cellInfo.cellSignalStrength
                    results.add(
                        GsmResultV0(
                            mcc = cellIdentity.mcc,
                            mnc = cellIdentity.mnc,
                            lac = cellIdentity.tac,
                            cellId = cellIdentity.ci,
                            level = cellSignalStrength.dbm
                        )
                    )
                }
            }
        }

        return results
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверяем разрешения
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

            // Запрашиваем разрешения, если они еще не предоставлены
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE), PERMISSIONS_REQUEST_CODE)
        }

        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        osmConfig.osmdroidBasePath = getExternalFilesDir(null)
        osmConfig.osmdroidTileCache = File(osmConfig.osmdroidBasePath, "tile")

        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val button = Button(this).apply {
            text = "Detect"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val leftTextView = TextView(this).apply {
            text = ""
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val rightTextView = TextView(this).apply {
            text = "Detecting"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(leftTextView)

            switch = Switch(this@MainActivity).apply {
                isChecked = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        leftTextView.text = ""
                        rightTextView.text = "Detecting"
                        button.text = "Detect"
                    } else {
                        leftTextView.text = "Scanning"
                        rightTextView.text = ""
                        button.text = "Scan Wifi-list"
                    }
                }
            }

            addView(switch)
            addView(rightTextView)
        }

        val checkBox = CheckBox(this).apply {
            text = "Auto send"
        }

        val buttonShowAll = Button(this).apply {
            text = "Show all"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            addView(map)
            addView(button)
            addView(switchLayout)
            addView(checkBox)
            addView(buttonShowAll)
        }

        setContentView(layout)

        val handler = Handler(Looper.getMainLooper())
        val runnable: Runnable = object : Runnable {
            override fun run() {
                if (checkBox.isChecked) {
                    button.performClick()
                    handler.postDelayed(this, 2500)
                }
            }
        }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                handler.postDelayed(runnable, 2500)
            } else {
                handler.removeCallbacks(runnable)
            }
        }

        setContentView(layout)

        mapController = map.controller
        mapController.setZoom(9.0)
        mapController.setCenter(GeoPoint(48.8583, 2.2944))

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if ((ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED
                    ) || (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) ||
            (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                p0 ?: return
                // Обновлённое местоположение
                val updatedLocation = p0.lastLocation
                // Вы можете использовать обновлённое местоположение
                // ...
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        button.setOnClickListener {
            val wifiList = getWifiList()
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    return@addOnSuccessListener
                }
                if (!switch.isChecked) {
                    map.overlays.clear()
                    setPoint(location.latitude, location.longitude, setCenter = true)
                    val accuracyInMeters = location.accuracy
                    if (accuracyInMeters > 70) {
                        makeToast("Too inaccurate: $accuracyInMeters")
                        return@addOnSuccessListener
                    }
                }

                if (switch.isChecked) {
                    // detect
                    postDetect(wifiList, location)
                } else {
                    // scan
                    postDataToServer(wifiList, location)
                }
            }


        }

        buttonShowAll.setOnClickListener {
            getAllWifi()
        }
    }

    private fun makeToast(text: String) {
        val toast = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT)
        toast.show()
        Handler().postDelayed({ toast.cancel() }, 1000)
    }

    private fun getWifiList(): List<ScanResult> {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if ((ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED) && (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
        return wifiManager.scanResults;
    }
    data class ScanResultVO(
        val SSID: String,
        val BSSID: String,
        val capabilities: String,
        val frequency: Int,
        val level: Int,
        val lat: Double,
        val lon: Double
    ) {
        constructor(scanResult: ScanResult, location: Location) : this(
            SSID = scanResult.SSID,
            BSSID = scanResult.BSSID,
            capabilities = scanResult.capabilities,
            frequency = scanResult.frequency,
            level = scanResult.level,
            lat = location.latitude,
            lon = location.longitude
        )

        constructor(scanResult: ScanResult) : this(
            SSID = scanResult.SSID,
            BSSID = scanResult.BSSID,
            capabilities = scanResult.capabilities,
            frequency = scanResult.frequency,
            level = scanResult.level,
            lat = 0.0,
            lon = 0.0
        )
    }

    data class InputGps(
        val lat: Double,
        val lon: Double
    )
    data class CombinedScanResult(
        val wifiList: List<ScanResultVO>,
        val cellList: List<GsmResultV0>,
        val inputGps: InputGps
    ) {

    }

    data class GsmResultV0(
        val mcc: Int,
        val mnc: Int,
        val lac: Int,
        val cellId: Int,
        val level: Int,
        val lat: Double = 0.0,
        val lon: Double = 0.0
    ) {
    }


    data class LbsResponse(
        var count: String,
        var upd: String
    )

    private fun postDataToServer(wifiList: List<ScanResult>, location: Location?) {
        if (location == null) {
            return
        }
        val client = OkHttpClient.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://kmekhovichlbs.pythonanywhere.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val wifiScanResultVOList = wifiList.map { ScanResultVO(it, location) }
        val service = retrofit.create(ApiService::class.java)

        val call = service.sendWifiScanResult(wifiScanResultVOList)

        call.enqueue(object : retrofit2.Callback<LbsResponse> {
            override fun onResponse(call: retrofit2.Call<LbsResponse>, response: Response<LbsResponse>) {
                Log.e("r", response.toString())
                if (response.isSuccessful) {
                    val lbsResponse = response.body()

                    if (lbsResponse != null) {
                        makeToast("Scanned ${lbsResponse.count} new wifi. Updated ${lbsResponse.upd}")
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<LbsResponse>, t: Throwable) {
                makeToast("got error $t")
            }
        })
    }
    data class Wifi(
        var lat: String,
        var lon: String
    )

    data class Cell(
        var lat: String,
        var lon: String
    )

    private fun drawWifi(wifiList: List<Wifi>, color: Int = Color.BLUE) {
        for (wifi in wifiList) {
            val myLocation = GeoPoint(
                wifi.lat.toDouble(),
                wifi.lon.toDouble()
            )
            val myMarker = OverlayItem("Title", "Description", myLocation)
            val d = ShapeDrawable(OvalShape())
            d.intrinsicHeight = 10
            d.intrinsicWidth = 10
            d.paint.color = color
            myMarker.setMarker(d)
            val markersOverlay = ItemizedOverlayWithFocus<OverlayItem>(
                this,
                listOf(myMarker),
                object :
                    ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(
                        index: Int,
                        item: OverlayItem
                    ): Boolean {
                        // Реакция на одиночный тап по маркеру
                        return true
                    }

                    override fun onItemLongPress(
                        index: Int,
                        item: OverlayItem
                    ): Boolean {
                        // Реакция на долгий тап по маркеру
                        return false
                    }
                }
            )
            map.overlays.add(markersOverlay)
        }
    }

    private fun drawCells(cellList: List<Cell>, color: Int = Color.BLACK) {
        for (cell in cellList) {
            val myLocation = GeoPoint(
                cell.lat.toDouble(),
                cell.lon.toDouble()
            )
            val myMarker = OverlayItem("Title", "Description", myLocation)
            val d = ShapeDrawable(OvalShape())
            d.intrinsicHeight = 50
            d.intrinsicWidth = 50
            d.paint.color = color
            myMarker.setMarker(d)
            val markersOverlay = ItemizedOverlayWithFocus<OverlayItem>(
                this,
                listOf(myMarker),
                object :
                    ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(
                        index: Int,
                        item: OverlayItem
                    ): Boolean {
                        // Реакция на одиночный тап по маркеру
                        return true
                    }

                    override fun onItemLongPress(
                        index: Int,
                        item: OverlayItem
                    ): Boolean {
                        // Реакция на долгий тап по маркеру
                        return false
                    }
                }
            )
            map.overlays.add(markersOverlay)
        }
    }

    data class LocationResponse(
        var lat: String,
        var lon: String,
        var count: String,
        var wifi: List<Wifi>,
        var cells: List<Cell>
    )

    private fun Distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000
        val f1 = lat1 * PI / 180.0
        val f2 = lat2 * PI / 180.0
        val delta_f = (lat2 - lat1) * PI / 180.0
        val delta_l = (lon2 - lon2) * PI / 180.0
        val a = sin(delta_f / 2) * sin(delta_f / 2) + cos(f1) * cos(f2) * (sin(delta_l / 2) * sin(delta_l / 2))
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun postDetect(wifiList: List<ScanResult>, location: Location) {
        val client = OkHttpClient.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://kmekhovichlbs.pythonanywhere.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val wifiScanResultVOList = wifiList.map { ScanResultVO(it) }
        val cellTowersResultV0List = scanCellTowers()
        val service = retrofit.create(ApiService::class.java)

        val combinedScanResult = CombinedScanResult(wifiScanResultVOList, cellTowersResultV0List, InputGps(location.latitude, location.longitude))
        val call = service.sendDetect(combinedScanResult)

        call.enqueue(object : retrofit2.Callback<LocationResponse> {
            override fun onResponse(call: retrofit2.Call<LocationResponse>, response: Response<LocationResponse>) {
                Log.e("r", response.toString())
                if (response.isSuccessful) {
                    val locationResponse = response.body()

                    if (locationResponse != null) {
                        val lat = locationResponse.lat.toDouble()
                        val lon = locationResponse.lon.toDouble()

                        map.overlays.clear()
                        setPoint(location.latitude, location.longitude, setCenter = !switch.isChecked)
                        setPoint(lat, lon, Color.YELLOW, setCenter = true)

                        makeToast("Location based on ${locationResponse.count} wifi. Error is %.2f meters. Found ${locationResponse.cells.size} cells".format(Distance(lat, lon, location.latitude, location.longitude)))
                        drawWifi(locationResponse.wifi.toList())
                        drawCells(locationResponse.cells.toList())
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<LocationResponse>, t: Throwable) {
                makeToast("got error $t")
            }
        })
    }

    private fun getAllWifi() {
        val client = OkHttpClient.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://kmekhovichlbs.pythonanywhere.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val service = retrofit.create(ApiService::class.java)

        val call = service.getAllWifi()

        call.enqueue(object : retrofit2.Callback<LocationResponse> {
            override fun onResponse(call: retrofit2.Call<LocationResponse>, response: Response<LocationResponse>) {
                if (response.isSuccessful) {
                    val locationResponse = response.body()

                    if (locationResponse != null) {
                        var wifiList = locationResponse.wifi.toList()
                        makeToast("Got ${wifiList.size} wifis")
                        drawWifi(wifiList, Color.GREEN)
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<LocationResponse>, t: Throwable) {
                makeToast("got error $t")
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if(this::map.isInitialized) {
            map.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if(this::map.isInitialized) {
            map.onResume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(this::map.isInitialized) {
            map.onDetach()
        }
    }

    interface ApiService {
        @POST("lbs")
        fun sendWifiScanResult(@Body wifiList: List<ScanResultVO>): retrofit2.Call<LbsResponse>

        @POST("detect")
        fun sendDetect(@Body combinedScanResult: CombinedScanResult): retrofit2.Call<LocationResponse>

        @GET("get_all")
        fun getAllWifi(): retrofit2.Call<LocationResponse>
    }

}
