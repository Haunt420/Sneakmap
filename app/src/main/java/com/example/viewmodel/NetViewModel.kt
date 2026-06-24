package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.HostEntity
import com.example.data.NetDatabase
import kotlinx.coroutines.flow.Flow

class NetViewModel(application: Application) : AndroidViewModel(application) {
    private val db = NetDatabase.getDatabase(application)
    private val dao = db.netDao()

    val allHosts: Flow<List<HostEntity>> = dao.getAllHosts()
}
