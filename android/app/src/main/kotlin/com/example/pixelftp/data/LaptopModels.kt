package com.example.pixelftp.data

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long
)

data class ShortcutItem(
    val name: String,
    val path: String,
    val icon: String
)

data class DirectoryResponse(
    val currentPath: String,
    val parentPath: String?,
    val items: List<FileItem>
)

data class LaptopStatus(
    val hostname: String,
    val user: String,
    val os: String
)
