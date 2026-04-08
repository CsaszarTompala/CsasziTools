package com.example.moneysplitter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.moneysplitter.ui.screens.HomeScreen
import com.example.moneysplitter.ui.screens.SplashScreen
import com.example.moneysplitter.ui.screens.TripScreen
import com.example.moneysplitter.ui.theme.MoneySplitterTheme
import com.example.moneysplitter.viewmodel.HomeViewModel
import com.example.moneysplitter.viewmodel.TripViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneySplitterTheme {
                MoneySplitterApp()
            }
        }
    }
}

@Composable
fun MoneySplitterApp() {
    var currentRoute by remember { mutableStateOf<Route>(Route.Splash) }

    when (val route = currentRoute) {
        is Route.Splash -> {
            SplashScreen(
                onSplashFinished = { currentRoute = Route.Home }
            )
        }
        is Route.Home -> {
            val homeViewModel: HomeViewModel = viewModel()
            Box(modifier = Modifier.fillMaxSize()) {
                // Low-opacity background watermark
                Image(
                    painter = painterResource(id = R.drawable.main_page),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.06f),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onTripClick = { fileName ->
                        currentRoute = Route.Trip(fileName)
                    }
                )
            }
        }
        is Route.Trip -> {
            val tripViewModel: TripViewModel = viewModel(key = route.fileName)

            BackHandler {
                currentRoute = Route.Home
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Low-opacity background watermark
                Image(
                    painter = painterResource(id = R.drawable.main_page),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.06f),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
                TripScreen(
                    viewModel = tripViewModel,
                    fileName = route.fileName,
                    onBack = {
                        currentRoute = Route.Home
                    }
                )
            }
        }
    }
}

sealed class Route {
    data object Splash : Route()
    data object Home : Route()
    data class Trip(val fileName: String) : Route()
}
