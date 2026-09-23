package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.YouTubeSimulatorScreen
import com.example.ui.theme.MaxDarkBg
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSurface
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MaxViewModel

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: MaxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MaxViewModel) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Request Runtime Permissions for Voice, Camera & Location
    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.addLog("ऑडियो रिकॉर्डिंग अनुमति प्राप्त हुई।")
        }
        if (permissions[Manifest.permission.CAMERA] == true) {
            viewModel.addLog("कैमरा अनुमति प्राप्त हुई।")
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            multiPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    val navItems = listOf(
        NavTabItem("Max Hub", Icons.Default.Home, "nav_dashboard"),
        NavTabItem("YouTube", Icons.Default.PlayCircle, "nav_youtube"),
        NavTabItem("Memory", Icons.Default.Memory, "nav_memory"),
        NavTabItem("Settings", Icons.Default.Settings, "nav_settings")
    )

    Scaffold(
        bottomBar = {
            // Hide bottom bar when inside YouTube simulator for immersion
            if (selectedTab != 1) {
                NavigationBar(
                    containerColor = MaxSurface,
                    contentColor = TextPrimary
                ) {
                    navItems.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = if (isSelected) MaxPrimary else TextSecondary
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    color = if (isSelected) MaxPrimary else TextSecondary
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaxPrimary.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        },
        containerColor = MaxDarkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        when (selectedTab) {
            0 -> DashboardScreen(
                viewModel = viewModel,
                onNavigateToSimulator = { selectedTab = 1 },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> YouTubeSimulatorScreen(
                viewModel = viewModel,
                onNavigateBack = { selectedTab = 0 }
            )
            2 -> MemoryScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            3 -> SettingsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

