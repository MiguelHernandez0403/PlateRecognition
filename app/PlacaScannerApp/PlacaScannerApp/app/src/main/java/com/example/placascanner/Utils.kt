package com.example.placascanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File

/**
 * Crea un archivo temporal en el cache de la app y devuelve tanto su
 * Uri (para pasársela a la cámara vía FileProvider) como el File
 * (para poder leerlo luego y subirlo por HTTP).
 */
fun createImageFileAndUri(context: Context): Pair<Uri, File> {
    val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(imagesDir, "placa_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    return uri to file
}

/**
 * Decodifica el string base64 que devuelve la FastAPI (campo "image")
 * en un Bitmap para mostrarlo en pantalla.
 */
fun decodeBase64ToBitmap(base64: String): Bitmap? {
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
