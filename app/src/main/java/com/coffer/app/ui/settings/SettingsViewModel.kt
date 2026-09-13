package com.coffer.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.repository.BackupRepository
import com.coffer.app.data.settings.CurrencyPreferences
import com.coffer.app.domain.Currency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val currencyPreferences: CurrencyPreferences,
    private val backupRepository: BackupRepository
) : ViewModel() {

    val currency: StateFlow<Currency> = currencyPreferences.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Currency.DEFAULT)

    fun setCurrency(currency: Currency) {
        viewModelScope.launch { currencyPreferences.setCurrency(currency) }
    }

    fun exportBackup(context: Context, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val json = backupRepository.exportToJson()
            val uri = withContext(Dispatchers.IO) { writeBackupFile(context, json) }
            onReady(uri)
        }
    }

    fun restoreBackup(context: Context, uri: Uri, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (json == null) {
                onDone(false)
                return@launch
            }
            val success = runCatching { backupRepository.importFromJson(json) }.isSuccess
            onDone(success)
        }
    }

    private fun writeBackupFile(context: Context, json: String): Uri {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "coffer-backup-$stamp.json")
        file.writeText(json)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
