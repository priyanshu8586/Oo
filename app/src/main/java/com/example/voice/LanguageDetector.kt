package com.example.voice

object LanguageDetector {
  private val devanagariRegex = Regex("[\\u0900-\\u097F]")
  private val hinglishWords =
    setOf(
      "kya",
      "hai",
      "hain",
      "kaise",
      "kaisa",
      "kaisi",
      "baje",
      "samay",
      "aaj",
      "kal",
      "karo",
      "kholo",
      "ruko",
      "band",
      "bolo",
      "batao",
      "theek",
      "nahi",
      "haan",
      "shukriya",
      "dhanyavaad",
      "namaste",
      "kitne",
      "bhasha",
      "badlo",
      "dobara",
      "mera",
      "meri",
      "tum",
      "aap",
      "yaar",
      "bhai",
      "abhi",
      "minute",
    )

  fun detectLocaleTag(text: String): String {
    if (devanagariRegex.containsMatchIn(text)) {
      return "hi-IN"
    }
    val words =
      text
        .lowercase()
        .split(Regex("\\s+"))
        .map { it.replace(Regex("[^a-z]"), "") }
        .filter { it.isNotEmpty() }
    val hasHindiWord = words.any { it in hinglishWords }
    return if (hasHindiWord) "hi-IN" else "en-IN"
  }
}
