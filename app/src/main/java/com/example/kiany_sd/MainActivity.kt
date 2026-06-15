package com.example.kiany_sd

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.util.Timer
import java.util.TimerTask

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences

    companion object {
        @Volatile
        var isBusAlertValidated: Boolean = false

        // Helper functions to save and load the WiFi list as JSON in Prefs
        fun getSavedSSIDs(context: Context): List<String> {
            val p = context.getSharedPreferences("kiany_prefs", Context.MODE_PRIVATE)
            val jsonString = p.getString("wifi_triggers", null) ?: return listOf("kavim") // default
            val list = mutableListOf<String>()
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
            } catch (e: Exception) {}
            return list
        }

        fun saveSSIDs(context: Context, list: List<String>) {
            val p = context.getSharedPreferences("kiany_prefs", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            list.take(5).forEach { jsonArray.put(it) } // max 5
            p.edit().putString("wifi_triggers", jsonArray.toString()).apply()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("kiany_prefs", Context.MODE_PRIVATE)

        setContent {
            val context = LocalContext.current
            var isRunning by remember { mutableStateOf(false) }
            var statusText by remember { mutableStateOf("System Offline. Tap start before boarding.") }

            var selectedAppName by remember { mutableStateOf(prefs.getString("selected_app_name", "Choose App...") ?: "Choose App...") }
            var expanded by remember { mutableStateOf(false) }
            val installedApps = remember { getInstalledAppsList(context) }

            // Dynamic WiFi list management UI state
            var wifiList by remember { mutableStateOf(getSavedSSIDs(context)) }
            var newWifiName by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                isRunning = isServiceRunning(context, WifiBackgroundService::class.java)
                if (isRunning) {
                    statusText = "Monitoring active 24/7. Ready."
                }
                if (!hasUsageStatsPermission(context)) {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
            }

            val bgLocationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    isRunning = true
                    statusText = "Monitoring active 24/7. Ready."
                    startWifiService(context)
                } else {
                    statusText = "Error: Background location required!"
                }
            }

            val initialPermissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        isRunning = true
                        statusText = "Monitoring active 24/7. Ready."
                        startWifiService(context)
                    }
                } else {
                    statusText = "Error: Permissions denied!"
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("KianySD - BUS MONITOR", fontWeight = FontWeight.Bold, color = Color.White) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xff1E293B))
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xff0F172A))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Icon(
                                imageVector = if (isRunning) Icons.Rounded.Security else Icons.Rounded.GppBad,
                                contentDescription = "Status",
                                modifier = Modifier.size(80.dp),
                                tint = if (isRunning) Color(0xff4ADE80) else Color(0xffF87171)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = statusText, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 16.sp, color = Color.White)
                        }

                        // App Selection Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xff1E293B))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Select app to open for muting alerts:", color = Color.Gray, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box {
                                        Button(
                                            onClick = { expanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xff334155))
                                        ) {
                                            Text(selectedAppName, color = Color.White, fontSize = 16.sp)
                                        }

                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.heightIn(max = 300.dp)
                                        ) {
                                            installedApps.forEach { appInfo ->
                                                DropdownMenuItem(
                                                    text = { Text(appInfo.label) },
                                                    onClick = {
                                                        selectedAppName = appInfo.label
                                                        expanded = false
                                                        prefs.edit()
                                                            .putString("selected_app_name", appInfo.label)
                                                            .putString("selected_app_package", appInfo.packageName)
                                                            .apply()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // WiFi SSID Manager Card (Max 5)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xff1E293B))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("WiFi Network Triggers (${wifiList.size}/5):", color = Color.Gray, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Existing WiFi List
                                    wifiList.forEach { ssid ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(ssid, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                            IconButton(onClick = {
                                                val updatedList = wifiList.toMutableList().apply { remove(ssid) }
                                                wifiList = updatedList
                                                saveSSIDs(context, updatedList)
                                            }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xffF87171))
                                            }
                                        }
                                    }

                                    // Add New WiFi Input Row (Visible only if less than 5)
                                    if (wifiList.size < 5) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = newWifiName,
                                                onValueChange = { newWifiName = it },
                                                label = { Text("Network Name (e.g., egged)") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xff38BDF8),
                                                    unfocusedBorderColor = Color(0xff334155),
                                                    focusedLabelColor = Color(0xff38BDF8)
                                                )
                                            )
                                            Button(
                                                onClick = {
                                                    if (newWifiName.isNotBlank()) {
                                                        val cleanName = newWifiName.trim().lowercase()
                                                        if (!wifiList.contains(cleanName)) {
                                                            val updatedList = wifiList.toMutableList().apply { add(cleanName) }
                                                            wifiList = updatedList
                                                            saveSSIDs(context, updatedList)
                                                        }
                                                        newWifiName = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff38BDF8))
                                            ) {
                                                Text("ADD", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Main Monitoring Toggle Button
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (!isRunning) {
                                        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                        } else true

                                        if (!hasLocation || !hasNotifications) {
                                            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).apply {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                                            }.toTypedArray()
                                            initialPermissionsLauncher.launch(perms)
                                        } else {
                                            isRunning = true
                                            statusText = "Monitoring active 24/7. Ready."
                                            isBusAlertValidated = false
                                            prefs.edit().putBoolean("is_validated", false).apply()
                                            startWifiService(context)
                                        }
                                    } else {
                                        isRunning = false
                                        statusText = "System Offline. Tap start before boarding."
                                        stopWifiService(context)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color(0xffF87171) else Color(0xff38BDF8))
                            ) {
                                Text(if (isRunning) "STOP MONITORING" else "START MONITORING", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getInstalledAppsList(context: Context): List<AppInfoData> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val filteredList = mutableListOf<AppInfoData>()

        for (app in apps) {
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                val label = app.loadLabel(pm).toString()
                filteredList.add(AppInfoData(label, app.packageName))
            }
        }
        return filteredList.sortedBy { it.label.lowercase() }
    }

    private fun startWifiService(context: Context) {
        val intent = Intent(context, WifiBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    private fun stopWifiService(context: Context) {
        context.stopService(Intent(context, WifiBackgroundService::class.java))
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }
}

data class AppInfoData(val label: String, val packageName: String)

class WifiBackgroundService : Service() {
    private val BUS_ALERT_CHANNEL_ID = "kiany_bus_silent_v13"
    private val SERVICE_FOREGROUND_CHANNEL_ID = "kiany_service_silent_background_v1"
    private val NOTIFICATION_ID = 999

    private var wifiManager: WifiManager? = null
    private var timer: Timer? = null
    private lateinit var prefs: SharedPreferences

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkWifiAndForegroundApp()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("kiany_prefs", Context.MODE_PRIVATE)
        createNotificationChannels()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, SERVICE_FOREGROUND_CHANNEL_ID)
            .setContentTitle("KianySD Active")
            .setContentText("Monitoring networks and target apps...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        startScanningLoop()
        return START_STICKY
    }

    private fun startScanningLoop() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    checkForegroundApp()

                    if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        @Suppress("DEPRECATION")
                        wifiManager?.startScan()
                    }
                } catch (e: Exception) {}
            }
        }, 0, 5000)
    }

    private fun checkWifiAndForegroundApp() {
        try {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val results = wifiManager?.scanResults ?: return

                // Load the dynamic WiFi trigger items customized by the user
                val allowedTriggers = MainActivity.getSavedSSIDs(applicationContext)

                // Match scanned SSIDs with selected text keywords
                val foundTargetBus = results.any {
                    @Suppress("DEPRECATION")
                    val cleanSSID = it.SSID.replace("\"", "").lowercase()
                    allowedTriggers.any { trigger -> cleanSSID.contains(trigger) }
                }

                if (foundTargetBus) {
                    checkForegroundApp()

                    val isValidated = prefs.getBoolean("is_validated", false)
                    if (!isValidated && !MainActivity.isBusAlertValidated) {
                        sendBusAlertNotification()
                    }
                } else {
                    if (prefs.getBoolean("is_validated", false) || MainActivity.isBusAlertValidated) {
                        MainActivity.isBusAlertValidated = false
                        prefs.edit().putBoolean("is_validated", false).apply()
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun checkForegroundApp() {
        try {
            val targetPackage = prefs.getString("selected_app_package", "") ?: return
            if (targetPackage.isEmpty()) return

            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000 * 60

            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            if (stats != null && stats.isNotEmpty()) {
                val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                val currentForegroundPackage = sortedStats[0].packageName

                if (currentForegroundPackage == targetPackage) {
                    MainActivity.isBusAlertValidated = true
                    prefs.edit().putBoolean("is_validated", true).apply()

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(888)
                }
            }
        } catch (e: Exception) {}
    }

    private fun sendBusAlertNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val selectedApp = prefs.getString("selected_app_name", "Ticketing App")

        val alertNotification = NotificationCompat.Builder(this, BUS_ALERT_CHANNEL_ID)
            .setContentTitle("🚨 Bus Detected Nearby!")
            .setContentText("Open $selectedApp to validate and dismiss this alert.")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        notificationManager.notify(888, alertNotification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val busAlertChannel = NotificationChannel(BUS_ALERT_CHANNEL_ID, "Kiany Bus Silent Alerts V13", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(null, null)
                description = "Vibration notifications triggered when target vehicles are nearby."
            }
            manager.createNotificationChannel(busAlertChannel)

            val serviceChannel = NotificationChannel(SERVICE_FOREGROUND_CHANNEL_ID, "Kiany Active Background Service", NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                setSound(null, null)
                description = "Persistent background system indicator keeping tracking services active."
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timer?.cancel()
        try { unregisterReceiver(wifiReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }
}