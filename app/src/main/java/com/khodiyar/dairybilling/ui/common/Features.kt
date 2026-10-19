package com.khodiyar.dairybilling.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.khodiyar.dairybilling.data.prefs.Features

/** The open dairy's feature switches (Settings → App features), provided by MainActivity. */
val LocalFeatures = compositionLocalOf { Features() }
