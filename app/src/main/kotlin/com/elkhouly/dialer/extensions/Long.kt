package com.elkhouly.dialer.extensions

import com.elkhouly.commons.extensions.toDayCode

fun Long.getDayCode(): String {
    return toDayCode("yyyy-MM-dd") // format helps with sorting in call log
}
