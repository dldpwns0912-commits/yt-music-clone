package com.ytmusic.core.extractor.updater

import com.ytmusic.core.extractor.model.ExtractorVersionInfo
import com.ytmusic.core.extractor.model.UpdateStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ExtractorUpdateManager {
    fun getCurrentVersion(): String
    fun getBinaryFile(): File
    fun isBinaryAvailable(): Boolean
    fun checkForUpdates(): Flow<UpdateStatus>
    fun downloadAndInstall(versionInfo: ExtractorVersionInfo): Flow<UpdateStatus>
}
