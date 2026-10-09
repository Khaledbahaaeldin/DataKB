package io.github.khaledbahaaeldin.emberbyte.settings

import android.content.ContentResolver
import android.net.Uri
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

interface StreamOpener {
    fun output(uri: Uri): OutputStream
    fun input(uri: Uri): InputStream
}

class ContentResolverStreamOpener(private val contentResolver: ContentResolver) : StreamOpener {
    override fun output(uri: Uri): OutputStream =
        contentResolver.openOutputStream(uri) ?: throw FileNotFoundException("Could not open output stream for $uri")

    override fun input(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Could not open input stream for $uri")
}
