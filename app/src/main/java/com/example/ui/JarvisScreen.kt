package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.JarvisState
import com.example.model.JarvisUiState
import com.example.model.SpeakerRole
import com.example.model.TranscriptEntry
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBg
import com.example.ui.theme.JarvisBgDeep
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDanger
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisText
import com.example.ui.theme.JarvisTextDim

@Composable
fun JarvisRootScreen(viewModel: JarvisViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted) {
        viewModel.onReactorTapped()
      } else {
        viewModel.onPermissionDenied()
      }
    }

  val wakePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted) {
        viewModel.toggleAlwaysListening(true)
      } else {
        viewModel.onPermissionDenied()
      }
    }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFF0C1B33), JarvisBg, JarvisBgDeep),
            radius = 1200f,
          ),
        )
        .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    if (uiState.showSetupScreen) {
      BackHandler(enabled = uiState.allowCancelSetup) { viewModel.cancelKeySetup() }
      ApiKeySetupContent(
        allowCancel = uiState.allowCancelSetup,
        savedPreview = uiState.savedKeyPreview,
        onSave = { key -> viewModel.saveApiKey(key) },
        onCancel = { viewModel.cancelKeySetup() },
      )
    } else {
      JarvisHudContent(
        uiState = uiState,
        hasMicPermission = hasMicPermission,
        onRequestMicAndTap = {
          if (hasMicPermission) {
            viewModel.onReactorTapped()
          } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        },
        onToggleWake = { next ->
          if (!next) {
            viewModel.toggleAlwaysListening(false)
          } else if (hasMicPermission) {
            viewModel.toggleAlwaysListening(true)
          } else {
            wakePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        },
        onCycleLanguage = { viewModel.cycleLanguageMode(announce = false) },
        onChangeKey = { viewModel.openKeySetup() },
        onClearTranscript = { viewModel.clearTranscript() },
      )
    }
  }
}

@Composable
private fun ApiKeySetupContent(
  allowCancel: Boolean,
  savedPreview: String,
  onSave: (String) -> Boolean,
  onCancel: () -> Unit,
) {
  var keyInput by rememberSaveable { mutableStateOf("") }
  var showError by rememberSaveable { mutableStateOf(false) }

  Box(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = JarvisSurface),
      border = BorderStroke(1.dp, JarvisBorder),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          text = stringResource(R.string.setup_title),
          style = MaterialTheme.typography.headlineSmall,
          color = JarvisCyan,
        )
        Text(
          text = stringResource(R.string.setup_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = JarvisTextDim,
        )
        if (savedPreview.isNotEmpty()) {
          Text(
            text = "Current key: $savedPreview",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisCyan,
          )
        }
        OutlinedTextField(
          value = keyInput,
          onValueChange = {
            keyInput = it
            showError = false
          },
          label = { Text(stringResource(R.string.setup_key_label)) },
          placeholder = { Text(stringResource(R.string.setup_key_placeholder)) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          isError = showError,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = JarvisCyan,
              unfocusedBorderColor = JarvisBorder,
              focusedTextColor = JarvisText,
              unfocusedTextColor = JarvisText,
              focusedLabelColor = JarvisCyan,
              unfocusedLabelColor = JarvisTextDim,
            ),
          modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
        )
        if (showError) {
          Text(
            text = "Please paste a valid Gemini API key.",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisDanger,
          )
        }
        Text(
          text = stringResource(R.string.setup_help_text),
          style = MaterialTheme.typography.bodySmall,
          color = JarvisTextDim,
        )
        Button(
          onClick = {
            if (!onSave(keyInput)) {
              showError = true
            }
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = JarvisCyan,
              contentColor = JarvisBgDeep,
            ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_api_key_button"),
        ) {
          Text(
            text = stringResource(R.string.setup_save_button),
            fontWeight = FontWeight.Bold,
          )
        }
        if (allowCancel) {
          OutlinedButton(
            onClick = onCancel,
            border = BorderStroke(1.dp, JarvisBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cancel_setup_button"),
          ) {
            Text(text = stringResource(R.string.setup_cancel_button), color = JarvisTextDim)
          }
        }
      }
    }
  }
}

@Composable
private fun JarvisHudContent(
  uiState: JarvisUiState,
  hasMicPermission: Boolean,
  onRequestMicAndTap: () -> Unit,
  onToggleWake: (Boolean) -> Unit,
  onCycleLanguage: () -> Unit,
  onChangeKey: () -> Unit,
  onClearTranscript: () -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .widthIn(max = 560.dp)
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .navigationBarsPadding(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Top HUD Control Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(JarvisCyan))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "J.A.R.V.I.S.",
          style = MaterialTheme.typography.titleMedium,
          color = JarvisCyan,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        HudChipButton(
          label = uiState.languageMode.label,
          active = false,
          icon = Icons.Outlined.Language,
          contentDesc = stringResource(R.string.lang_cycle_desc),
          testTag = "language_toggle_button",
          onClick = onCycleLanguage,
        )
        HudChipButton(
          label = if (uiState.alwaysListeningWake) "WAKE ON" else "WAKE",
          active = uiState.alwaysListeningWake,
          icon = Icons.Outlined.RecordVoiceOver,
          contentDesc = stringResource(R.string.wake_toggle_label),
          testTag = "wake_mode_button",
          onClick = { onToggleWake(!uiState.alwaysListeningWake) },
        )
        IconButton(
          onClick = onChangeKey,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("change_key_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = stringResource(R.string.change_key_desc),
            tint = JarvisTextDim,
          )
        }
      }
    }

    if (!uiState.speechAvailable) {
      Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = JarvisDanger.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, JarvisDanger),
      ) {
        Text(
          text = "Speech Recognition is unavailable on this device. Please ensure Google Speech Services is enabled.",
          style = MaterialTheme.typography.bodySmall,
          color = JarvisText,
          modifier = Modifier.padding(12.dp),
        )
      }
    } else if (!hasMicPermission) {
      Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = JarvisSurfaceElevated,
        border = BorderStroke(1.dp, JarvisBorder),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.permission_needed_title),
            style = MaterialTheme.typography.bodySmall,
            color = JarvisText,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onRequestMicAndTap,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = JarvisCyan,
                contentColor = JarvisBgDeep,
              ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(48.dp).testTag("grant_mic_button"),
          ) {
            Text(
              text = stringResource(R.string.permission_grant_button),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Arc Reactor Mic Circle
    ArcReactorButton(
      jarvisState = uiState.jarvisState,
      rmsLevel = uiState.rmsLevel,
      onClick = onRequestMicAndTap,
    )

    Spacer(modifier = Modifier.height(10.dp))

    val statusText =
      when (uiState.jarvisState) {
        JarvisState.IDLE -> stringResource(R.string.status_idle)
        JarvisState.WAKE_LISTENING -> stringResource(R.string.status_wake_waiting)
        JarvisState.LISTENING -> stringResource(R.string.status_listening)
        JarvisState.THINKING -> stringResource(R.string.status_thinking)
        JarvisState.SPEAKING -> stringResource(R.string.status_speaking)
      }

    val statusColor by
      animateColorAsState(
        targetValue =
          when (uiState.jarvisState) {
            JarvisState.THINKING -> JarvisAmber
            JarvisState.SPEAKING -> JarvisEmerald
            else -> JarvisCyan
          },
        label = "statusColor",
      )

    Text(
      text = statusText.uppercase(),
      style = MaterialTheme.typography.titleMedium,
      color = statusColor,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag("status_line"),
    )
    Text(
      text = uiState.statusDetail,
      style = MaterialTheme.typography.bodySmall,
      color = JarvisTextDim,
      textAlign = TextAlign.Center,
    )

    if (uiState.timerSecondsRemaining > 0) {
      Spacer(modifier = Modifier.height(8.dp))
      val mins = uiState.timerSecondsRemaining / 60
      val secs = uiState.timerSecondsRemaining % 60
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = JarvisAmber.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, JarvisAmber),
      ) {
        Text(
          text = "TIMER %02d:%02d".format(mins, secs),
          style = MaterialTheme.typography.labelMedium,
          color = JarvisAmber,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).testTag("timer_badge"),
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Transcript Card
    Card(
      modifier = Modifier.fillMaxWidth().weight(1f),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = JarvisSurface.copy(alpha = 0.85f)),
      border = BorderStroke(1.dp, JarvisBorder),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "TRANSCRIPT",
            style = MaterialTheme.typography.labelMedium,
            color = JarvisTextDim,
          )
          IconButton(
            onClick = onClearTranscript,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_transcript_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteSweep,
              contentDescription = stringResource(R.string.clear_chat_desc),
              tint = JarvisTextDim,
            )
          }
        }

        if (uiState.transcript.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.empty_transcript_hint),
              style = MaterialTheme.typography.bodySmall,
              color = JarvisTextDim,
              textAlign = TextAlign.Center,
            )
          }
        } else {
          val listState = rememberLazyListState()
          LaunchedEffect(uiState.transcript.size) {
            listState.animateScrollToItem(uiState.transcript.lastIndex)
          }
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().testTag("transcript_list"),
          ) {
            items(uiState.transcript, key = { it.id }) { entry -> TranscriptBubble(entry) }
          }
        }
      }
    }
  }
}

@Composable
private fun HudChipButton(
  label: String,
  active: Boolean,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDesc: String,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    color = if (active) JarvisCyan.copy(alpha = 0.18f) else JarvisSurface,
    border = BorderStroke(1.dp, if (active) JarvisCyan else JarvisBorder),
    modifier = Modifier.height(48.dp).testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = contentDesc,
        tint = if (active) JarvisCyan else JarvisText,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) JarvisCyan else JarvisText,
      )
    }
  }
}

@Composable
private fun ArcReactorButton(
  jarvisState: JarvisState,
  rmsLevel: Float,
  onClick: () -> Unit,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "reactor")
  val outerRotation by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = if (jarvisState == JarvisState.LISTENING) 6000 else 16000,
              easing = LinearEasing,
            ),
        ),
      label = "outerRotation",
    )
  val innerRotation by
    infiniteTransition.animateFloat(
      initialValue = 360f,
      targetValue = 0f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = if (jarvisState == JarvisState.THINKING) 1800 else 8000,
              easing = LinearEasing,
            ),
        ),
      label = "innerRotation",
    )
  val pulse by
    infiniteTransition.animateFloat(
      initialValue = 0.94f,
      targetValue = 1.08f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = if (jarvisState == JarvisState.SPEAKING) 550 else 1200,
            ),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse",
    )

  val smoothRms by
    animateFloatAsState(
      targetValue = rmsLevel,
      animationSpec = spring(stiffness = 300f),
      label = "rms",
    )

  val ringColor by
    animateColorAsState(
      targetValue =
        when (jarvisState) {
          JarvisState.THINKING -> JarvisAmber
          JarvisState.SPEAKING -> JarvisEmerald
          else -> JarvisCyan
        },
      label = "ringColor",
    )

  val interactionSource = remember { MutableInteractionSource() }

  Box(
    modifier =
      Modifier
        .size(196.dp)
        .clip(CircleShape)
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = false, color = ringColor),
          role = Role.Button,
          onClickLabel = stringResource(R.string.reactor_mic_desc),
          onClick = onClick,
        )
        .testTag("reactor_mic_button"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val baseRadius = size.minDimension / 2f - 10.dp.toPx()

      val activeScale =
        when (jarvisState) {
          JarvisState.LISTENING -> 1f + (smoothRms * 0.14f) + ((pulse - 1f) * 0.4f)
          JarvisState.SPEAKING -> pulse
          JarvisState.WAKE_LISTENING -> 0.98f + ((pulse - 1f) * 0.35f)
          else -> 1f
        }

      // Ambient glow
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(ringColor.copy(alpha = 0.34f), Color.Transparent),
            center = center,
            radius = baseRadius * 1.15f * activeScale,
          ),
        radius = baseRadius * 1.15f * activeScale,
        center = center,
      )

      // Segmented outer ring
      rotate(degrees = outerRotation, pivot = center) {
        val segRadius = baseRadius * 0.94f
        val topLeft = Offset(center.x - segRadius, center.y - segRadius)
        val arcSize = Size(segRadius * 2f, segRadius * 2f)
        for (i in 0 until 6) {
          drawArc(
            color = ringColor.copy(alpha = 0.78f),
            startAngle = i * 60f + 8f,
            sweepAngle = 40f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
          )
        }
      }

      // Counter-rotating inner arcs
      rotate(degrees = innerRotation, pivot = center) {
        val midRadius = baseRadius * 0.74f
        val topLeft = Offset(center.x - midRadius, center.y - midRadius)
        val arcSize = Size(midRadius * 2f, midRadius * 2f)
        drawArc(
          color = ringColor,
          startAngle = 0f,
          sweepAngle = 135f,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
          color = ringColor,
          startAngle = 180f,
          sweepAngle = 135f,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      // Core circle
      val coreRadius = baseRadius * 0.52f * activeScale
      drawCircle(
        color = ringColor.copy(alpha = 0.18f),
        radius = coreRadius,
        center = center,
      )
      drawCircle(
        color = ringColor,
        radius = coreRadius,
        center = center,
        style = Stroke(width = 2.5.dp.toPx()),
      )
    }

    Icon(
      imageVector =
        if (jarvisState == JarvisState.SPEAKING) Icons.Filled.GraphicEq else Icons.Filled.Mic,
      contentDescription = stringResource(R.string.reactor_mic_desc),
      tint = ringColor,
      modifier = Modifier.size(42.dp),
    )
  }
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
  val isUser = entry.role == SpeakerRole.USER
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = if (isUser) JarvisCyan.copy(alpha = 0.14f) else JarvisSurfaceElevated,
      border =
        BorderStroke(
          1.dp,
          if (isUser) JarvisCyan.copy(alpha = 0.35f) else JarvisBorder,
        ),
      modifier = Modifier.widthIn(max = 300.dp),
    ) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
          text = if (isUser) "YOU" else "JARVIS",
          style = MaterialTheme.typography.labelMedium,
          color = if (isUser) JarvisCyan else JarvisEmerald,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = entry.text,
          style = MaterialTheme.typography.bodyLarge,
          color = JarvisText,
        )
      }
    }
  }
}
