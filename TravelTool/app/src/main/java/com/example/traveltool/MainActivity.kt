package com.example.traveltool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.example.traveltool.data.ThemeStore
import com.example.traveltool.navigation.TravelToolNavHost
import com.example.traveltool.ui.theme.TravelToolTheme

class MainActivity : ComponentActivity() {

    /** Mutable state so theme changes are reflected immediately. */
    internal var themeChoice by mutableStateOf(ThemeStore.ThemeChoice.DRACULA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeChoice = ThemeStore.getTheme(this)
        enableEdgeToEdge()
        setContent {
            TravelToolTheme(themeChoice = themeChoice) {
                val navController = rememberNavController()
                TravelToolNavHost(navController)
            }
        }
    }
}