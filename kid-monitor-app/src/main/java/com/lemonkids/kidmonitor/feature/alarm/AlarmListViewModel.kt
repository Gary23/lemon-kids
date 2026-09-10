package com.lemonkids.kidmonitor.feature.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lemonkids.kidmonitor.alarm.AlarmDao
import com.lemonkids.kidmonitor.alarm.DeviceAlarmEntity
import com.lemonkids.kidmonitor.alarm.RemoteAlarmSyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 只读取本机已下发的闹钟，不直接展示云端尚未部署的配置。 */
@HiltViewModel
class AlarmListViewModel @Inject constructor(
    alarmDao: AlarmDao,
    private val syncCoordinator: RemoteAlarmSyncCoordinator
) : ViewModel() {
    val alarms: StateFlow<List<DeviceAlarmEntity>> = alarmDao.observeEffectiveAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch { syncCoordinator.sync() }
    }
}
