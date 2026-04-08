package com.example.moneysplitter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.moneysplitter.data.TripData
import com.example.moneysplitter.data.TripRepository
import com.example.moneysplitter.data.TripSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TripRepository(application)

    private val _trips = MutableStateFlow<List<TripSummary>>(emptyList())
    val trips: StateFlow<List<TripSummary>> = _trips.asStateFlow()

    init {
        loadTrips()
    }

    fun loadTrips() {
        _trips.value = repository.listTrips()
    }

    fun createTrip(name: String): String {
        val trip = TripData(name = name)
        val fileName = repository.generateFileName(name)
        repository.saveTrip(trip, fileName)
        loadTrips()
        return fileName
    }

    fun deleteTrip(fileName: String) {
        repository.deleteTrip(fileName)
        loadTrips()
    }
}
