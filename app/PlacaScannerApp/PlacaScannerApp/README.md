# Detector de Placas — App Android (Kotlin)

App nativa en Kotlin (Jetpack Compose) que:
1. Abre la cámara del dispositivo para tomar una foto de un vehículo.
2. Envía la foto por HTTP (multipart) al endpoint `POST /predict/` de tu FastAPI (`app.py`).
3. Muestra el texto de la(s) placa(s) detectada(s) y la imagen procesada que devuelve el backend.

Backend configurado: `http://184.194.163.240:8080`

## Cómo abrir el proyecto

1. Abre **Android Studio** (Koala/Ladybug o más reciente).
2. `File > Open...` → selecciona la carpeta `PlacaScannerApp`.
3. Deja que Android Studio sincronice Gradle. Como el `gradle-wrapper.jar` no viene incluido,
   Android Studio te ofrecerá regenerarlo automáticamente al abrir el proyecto (o puedes ejecutar
   `gradle wrapper` una vez si tienes Gradle instalado localmente).
4. Conecta un dispositivo Android físico (recomendado, para poder usar la cámara real) o un
   emulador con cámara virtual habilitada.
5. Ejecuta ▶️ (Run 'app').

## Estructura del proyecto

```
PlacaScannerApp/
├── app/
│   ├── build.gradle.kts          # Dependencias: Compose, OkHttp, coroutines
│   └── src/main/
│       ├── AndroidManifest.xml   # Permisos CAMERA/INTERNET, FileProvider
│       ├── java/com/example/placascanner/
│       │   ├── MainActivity.kt       # UI: botón tomar foto, enviar, resultados
│       │   ├── ApiClient.kt          # Llamada HTTP multipart a /predict/
│       │   ├── PlateApiResponse.kt   # Modelo de la respuesta JSON
│       │   └── Utils.kt              # Crear Uri de foto / decodificar base64
│       └── res/xml/
│           ├── file_paths.xml               # Rutas permitidas para el FileProvider
│           └── network_security_config.xml  # Permite HTTP (no HTTPS) solo hacia esa IP
```

## Puntos importantes

- **HTTP en texto plano**: como tu FastAPI corre en `http://184.194.163.240:8080` (sin HTTPS),
  Android bloquea ese tráfico por defecto desde Android 9. Por eso el proyecto incluye
  `network_security_config.xml`, que habilita cleartext **solo** para esa IP. Si más adelante
  pones un dominio con HTTPS delante (recomendado, p. ej. con Nginx + Let's Encrypt), puedes
  quitar esa excepción.
- **Permiso de cámara**: la app pide el permiso `CAMERA` en tiempo de ejecución antes de abrir
  la cámara del sistema.
- **FileProvider**: la foto se guarda en el caché de la app y se comparte con la app de cámara
  mediante un `content://` URI seguro (buena práctica en Android moderno, en vez de exponer
  rutas `file://`).
- **Campo del formulario**: la app sube la imagen con el campo `file`, que es exactamente lo que
  espera tu backend: `file: Optional[UploadFile] = File(None)` en `/predict/`.
- **CORS**: el `allow_origins=["*"]` de tu FastAPI no afecta a esta app (CORS es una restricción
  de navegadores web, no de apps nativas Android), así que no necesitas tocar eso para que la
  app funcione.
- **IP fija**: la URL del backend está en `ApiClient.kt` (`BASE_URL`). Si tu servidor cambia de
  IP o pasas a un dominio, solo tienes que actualizar esa constante.
- **Red del teléfono**: el celular donde corra la app necesita poder alcanzar `184.194.163.240:8080`
  por la red (misma red local, VPN, o que el puerto esté expuesto públicamente). Si usas datos
  móviles y la IP es privada/local, no va a conectar.

## Próximos pasos sugeridos

- Añadir manejo de reintentos / mensajes más claros si el servidor tarda en responder (el modelo
  YOLO + OCR puede tardar varios segundos en la primera petición).
- Mostrar un ícono de la app (`android:icon`) y splash screen personalizados.
- Si vas a publicar la app, mover `BASE_URL` a algo configurable (BuildConfig o pantalla de ajustes)
  y pasar el backend a HTTPS.
