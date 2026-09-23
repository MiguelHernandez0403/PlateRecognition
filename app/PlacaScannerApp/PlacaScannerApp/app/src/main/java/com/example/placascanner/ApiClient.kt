package com.example.placascanner

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP que habla con la FastAPI (app.py) montada en
 * http://184.194.163.240:8080
 *
 * Usa el endpoint POST /predict/ que acepta la imagen como multipart
 * (campo "file"), tal como espera app.py:
 *   file: Optional[UploadFile] = File(None)
 */
object ApiClient {

    // Cambia esto si tu backend cambia de IP/puerto.
    private const val BASE_URL = "http://184.194.163.240:8080"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Sube la foto al endpoint /predict/. Debe llamarse desde un hilo
     * de fondo (Dispatchers.IO), ya que hace una llamada de red bloqueante.
     */
    @Throws(IOException::class)
    fun uploadPhoto(file: File): PlateApiResponse {
        val mediaType = "image/jpeg".toMediaTypeOrNull()
        val requestBody = file.asRequestBody(mediaType)

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestBody)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/predict/")
            .post(multipartBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
                ?: throw IOException("El servidor devolvió una respuesta vacía")

            if (!response.isSuccessful) {
                throw IOException("Error del servidor (HTTP ${response.code}): $bodyStr")
            }

            return parseResponse(bodyStr)
        }
    }

    private fun parseResponse(json: String): PlateApiResponse {
        val obj = JSONObject(json)

        if (obj.has("error")) {
            throw IOException(obj.getString("error"))
        }

        val placasArray = obj.optJSONArray("placas")
        val placas = mutableListOf<String>()
        if (placasArray != null) {
            for (i in 0 until placasArray.length()) {
                placas.add(placasArray.getString(i))
            }
        }

        val message = obj.optString("message", "")
        val imageBase64 = if (obj.has("image") && !obj.isNull("image")) {
            obj.getString("image")
        } else null

        return PlateApiResponse(placas, message, imageBase64)
    }
}
