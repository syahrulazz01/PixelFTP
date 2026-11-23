package com.example.pixelftp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.pixelftp.ui.FtpScreen
import com.example.pixelftp.ui.filemanager.LaptopFileManagerScreen
import com.example.pixelftp.ui.theme.PixelFTPTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelFTPTheme {
                var selectedTab by remember { mutableIntStateOf(1) } // Default to "Jelajahi Laptop" (tab 1)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = "Server HP") },
                                label = { Text("Server HP (FTP)") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Computer, contentDescription = "Jelajahi Laptop") },
                                label = { Text("Jelajahi Laptop") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (selectedTab) {
                            0 -> FtpScreen()
                            1 -> LaptopFileManagerScreen()
                        }
                    }
                }
            }
        }
    }
}
