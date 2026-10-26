package com.elkhouly.commons.compose.extensions

import android.content.Context
import com.elkhouly.commons.helpers.BaseConfig

val Context.config: BaseConfig get() = BaseConfig.newInstance(applicationContext)
