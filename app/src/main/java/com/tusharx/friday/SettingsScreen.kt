@file:OptIn(ExperimentalMaterial3Api::class)

package com.tusharx.friday

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: FridayViewModel, onBack: () -> Unit) {
    val p = vm.prefs
    val ctx = LocalContext.current

    var apiKey by remember { mutableStateOf(p.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(p.model) }
    var boss by remember { mutableStateOf(p.bossName) }
    var owner by remember { mutableStateOf(p.ownerName) }
    var pass by remember { mutableStateOf(p.passphrase) }
    var guest by remember { mutableStateOf(p.guestControl) }
    var personality by remember { mutableStateOf(p.personality) }
    var language by remember { mutableStateOf(p.language) }
    var extra by remember { mutableStateOf(p.extra) }
    var rate by remember { mutableStateOf(p.rate) }
    var pitch by remember { mutableStateOf(p.pitch) }
    var silence by remember { mutableStateOf(p.silenceSec.toFloat()) }
    var proactive by remember { mutableStateOf(p.proactive) }
    var autoSpeak by remember { mutableStateOf(p.autoSpeak) }
    var voiceName by remember { mutableStateOf(p.voiceName) }
    var useEleven by remember { mutableStateOf(p.useEleven) }
    var elevenKey by remember { mutableStateOf(p.elevenKey) }
    var elevenVoice by remember { mutableStateOf(p.elevenVoiceId) }
    var voiceTick by remember { mutableStateOf(0) }
    var a11y by remember { mutableStateOf(FridayAccessibilityService.instance != null) }

    LaunchedEffect(Unit) {
        while (true) { a11y = FridayAccessibilityService.instance != null; delay(1000) }
    }
    val voices = remember(voiceTick) { vm.voice.availableVoices() }

    Box(Modifier.fillMaxSize()) {
        SpaceBackground(Status.IDLE)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextMain) }
                Text("Settings", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Section("🔑  Gemini API") {
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it; p.apiKey = it },
                        label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation()
                    )
                    SwitchRow("Show key", showKey) { showKey = it }
                    OutlinedTextField(
                        value = model, onValueChange = { model = it; p.model = it },
                        label = { Text("Model (e.g. gemini-3.8-flash)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { vm.testApi() }) { Text("Test connection") }
                    if (vm.apiTest.isNotBlank()) Text(vm.apiTest, color = Muted, fontSize = 13.sp)
                }

                Section("👑  Owner & identity") {
                    OutlinedTextField(
                        value = boss, onValueChange = { boss = it; p.bossName = it },
                        label = { Text("What Friday calls the owner") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = owner, onValueChange = { owner = it; p.ownerName = it },
                        label = { Text("Creator name (when asked who made it)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it; p.passphrase = it },
                        label = { Text("Secret unlock phrase (blank = everyone is owner)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Owner banne ke liye ye phrase bolo. 'Friday lock' bolne par guest mode. Android voice ko pehchaan nahi sakta, isliye phrase use hota hai.",
                        color = Muted, fontSize = 12.sp
                    )
                    SwitchRow("Guests can control the phone", guest) { guest = it; p.guestControl = it }
                }

                Section("🎭  Personality") {
                    Choice("Style", listOf("Friendly", "Funny", "Professional", "Desi Swag"), personality) { personality = it; p.personality = it }
                    Choice("Language", listOf("Hinglish", "Hindi", "English"), language) { language = it; p.language = it; vm.applyVoiceSettings() }
                    OutlinedTextField(
                        value = extra, onValueChange = { extra = it; p.extra = it },
                        label = { Text("Extra instructions for Friday") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                    )
                }

                Section("🔊  Voice") {
                    SwitchRow("Speak replies aloud", autoSpeak) { autoSpeak = it; p.autoSpeak = it }
                    SliderRow("Speed", rate, 0.5f..1.6f, "%.2f".format(rate)) { rate = it; p.rate = it }
                    SliderRow("Pitch (low = deep)", pitch, 0.5f..1.5f, "%.2f".format(pitch)) { pitch = it; p.pitch = it }
                    Text("Phone ki voices (tap = select + preview):", color = TextMain, fontSize = 14.sp)
                    VoiceRow("Auto (language ke hisaab se)", voiceName.isBlank()) {
                        voiceName = ""; p.voiceName = ""; vm.applyVoiceSettings(); vm.testVoice()
                    }
                    voices.take(14).forEach { v ->
                        VoiceRow("${v.locale.displayName}  •  ${v.name.take(30)}", v.name == voiceName) {
                            voiceName = v.name; p.voiceName = v.name; vm.applyVoiceSettings(); vm.testVoice()
                        }
                    }
                    if (voices.isEmpty()) Text("Voices abhi load nahi hui. 'Refresh list' dabao.", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { voiceTick++ }) { Text("Refresh list") }
                        Button(onClick = { vm.testVoice() }) { Text("Test voice") }
                    }
                    Text(
                        "Aur voices chahiye? Phone Settings > Text-to-speech > Google Text-to-speech > Install voice data se naye Hindi/English voices download karo, phir 'Refresh list' dabao.",
                        color = Muted, fontSize = 12.sp
                    )
                    OutlinedButton(onClick = {
                        try { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (e: Exception) { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Open phone TTS settings") }
                }

                Section("✨  ElevenLabs voice (optional, realistic)") {
                    Text(
                        "elevenlabs.io par account banao, API key aur kisi voice ka Voice ID copy karke yahan daalo. Internet chahiye. Fail hua to phone ki voice chalegi.",
                        color = Muted, fontSize = 12.sp
                    )
                    SwitchRow("Use ElevenLabs voice", useEleven) { useEleven = it; p.useEleven = it }
                    OutlinedTextField(
                        value = elevenKey, onValueChange = { elevenKey = it; p.elevenKey = it },
                        label = { Text("ElevenLabs API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = elevenVoice, onValueChange = { elevenVoice = it; p.elevenVoiceId = it },
                        label = { Text("Voice ID") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { vm.testVoice() }) { Text("Test voice") }
                    if (vm.voiceNote.isNotBlank()) Text(vm.voiceNote, color = Amber, fontSize = 12.sp)
                }

                Section("⏱️  Proactive mode") {
                    SwitchRow("Speak up when I'm silent", proactive) { proactive = it; p.proactive = it }
                    SliderRow("Silence before speaking", silence, 15f..300f, "${silence.toInt()} sec") { silence = it; p.silenceSec = it.toInt() }
                }

                Section("📱  Phone control") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (a11y) Color(0xFF3DDC84) else Color(0xFFFF5C5C)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (a11y) "Accessibility: ON (Home/Back/Recents/Screenshot ready)" else "Accessibility: OFF",
                            color = TextMain, fontSize = 13.sp
                        )
                    }
                    Text(
                        "Apps kholna, call, torch, volume, alarm sab bina accessibility ke chalta hai. Sirf Home/Back/Recents/Screenshot ke liye ye chahiye.",
                        color = Muted, fontSize = 12.sp
                    )
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                        Text("Open Accessibility settings")
                    }
                    Text(
                        "Toggle grey ho ya \"Restricted setting\" likha aaye (sideload APK par Android 13+ mein hota hai):\n" +
                            "1. Neeche 'Open App info' dabao.\n" +
                            "2. Upar-right ⋮ (3 dots) > 'Allow restricted settings' > confirm.\n" +
                            "3. Wapas Accessibility > Installed/Downloaded apps > Friday > ON.\n" +
                            "3 dots na dikhein to pehle Accessibility mein Friday par ek baar tap karo (popup aane do), phir App info kholo.",
                        color = Muted, fontSize = 12.sp
                    )
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Open App info") }
                    OutlinedButton(onClick = { vm.clearChat() }) { Text("Clear chat history") }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Friday v2.0  •  made by ${owner.ifBlank { "GM Tusharx" }}", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), 0.06f, 0.14f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMain, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row { Text(label, color = TextMain, modifier = Modifier.weight(1f)); Text(shown, color = Muted) }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) Purple.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) Cyan else Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = Cyan, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Choice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TextMain)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                val on = o == selected
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) Purple else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (on) Cyan else Muted.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { onSelect(o) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(o, color = TextMain, fontSize = 12.sp) }
            }
        }
    }
}
