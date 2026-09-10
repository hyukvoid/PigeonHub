package com.pigeonhub.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a url in an external browser. Defense in depth: the payload validator
 * already enforces https, but anything leaving the app re-checks it here so no
 * call site can accidentally open an arbitrary scheme.
 */
object UrlOpener {

    fun open(context: Context, rawUrl: String): Boolean {
        val uri = Uri.parse(rawUrl)
        if (uri.scheme?.lowercase() != "https") return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
