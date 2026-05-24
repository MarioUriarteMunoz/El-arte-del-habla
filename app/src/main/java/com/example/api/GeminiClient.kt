package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.ExerciseOption
import com.example.data.FallbackData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun generateDailyWorkouts(): List<ExerciseOption> = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            Log.d(TAG, "No API key configured. Using local exercises.")
            return@withContext FallbackData.getRandomSelection()
        }

        val url = "${BASE_URL}models/gemini-3.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
        val systemInstruction = """
            Eres el motor de Inteligencia Artificial de "Conecta5", una aplicación de desarrollo personal y habilidades sociales para adultos.
            Debes generar exactamente 3 desafíos diarios diferentes de 5 minutos en español que potencien las habilidades sociales.
            Cada desafío debe pertenecer a una categoría DISTINTA seleccionada de estas 5 posibles:
            - Humor y Chispa (Hacer Reír)
            - Carisma y Conexión Social
            - El Arte del "Chisme" y Storytelling
            - Confianza y Desbloqueo Psicológico
            - Agilidad Mental

            Regresa únicamente un objeto JSON con la estructura indicada abajo. No respondas con marcas de código markdown como ```json. Responde estrictamente con el archivo raw JSON plano.
            FORMATO REQUERIDO:
            {
              "options": [
                {
                  "id": 1,
                  "category": "Humor y Chispa",
                  "emoji": "🎭",
                  "title": "El Rey de la Exageración",
                  "description": "Exagera un suceso mundano como si fuera de vida o muerte.",
                  "instructions": "Graba tu voz narrando tu desayuno como si fueras un gran emperador romano. Exagera el tostado y las texturas.",
                  "psychologicalTip": "Tip: Gesticular ampliamente ayuda a romper la autolimitación expresiva de forma juguetona."
                }
              ]
            }
        """.trimIndent()

        val prompt = "Genera 3 desafíos exclusivos de Conecta5 del día de hoy."

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.8)
            })
        }

        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Unsuccessful response from Gemini: ${response.code} ${response.message}")
                return@withContext FallbackData.getRandomSelection()
            }

            val bodyText = response.body?.string() ?: ""
            val jsonResponse = JSONObject(bodyText)
            val candidates = jsonResponse.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val text = firstCandidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")

            val workoutsObject = JSONObject(text.trim())
            val optionsArray = workoutsObject.getJSONArray("options")
            val list = mutableListOf<ExerciseOption>()
            for (i in 0 until optionsArray.length()) {
                val item = optionsArray.getJSONObject(i)
                list.add(
                    ExerciseOption(
                        id = item.optInt("id", i + 1),
                        category = item.getString("category"),
                        emoji = item.optString("emoji", "🎯"),
                        title = item.getString("title"),
                        description = item.getString("description"),
                        instructions = item.getString("instructions"),
                        psychologicalTip = item.getString("psychologicalTip")
                    )
                )
            }
            if (list.size >= 3) list else FallbackData.getRandomSelection()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating daily workouts from Gemini, falling back: ${e.message}", e)
            FallbackData.getRandomSelection()
        }
    }

    suspend fun generateDynamicFeedback(
        misionName: String,
        enfoque: String,
        psychTip: String,
        userText: String
    ): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext generateLocalFeedback(misionName, enfoque, psychTip, userText)
        }

        val url = "${BASE_URL}models/gemini-3.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
        val systemInstruction = """
            Eres el coach experto de Oratoria, Carisma y Relaciones Sociales de la aplicación "Conecta5" en español.
            Tu rol es validar el ejercicio del usuario de forma ultra motivadora y alegre.
            No evalúes, califiques, puntúes ni corrijas su grabación de audio o texto. ¡Solo celebra su atrevimiento y su valentía!
            
            Estructuración obligatoria de la respuesta:
            1. Un párrafo alegre y chispeante felicitando al usuario por completar el ejercicio. Sé audaz, maduro, divertido y lleno de energía.
            2. Darle un tip psicológico inmediato o un consejo complementario para usar en su vida cotidiana con desconocidos o amigos.
            3. Cerrar con la ficha exacta para archivar, formateada al final con el encabezado [FICHA DE ARCHIVO] obligatoriamente en este formato exacto:
            
            [FICHA DE ARCHIVO]
            - Misión: [Nombre de la misión]
            - Enfoque: [Nombre del enfoque]
            - Tip del día: [Tu tip psicológico clave de un solo enunciado]
            - Estado: Guardado en tu diario personal.
        """.trimIndent()

        val prompt = """
            Misión del usuario: $misionName
            Enfoque del ejercicio: $enfoque
            Tip psicológico inicial: $psychTip
            Ejercitación o texto del usuario: "${if (userText.isEmpty()) "[Audio registrado de 2 minutos]" else userText}"
            
            Dame la validación motivadora y la Ficha de Archivo al final.
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.85)
            })
        }

        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext generateLocalFeedback(misionName, enfoque, psychTip, userText)
            }
            val bodyText = response.body?.string() ?: ""
            val jsonResponse = JSONObject(bodyText)
            val candidates = jsonResponse.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            firstCandidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating dynamic feedback from Gemini API: ${e.message}", e)
            generateLocalFeedback(misionName, enfoque, psychTip, userText)
        }
    }

    private fun generateLocalFeedback(
        misionName: String,
        enfoque: String,
        psychTip: String,
        userText: String
    ): String {
        return """
            ¡Excelente misión cumplida! 🎉 Felicidades por dar el paso y atreverte a practicar hoy. Cada pequeña ejercitación de 5 minutos va reprogramando tus circuitos de confianza, reduciendo la ansiedad y ampliando tu abanico de tonalidades vocales y gestuales. ¡Sigue así, campeón de la elocuencia!
            
            Tip psicológico rápido: La seguridad no se siente antes de hablar; se genera durante la acción. Cuando sientas dudas en público, sonríe sutilmente y toma una respiración profunda desde el abdomen. Tu cerebro asimilará que estás a salvo y reducirá las señales de estrés en tu garganta.
            
            [FICHA DE ARCHIVO]
            - Misión: $misionName
            - Enfoque: $enfoque
            - Tip del día: $psychTip
            - Estado: Guardado en tu diario personal.
        """.trimIndent()
    }
}
