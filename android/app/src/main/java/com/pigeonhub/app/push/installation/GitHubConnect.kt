package com.pigeonhub.app.push.installation

import java.net.URL

/** GitHub App connection constants and browser-launch helper. */
object GitHubConnect {

    // The GitHub App slug is derived from the app name on GitHub.
    // Change this if the app is renamed.
    const val GITHUB_APP_SLUG = "pigeonhub-dev"

    /** URL that opens the GitHub App installation page (select repos → install). */
    fun installUrl(): String = "https://github.com/apps/$GITHUB_APP_SLUG/installations/new"
}
