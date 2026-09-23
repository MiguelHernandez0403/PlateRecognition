package com.example.placascanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlacaScannerScreen()
                }
            }
        }
    }
}

@Composable
fun PlacaScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var placas by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun resetResult() {
        resultBitmap = null
        placas = emptyList()
        message = ""
        errorMsg = null
    }

    fun launchCameraFlow(launcher: androidx.activity.result.ActivityResultLauncher<Uri>) {
        val (uri, file) = createImageFileAndUri(context)
        photoUri = uri
        photoFile = file
        launcher.launch(uri)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            resetResult()
            photoUri?.let { uri ->
                previewBitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
        } else {
            errorMsg = "No se tomó ninguna foto."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraFlow(takePictureLauncher)
        } else {
            errorMsg = "Se necesita permiso de cámara para tomar la foto."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Detector de Placas", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            val permission = Manifest.permission.CAMERA
            if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCameraFlow(takePictureLauncher)
            } else {
                cameraPermissionLauncher.launch(permission)
            }
        }) {
            Text("📷 Tomar foto")
        }

        Spacer(Modifier.height(16.dp))

        previewBitmap?.let { bmp ->
            Text("Foto capturada:", style = MaterialTheme.typography.titleSmall)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Foto tomada",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !isLoading,
                onClick = {
                    val file = photoFile ?: return@Button
                    isLoading = true
                    errorMsg = null
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.uploadPhoto(file)
                            }
                            placas = response.placas
                            message = response.message
                            resultBitmap = response.imageBase64?.let { decodeBase64ToBitmap(it) }
                        } catch (e: Exception) {
                            errorMsg = "Error al conectar con el servidor: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text(if (isLoading) "Enviando..." else "🚀 Enviar al servidor")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }

        errorMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
        }

        if (placas.isNotEmpty()) {
            Text("Placas detectadas:", style = MaterialTheme.typography.titleMedium)
            placas.forEach { placa ->
                Text("• $placa", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }

        resultBitmap?.let { bmp ->
            Spacer(Modifier.height(16.dp))
            Text("Imagen procesada:", style = MaterialTheme.typography.titleSmall)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Resultado procesado",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
