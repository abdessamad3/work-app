package com.prayerwakeup.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prayerwakeup.app.data.remote.MawaqitTimes
import com.prayerwakeup.app.domain.CalculationMethod
import com.prayerwakeup.app.domain.CallerPersona
import com.prayerwakeup.app.domain.Madhab
import com.prayerwakeup.app.domain.Prayer
import com.prayerwakeup.app.domain.PrayerTimeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("الموقع")
            Text(
                if (state.settings.hasLocation) {
                    "${state.settings.locationLabel} (${"%.4f".format(state.settings.latitude)}, ${"%.4f".format(state.settings.longitude)})"
                } else {
                    "لم يُحدد بعد"
                }
            )
            Spacer(Modifier.height(8.dp))
            var locating by remember { mutableStateOf(false) }
            var locationError by remember { mutableStateOf<String?>(null) }
            Button(onClick = {
                locating = true
                locationError = null
                viewModel.detectLocation { success ->
                    locating = false
                    if (!success) locationError = "تعذر تحديد الموقع، تأكد من تفعيل GPS أو أدخله يدوياً بالأسفل"
                }
            }) {
                Text(if (locating) "جارٍ تحديد الموقع..." else "استخدام موقعي الحالي (GPS)")
            }
            locationError?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
            ManualLocationForm(
                initialLat = state.settings.latitude,
                initialLon = state.settings.longitude,
                initialTz = state.settings.timeZoneId,
                onSave = { lat, lon, tz, label -> viewModel.setManualLocation(lat, lon, tz, label) }
            )

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("مصدر مواقيت الصلاة")
            PrayerTimeSource.entries.forEach { source ->
                RadioRow(
                    label = source.displayName,
                    selected = state.settings.prayerTimeSource == source,
                    onClick = { viewModel.setPrayerTimeSource(source) }
                )
            }
            if (state.settings.prayerTimeSource == PrayerTimeSource.MOROCCO_HABOUS) {
                MoroccoCitySection(viewModel = viewModel, selectedCityLabel = state.settings.moroccoCityLabel)
            }
            if (state.settings.prayerTimeSource == PrayerTimeSource.MAWAQIT_MOSQUE) {
                MawaqitMosqueSection(
                    viewModel = viewModel,
                    selectedMosqueId = state.settings.mawaqitMosqueId,
                    selectedMosqueLabel = state.settings.mawaqitMosqueLabel
                )
            }

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("طريقة حساب المواقيت (احتياطية دائماً، ومستخدمة كاملة إن اخترت \"حساب فلكي\")")
            CalculationMethod.entries.forEach { method ->
                RadioRow(
                    label = method.displayName,
                    selected = state.settings.calculationMethod == method,
                    onClick = { viewModel.setCalculationMethod(method) }
                )
            }

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("المذهب (لحساب وقت العصر)")
            Madhab.entries.forEach { madhab ->
                RadioRow(
                    label = madhab.displayName,
                    selected = state.settings.madhab == madhab,
                    onClick = { viewModel.setMadhab(madhab) }
                )
            }

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("الصلوات المفعّلة للاتصال")
            Prayer.entries.forEach { prayer ->
                Row {
                    Checkbox(
                        checked = state.settings.enabledPrayers.contains(prayer),
                        onCheckedChange = { viewModel.togglePrayer(prayer, state.settings.enabledPrayers) }
                    )
                    Text(prayer.arabicName, modifier = Modifier.padding(top = 12.dp))
                }
            }

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("أسلوب المتصل")
            CallerPersona.entries.forEach { persona ->
                Column {
                    RadioRow(
                        label = persona.displayName,
                        selected = state.settings.persona == persona,
                        onClick = { viewModel.setPersona(persona) }
                    )
                    Text(
                        persona.styleDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 40.dp, bottom = 4.dp)
                    )
                }
            }

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("مدة المكالمة القصوى (دقائق)")
            Stepper(
                value = state.settings.maxCallMinutes,
                range = 1..10,
                onChange = { viewModel.setMaxCallMinutes(it) }
            )

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("محادثة ذكية حقيقية (اختياري)")
            Text("أضف مفتاح Gemini API الخاص بك (مجاني من aistudio.google.com) ليتحدث معك المتصل بشكل حقيقي ويرد على ما تقوله. بدون المفتاح، سيستخدم التطبيق جملاً ثابتة فقط.")
            Spacer(Modifier.height(8.dp))
            var geminiKeyField by remember(state.geminiApiKey) { mutableStateOf(state.geminiApiKey) }
            OutlinedTextField(
                value = geminiKeyField,
                onValueChange = { geminiKeyField = it },
                label = { Text("Gemini API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.saveGeminiApiKey(geminiKeyField) }) { Text("حفظ المفتاح") }

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("صوت طبيعي مخصص (ElevenLabs، اختياري)")
            Text("أضف مفتاح ElevenLabs API (مجاني من elevenlabs.io) واختر صوتاً من مكتبتك ليتحدث به المتصل بدلاً من صوت النظام الافتراضي. الخطة المجانية تتيح أصواتاً جاهزة عالية الجودة، وليس استنساخ صوتك الخاص (يتطلب خطة مدفوعة).")
            Spacer(Modifier.height(8.dp))
            var elevenLabsKeyField by remember(state.elevenLabsApiKey) { mutableStateOf(state.elevenLabsApiKey) }
            OutlinedTextField(
                value = elevenLabsKeyField,
                onValueChange = { elevenLabsKeyField = it },
                label = { Text("ElevenLabs API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.saveElevenLabsApiKey(elevenLabsKeyField) }) { Text("حفظ المفتاح") }
            Spacer(Modifier.height(12.dp))
            ElevenLabsVoiceSection(
                viewModel = viewModel,
                selectedVoiceLabel = state.settings.elevenLabsVoiceLabel
            )

            Divider(Modifier.padding(vertical = 20.dp))

            SectionTitle("الأذونات")
            if (!state.canScheduleExactAlarms) {
                PermissionRow(
                    text = "إذن التنبيهات الدقيقة مطلوب حتى تتصل بك المكالمة في الوقت المحدد بالضبط.",
                    buttonLabel = "فتح الإعدادات"
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    }
                }
            }
            PermissionRow(
                text = "استثناء التطبيق من تحسين البطارية يمنع النظام من إيقافه قبل موعد الصلاة.",
                buttonLabel = "فتح الإعدادات"
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    )
                }.onFailure {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            PermissionRow(
                text = "تأكد من تفعيل الإشعارات ذات الأولوية العالية للتطبيق.",
                buttonLabel = "إعدادات الإشعارات"
            ) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startActivity(intent)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun PermissionRow(text: String, buttonLabel: String, onClick: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onClick) { Text(buttonLabel) }
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedButton(onClick = { if (value - 1 >= range.first) onChange(value - 1) }) { Text("-") }
        Text("$value", modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedButton(onClick = { if (value + 1 <= range.last) onChange(value + 1) }) { Text("+") }
    }
}

@Composable
private fun MoroccoCitySection(viewModel: SettingsViewModel, selectedCityLabel: String) {
    val citiesState by viewModel.moroccoCities.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (citiesState is MoroccoCitiesUiState.Idle) viewModel.loadMoroccoCities()
    }

    Column(Modifier.padding(top = 8.dp, start = 40.dp)) {
        Text(
            if (selectedCityLabel.isNotBlank()) "المدينة المختارة: $selectedCityLabel" else "لم تُختر مدينة بعد",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))

        when (val s = citiesState) {
            is MoroccoCitiesUiState.Idle -> {
                Button(onClick = { viewModel.loadMoroccoCities() }) { Text("تحميل قائمة المدن") }
            }
            is MoroccoCitiesUiState.Loading -> {
                Text(
                    "جارٍ تحميل قائمة المدن... قد يستغرق الأمر حتى دقيقة عند أول استخدام (الخدمة تحتاج للاستيقاظ).",
                    color = MaterialTheme.colorScheme.outline
                )
            }
            is MoroccoCitiesUiState.Error -> {
                Text(
                    "تعذر تحميل قائمة المدن: ${s.message}. سيُستخدم الحساب الفلكي بدلاً منها تلقائياً حتى تعاود المحاولة.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.loadMoroccoCities() }) { Text("إعادة المحاولة") }
            }
            is MoroccoCitiesUiState.Loaded -> {
                Column {
                    s.cities.forEach { city ->
                        Row {
                            RadioButton(
                                selected = selectedCityLabel == city.displayLabel,
                                onClick = { viewModel.setMoroccoCity(city) }
                            )
                            Text(city.displayLabel, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MawaqitMosqueSection(viewModel: SettingsViewModel, selectedMosqueId: String, selectedMosqueLabel: String) {
    val lookupState by viewModel.mawaqitLookup.collectAsState()
    var mosqueIdField by remember(selectedMosqueId) { mutableStateOf(selectedMosqueId) }

    Column(Modifier.padding(top = 8.dp, start = 40.dp)) {
        Text(
            if (selectedMosqueLabel.isNotBlank()) "المسجد المختار: $selectedMosqueLabel (رقم $selectedMosqueId)"
            else "لم يُختر مسجد بعد",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "أدخل رقم المسجد من موقع mawaqit.net (يظهر في رابط صفحة المسجد)، ثم تحقق منه لعرض اسمه ومواقيته قبل الاختيار.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = mosqueIdField,
            onValueChange = { mosqueIdField = it },
            label = { Text("رقم المسجد (مثال: 49015)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            viewModel.resetMawaqitLookup()
            viewModel.lookupMawaqitMosque(mosqueIdField)
        }) { Text("التحقق من المسجد") }

        Spacer(Modifier.height(8.dp))
        when (val s = lookupState) {
            is MawaqitLookupUiState.Idle -> Unit
            is MawaqitLookupUiState.Loading -> {
                Text("جارٍ البحث عن المسجد...", color = MaterialTheme.colorScheme.outline)
            }
            is MawaqitLookupUiState.Error -> {
                Text(
                    "تعذر العثور على المسجد: ${s.message}. سيُستخدم الحساب الفلكي بدلاً منه تلقائياً حتى تعاود المحاولة.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is MawaqitLookupUiState.Loaded -> {
                MawaqitPreview(times = s.times)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.confirmMawaqitMosque(mosqueIdField, s.times) }) {
                    Text("اعتماد هذا المسجد")
                }
            }
        }
    }
}

@Composable
private fun MawaqitPreview(times: MawaqitTimes) {
    Column {
        Text("تم العثور على: ${times.mosqueName}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "الفجر ${times.fajr} · الظهر ${times.dhuhr} · العصر ${times.asr} · المغرب ${times.maghrib} · العشاء ${times.isha}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ElevenLabsVoiceSection(viewModel: SettingsViewModel, selectedVoiceLabel: String) {
    val voicesState by viewModel.elevenLabsVoices.collectAsState()

    Column {
        Text(
            if (selectedVoiceLabel.isNotBlank()) "الصوت المختار: $selectedVoiceLabel (سيُستخدم دائماً كخيار أول، مع الرجوع لصوت النظام تلقائياً عند أي عطل)"
            else "لم يُختر صوت بعد — سيستخدم التطبيق صوت النظام الافتراضي",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        if (selectedVoiceLabel.isNotBlank()) {
            TextButton(onClick = { viewModel.clearElevenLabsVoice() }) { Text("الرجوع لصوت النظام الافتراضي") }
            Spacer(Modifier.height(4.dp))
        }

        when (val s = voicesState) {
            is ElevenLabsVoicesUiState.Idle -> {
                Button(onClick = { viewModel.loadElevenLabsVoices() }) { Text("تحميل قائمة الأصوات") }
            }
            is ElevenLabsVoicesUiState.Loading -> {
                Text("جارٍ تحميل قائمة الأصوات...", color = MaterialTheme.colorScheme.outline)
            }
            is ElevenLabsVoicesUiState.Error -> {
                Text(
                    "تعذر تحميل قائمة الأصوات: ${s.message}. سيُستخدم صوت النظام الافتراضي تلقائياً حتى تعاود المحاولة.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.loadElevenLabsVoices() }) { Text("إعادة المحاولة") }
            }
            is ElevenLabsVoicesUiState.Loaded -> {
                Column {
                    s.voices.forEach { voice ->
                        Row {
                            RadioButton(
                                selected = selectedVoiceLabel == voice.name,
                                onClick = { viewModel.setElevenLabsVoice(voice) }
                            )
                            Text(voice.name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualLocationForm(
    initialLat: Double,
    initialLon: Double,
    initialTz: String,
    onSave: (Double, Double, String, String) -> Unit
) {
    // Keyed on the initial values so the fields pick up a GPS/manual save made elsewhere
    // (e.g. tapping "use my current location") instead of freezing at whatever was on
    // screen the first time this form composed.
    var lat by remember(initialLat) { mutableStateOf(initialLat.toString()) }
    var lon by remember(initialLon) { mutableStateOf(initialLon.toString()) }
    var tz by remember(initialTz) { mutableStateOf(initialTz) }
    var label by remember { mutableStateOf("") }

    Text("أو أدخل الموقع يدوياً:", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = lat, onValueChange = { lat = it }, label = { Text("خط العرض (Latitude)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = lon, onValueChange = { lon = it }, label = { Text("خط الطول (Longitude)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = tz, onValueChange = { tz = it }, label = { Text("المنطقة الزمنية (مثال: Asia/Riyadh)") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = label, onValueChange = { label = it }, label = { Text("اسم المدينة (اختياري)") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = {
        val latValue = lat.toDoubleOrNull()
        val lonValue = lon.toDoubleOrNull()
        if (latValue != null && lonValue != null && tz.isNotBlank()) {
            onSave(latValue, lonValue, tz.trim(), label.ifBlank { "موقع يدوي" })
        }
    }) {
        Text("حفظ الموقع اليدوي")
    }
}
