package com.example

import com.example.voice.CommandProcessor
import com.example.voice.LanguageDetector
import com.example.voice.LocalCommandAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandProcessorTest {
  @Test
  fun detectsHindiAndHinglishCorrectly() {
    assertEquals("hi-IN", LanguageDetector.detectLocaleTag("नमस्ते जार्विस"))
    assertEquals("hi-IN", LanguageDetector.detectLocaleTag("Aaj mausam kaisa hai"))
    assertEquals("en-IN", LanguageDetector.detectLocaleTag("Tell me a joke about space"))
  }

  @Test
  fun matchesLocalCommandsBeforeGemini() {
    assertTrue(CommandProcessor.match("stop") is LocalCommandAction.Stop)
    assertTrue(CommandProcessor.match("ruko") is LocalCommandAction.Stop)
    assertTrue(CommandProcessor.match("repeat that") is LocalCommandAction.Repeat)
    assertTrue(CommandProcessor.match("change language") is LocalCommandAction.ChangeLanguage)
    assertTrue(CommandProcessor.match("what's the time") is LocalCommandAction.SpeakOnly)

    val openYt = CommandProcessor.match("open YouTube")
    assertTrue(openYt is LocalCommandAction.OpenUrl)
    assertEquals("https://www.youtube.com", (openYt as LocalCommandAction.OpenUrl).url)

    val timer = CommandProcessor.match("set a timer for 5 minutes")
    assertTrue(timer is LocalCommandAction.StartTimer)
    assertEquals(5.0, (timer as LocalCommandAction.StartTimer).minutes, 0.01)

    assertTrue(
      CommandProcessor.match("Explain black holes simply") is LocalCommandAction.NotMatched,
    )
  }
}
