package com.my.mg.data

// Data class for Gitee Release API response
data class GiteeRelease(
    val tag_name: String,
    val body: String,
    val assets: List<GiteeAsset>
)

data class GiteeAsset(
    val browser_download_url: String,
    val name: String
)