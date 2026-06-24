package com.gmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.gmap.data.HostEntity
import com.gmap.data.NetDatabase
import kotlinx.coroutines.flow.Flow

class NetViewModel(application: Application) : AndroidViewModel(application) {
    private val db = NetDatabase.getDatabase(application)
    private val dao = db.netDao()

    val allHosts: Flow<List<HostEntity>> = dao.getAllHosts()
}
