package com.elkhouly.commons.extensions

import android.content.Context
import com.elkhouly.commons.models.FileDirItem

fun FileDirItem.isRecycleBinPath(context: Context): Boolean {
    return path.startsWith(context.recycleBinPath)
}
