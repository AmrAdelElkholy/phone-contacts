package com.elkhouly.dialer.models

sealed class Events {
    data object RefreshCallLog : Events()
}
