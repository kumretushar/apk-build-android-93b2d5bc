@file:OptIn(ExperimentalMaterial3Api::class)

package com.tusharx.friday

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

val Bg = Color(0xFF04060F)
val Panel = Color(0xFF0E1530)
val Cyan = Color(0xFF00E5FF)
val Purple = Color(0xFF9B5CFF)
val Blue = Color(0xFF3D6BFF)
val Amber = Color(0xFFFFC857)
val TextMain = Color(0xFFE9EEFF)
val Muted = Color(0xFF8E9AC4)

/** Frosted-glass look used for cards, bubbles and the input bar. */
fun Modifier.glass(shape: Shape, fill: Float = 0.07f, line: Float = 0.14f): Modifier =
    this.clip(shape).background(Color.White.copy(alpha = fill)).border(1.dp, Color.White.copy(alpha = line), shape)

@Composable
fun FridayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, secondary = Purple, background = Bg, surface = Panel,
            onSurface = TextMain, onBackground = TextMain, onPrimary = Color.Black
        ),
        content = content
    )
}

@Composable
fun SpaceBackground(status: Status) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val accent = when (status) {
        Status.LISTENING -> Cyan; Status.SPEAKING -> Purple; Status.THINKING -> Amber; Status.IDLE -> Blue
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF03050D), Color(0xFF080D26), Color(0xFF04060F))))
        val c1 = Offset(size.width * (0.15f + 0.7f * a), size.height * 0.2f)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent), center = c1, radius = size.width), radius = size.width, center = c1)
        val c2 = Offset(size.width * (0.95f - 0.7f * a), size.height * 0.88f)
        drawCircle(Brush.radialGradient(listOf(Purple.copy(alpha = 0.16f), Color.Transparent), center = c2, radius = size.width * 0.9f), radius = size.width * 0.9f, center = c2)
    }
}

// ============================================================ main screen

@Composable
fun MainScreen(vm: FridayViewModel, onSettings: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val empty = vm.messages.isEmpty()
    val orbSize by animateDpAsState(if (empty) 240.dp else 150.dp, tween(500), label = "orbSize")

    LaunchedEffect(vm.messages.size, vm.status) {
        val n = vm.messages.size + (if (vm.status == Status.THINKING) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }
    val chips = listOf(
        "▶️  YouTube kholo" to "YouTube kholo",
        "🔦  Torch on karo" to "Torch on karo",
        "😄  Ek joke sunao" to "Ek joke sunao",
        "💻  Python code likho" to "Python mein ek simple calculator ka code likho",
        "✨  Kuch interesting batao" to "Kuch interesting batao"
    )

    Box(Modifier.fillMaxSize()) {
        SpaceBackground(vm.status)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Header(vm, onSettings)

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Orb(vm.status, vm.level, vm.live, orbSize) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.toggleLive()
                }
            }
            StatusLine(vm)

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (empty) item { Greeting(vm) }
                items(vm.messages) { Bubble(it) }
                if (vm.status == Status.THINKING) item { TypingBubble() }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(chips) { (label, cmd) ->
                    Box(
                        Modifier.glass(RoundedCornerShape(50), 0.08f, 0.2f)
                            .clickable { vm.send(cmd) }.padding(horizontal = 14.dp, vertical = 9.dp)
                    ) { Text(label, color = TextMain, fontSize = 13.sp) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f).glass(RoundedCornerShape(28.dp), 0.09f, 0.2f), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type karo ya bolo...", color = Muted) },
                        maxLines = 3,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent, cursorColor = Cyan
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" })
                    )
                }
                Spacer(Modifier.width(10.dp))
                val hasText = input.isNotBlank()
                val active = hasText || vm.live
                Box(
                    Modifier.size(54.dp).clip(CircleShape)
                        .background(Brush.linearGradient(if (active) listOf(Cyan, Purple) else listOf(Color(0xFF1B2347), Color(0xFF232C5A))))
                        .clickable {
                            if (hasText) { vm.send(input); input = "" }
                            else { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleLive() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (hasText) Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                    else Text(if (vm.live) "⏹" else "🎙️", fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
fun Header(vm: FridayViewModel, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "FRIDAY",
                style = TextStyle(brush = Brush.linearGradient(listOf(Cyan, Purple)), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 7.sp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (vm.isBoss) Color(0xFF3DDC84) else Amber))
                Spacer(Modifier.width(6.dp))
                Text(if (vm.isBoss) "Boss mode" else "Guest mode", color = Muted, fontSize = 12.sp)
            }
        }
        Box(Modifier.size(44.dp).glass(CircleShape, 0.09f, 0.2f).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextMain)
        }
    }
}

@Composable
fun StatusLine(vm: FridayViewModel) {
    val txt = when (vm.status) {
        Status.LISTENING -> if (vm.partial.isBlank()) "Listening..." else vm.partial
        Status.THINKING -> "Thinking..."
        Status.SPEAKING -> "Speaking..."
        Status.IDLE -> if (vm.live) "Ready" else "Tap the orb to talk"
    }
    Text(
        txt,
        color = if (vm.status == Status.LISTENING && vm.partial.isNotBlank()) TextMain else Muted,
        fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp)
    )
}

@Composable
fun Greeting(vm: FridayViewModel) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val g = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val who = if (vm.isBoss) vm.prefs.bossName else "friend"
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$g, $who 👋", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Orb dabao aur bolo, ya neeche type karo. Pehli baar ho to Settings mein Gemini API key daal do.", color = Muted, fontSize = 13.sp)
        Text("Try: \"WhatsApp kholo\"  •  \"Arijit Singh ke songs YouTube pe chalao\"  •  \"Mujhe ek Python code likh ke do\"", color = Cyan.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}

// ============================================================ orb

@Composable
fun Orb(status: Status, level: Float, live: Boolean, diameter: Dp, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (status == Status.THINKING) 2200 else 14000, easing = LinearEasing)), label = "spin")
    val spin2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "spin2")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (status == Status.SPEAKING) 380 else 2400), RepeatMode.Reverse), label = "breathe")
    val wave by t.animateFloat(0f, 6.2831855f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wave")
    val target = when (status) {
        Status.LISTENING -> 0.15f + level * 0.85f
        Status.SPEAKING -> 0.4f + 0.4f * breathe
        Status.THINKING -> 0.3f
        Status.IDLE -> 0.1f + 0.05f * breathe
    }
    val lvl by animateFloatAsState(target, tween(150), label = "lvl")
    val core = when (status) {
        Status.LISTENING -> Cyan; Status.SPEAKING -> Color(0xFFB07CFF); Status.THINKING -> Amber; Status.IDLE -> Blue
    }

    Canvas(Modifier.size(diameter).pointerInput(Unit) { detectTapGestures { onClick() } }) {
        val c = center
        val R = size.minDimension / 2f

        // outer glow
        drawCircle(
            Brush.radialGradient(listOf(core.copy(alpha = 0.30f + 0.25f * lvl), Purple.copy(alpha = 0.10f), Color.Transparent), center = c, radius = R),
            radius = R, center = c
        )
        // radial equaliser bars
        val bars = 72
        val r0 = R * 0.56f
        for (i in 0 until bars) {
            val a = i * 6.2831855f / bars
            val k = 0.5f + 0.5f * sin(wave * (1 + i % 3) + i * 0.9f)
            val len = R * (0.03f + 0.26f * lvl * k)
            val s = Offset(c.x + cos(a) * r0, c.y + sin(a) * r0)
            val e = Offset(c.x + cos(a) * (r0 + len), c.y + sin(a) * (r0 + len))
            drawLine(lerp(Cyan, Purple, i / bars.toFloat()), s, e, strokeWidth = R * 0.016f + 1.5f, cap = StrokeCap.Round)
        }
        // glass sphere with offset highlight (3D look)
        val r = R * 0.44f * (1f + 0.05f * breathe + 0.12f * lvl)
        val hl = Offset(c.x - r * 0.35f, c.y - r * 0.4f)
        drawCircle(
            Brush.radialGradient(listOf(Color.White, core, Color(0xFF3B1FA8), Color(0xFF0A0F2E)), center = hl, radius = r * 1.7f),
            radius = r, center = c
        )
        drawCircle(Color.White.copy(alpha = 0.28f), radius = r, center = c, style = Stroke(width = 2f))
        // rotating rings
        rotate(spin, c) {
            drawArc(
                color = Cyan.copy(alpha = 0.9f), startAngle = 0f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(c.x - R * 0.93f, c.y - R * 0.93f), size = Size(R * 1.86f, R * 1.86f),
                style = Stroke(width = if (live) 6f else 4f, cap = StrokeCap.Round)
            )
        }
        rotate(spin2, c) {
            drawArc(
                color = Purple.copy(alpha = 0.9f), startAngle = 180f, sweepAngle = 50f, useCenter = false,
                topLeft = Offset(c.x - R * 0.97f, c.y - R * 0.97f), size = Size(R * 1.94f, R * 1.94f),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
        // orbiting particles
        for (i in 0 until 8) {
            val a = spin * 0.0174533f * (if (i % 2 == 0) 1f else -1f) + i * 0.785f
            val pr = R * (0.84f + 0.03f * sin(wave + i))
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 2.5f, center = Offset(c.x + cos(a) * pr, c.y + sin(a) * pr))
        }
    }
}

// ============================================================ chat

@Composable
fun Avatar() {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Purple))),
        contentAlignment = Alignment.Center
    ) { Text("F", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "phase")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(); Spacer(Modifier.width(8.dp))
        Row(Modifier.glass(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 3) {
                val on = phase.toInt() % 3 == i
                Box(Modifier.size(8.dp).clip(CircleShape).background(Cyan.copy(alpha = if (on) 1f else 0.3f)))
            }
        }
    }
}

@Composable
fun Bubble(m: Msg) {
    val ctx = LocalContext.current
    if (m.fromUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7B4DFF), Blue)))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) { Text(m.text, color = Color.White, fontSize = 15.sp) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Avatar()
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.text.isNotBlank()) {
                    Box(
                        Modifier.widthIn(max = 310.dp)
                            .glass(RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp), 0.08f, 0.16f)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text(m.text, color = TextMain, fontSize = 15.sp) }
                }
                if (m.code.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF070B14))
                            .border(1.dp, Purple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.codeLang.ifBlank { "code" }, color = Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("code", m.code))
                                Toast.makeText(ctx, "Code copied", Toast.LENGTH_SHORT).show()
                            }) { Text("Copy") }
                            TextButton(onClick = {
                                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, m.code)
                                ctx.startActivity(Intent.createChooser(i, "Share code").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }) { Text("Share") }
                        }
                        Text(
                            m.code, color = Color(0xFFB8F4FF), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
                        )
                    }
                }
                if (m.note.isNotBlank()) {
                    Text("⚡ " + m.note, color = Cyan.copy(alpha = 0.8f), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
