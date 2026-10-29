package com.jas.montaj.core

import com.jas.montaj.platform.ExportedVideo

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(val progress: Float) : ExportState
    data class Finished(val video: ExportedVideo) : ExportState
}
