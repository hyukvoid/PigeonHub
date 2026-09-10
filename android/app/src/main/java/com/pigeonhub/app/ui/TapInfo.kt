package com.pigeonhub.app.ui

/** A notification tap routed into the UI (message id + optional https url). */
data class TapInfo(val messageId: String, val url: String?)
