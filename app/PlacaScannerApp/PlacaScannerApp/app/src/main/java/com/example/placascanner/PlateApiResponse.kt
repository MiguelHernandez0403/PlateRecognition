package com.example.placascanner

/**
 * Representa la respuesta JSON del endpoint /predict/ de la FastAPI:
 * {
 *   "success": true,
 *   "placas": ["ABC123"],
 *   "num_placas": 1,
 *   "image": "base64...",
 *   "message": "OK"
 * }
 */
data class PlateApiResponse(
    val placas: List<String>,
    val message: String,
    val imageBase64: String?
)
