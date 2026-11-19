package com.example.data

import com.example.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
  data class Success(val text: String) : GeminiResult()

  data class Error(val spokenMessage: String) : GeminiResult()
}

class GeminiClient {
  companion object {
    const val GEMINI_MODEL = "gemini-2.5-flash"
    const val SYSTEM_INSTRUCTION =
      "You are Jarvis, a friendly, smart assistant. Reply briefly in the same language the user spoke. Keep answers short because they will be spoken aloud."
  }

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun generateReply(apiKey: String, history: List<ChatMessage>): GeminiResult =
    withContext(Dispatchers.IO) {
      val trimmedKey = apiKey.trim()
      if (trimmedKey.isEmpty()) {
        return@withContext GeminiResult.Error("Please set your Gemini API key first.")
      }

      val url =
        "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$trimmedKey"

      val contentsArray = JSONArray()
      for (msg in history.takeLast(10)) {
        val partObj = JSONObject().put("text", msg.text)
        val partsArray = JSONArray().put(partObj)
        val contentObj = JSONObject().put("role", msg.role).put("parts", partsArray)
        contentsArray.put(contentObj)
      }

      val payload =
        JSONObject()
          .put(
            "system_instruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_INSTRUCTION))),
          )
          .put("contents", contentsArray)
          .put(
            "generationConfig",
            JSONObject().put("temperature", 0.7).put("maxOutputTokens", 200),
          )

      val request =
        Request.Builder()
          .url(url)
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

      try {
        client.newCall(request).execute().use { response ->
          val bodyStr = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext when (response.code) {
              400, 401, 403 ->
                GeminiResult.Error("Your Gemini API key appears invalid. Tap the key button to update it.")
              429 -> GeminiResult.Error("Rate limit reached. Please wait a moment before asking again.")
              else -> GeminiResult.Error("I could not reach Gemini right now. Please try again.")
            }
          }

          val root = JSONObject(bodyStr)
          val candidates = root.optJSONArray("candidates")
          val firstCandidate = candidates?.optJSONObject(0)
          val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
          val textBuilder = StringBuilder()
          if (parts != null) {
            for (i in 0 until parts.length()) {
              val partText = parts.optJSONObject(i)?.optString("text").orEmpty()
              if (partText.isNotBlank()) {
                if (textBuilder.isNotEmpty()) textBuilder.append(" ")
                textBuilder.append(partText.trim())
              }
            }
          }
          val finalReply = textBuilder.toString().trim()
          if (finalReply.isEmpty()) {
            GeminiResult.Error("I did not receive a reply. Please try asking again.")
          } else {
            GeminiResult.Success(finalReply)
          }
        }
      } catch (_: IOException) {
        GeminiResult.Error("Network error while contacting Gemini. Please check your internet connection.")
      } catch (_: Exception) {
        GeminiResult.Error("Something went wrong while processing that request.")
      }
    }
}
