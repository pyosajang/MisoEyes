package com.misoeyes

/**
 * 2026.07.25 GitHub 연결 시험
 */
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URL
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { MisoEyesApp() } }
    }
}

private enum class Screen { HOME, PERMISSIONS, SAVED }

data class AnalysisUiState(
    val bitmap: Bitmap? = null,
    val source: String = "",
    val labels: List<LabelResult> = emptyList(),
    val isAnalyzing: Boolean = false,
    val error: String? = null
)

data class LabelResult(val text: String, val confidence: Float)
data class SavedResult(val createdAt: String, val source: String, val labels: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()
    private val prefs = application.getSharedPreferences("miso_history", Context.MODE_PRIVATE)

    fun analyze(bitmap: Bitmap, source: String) {
        _state.value = AnalysisUiState(bitmap = bitmap, source = source, isAnalyzing = true)
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                _state.value = _state.value.copy(
                    isAnalyzing = false,
                    labels = labels.sortedByDescending { it.confidence }.take(5)
                        .map { LabelResult(it.text, it.confidence) }
                )
            }
            .addOnFailureListener { error ->
                _state.value = _state.value.copy(isAnalyzing = false, error = error.message ?: "분석에 실패했습니다.")
            }
    }

    fun loadFromUrl(url: String) {
        _state.value = AnalysisUiState(isAnalyzing = true, source = "네트워크 이미지")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream) ?: error("지원하지 않는 이미지 형식입니다.")
                }
                analyze(bitmap, "네트워크 이미지")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isAnalyzing = false, error = "이미지를 불러올 수 없습니다: ${e.message}")
            }
        }
    }

    fun saveCurrent() {
        val current = _state.value
        if (current.labels.isEmpty()) return
        val record = listOf(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date()),
            current.source,
            current.labels.joinToString(", ") { "${it.text} ${(it.confidence * 100).toInt()}%" }
        ).joinToString("|")
        val existing = prefs.getStringSet("records", emptySet())!!.toMutableSet()
        existing.add(record)
        prefs.edit().putStringSet("records", existing).apply()
    }

    fun saved(): List<SavedResult> = prefs.getStringSet("records", emptySet())!!.mapNotNull { raw ->
        val parts = raw.split("|", limit = 3)
        if (parts.size == 3) SavedResult(parts[0], parts[1], parts[2]) else null
    }.sortedByDescending { it.createdAt }
}

@Composable
private fun MisoEyesApp(vm: MainViewModel = viewModel()) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(vm, onPermissions = { screen = Screen.PERMISSIONS }, onSaved = { screen = Screen.SAVED })
                Screen.PERMISSIONS -> PermissionScreen(onBack = { screen = Screen.HOME })
                Screen.SAVED -> SavedScreen(vm, onBack = { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel, onPermissions: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    var showUrl by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(TakePicturePreview()) { bitmap -> bitmap?.let { vm.analyze(it, "카메라") } }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) camera.launch(null) }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
            else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            vm.analyze(bitmap, "기기 파일")
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Miso Eyes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("이미지를 보고, 이해하고, 기록합니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onPermissions) { Text("권한 확인") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp)) {
                    Text("이미지 가져오기", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.launch(null)
                            else cameraPermission.launch(Manifest.permission.CAMERA)
                        }, modifier = Modifier.weight(1f)) { Text("카메라") }
                        OutlinedButton(onClick = { file.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("파일") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showUrl = !showUrl }, modifier = Modifier.fillMaxWidth()) { Text("네트워크 URL에서 불러오기") }
                    if (showUrl) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https:// 이미지 주소") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { vm.loadFromUrl(url) }, enabled = url.startsWith("rtsp"), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("불러와서 분석") }
                    }
                }
            }
        }
        item {
            when {
                state.isAnalyzing -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(); Text("  AI가 이미지를 분석하고 있어요") }
                state.bitmap != null -> AnalysisCard(state, onSave = vm::saveCurrent)
                else -> EmptyCard()
            }
        }
        item { TextButton(onClick = onSaved, modifier = Modifier.fillMaxWidth()) { Text("저장된 분석 기록 보기") } }
    }
}

@Composable private fun EmptyCard() = Card(Modifier.fillMaxWidth()) { Text("카메라, 파일, 또는 URL을 이용해 이미지를 가져오세요.\nAI가 이미지 속 대상을 식별해 드립니다.", Modifier.padding(24.dp), textAlign = TextAlign.Center) }

@Composable private fun AnalysisCard(state: AnalysisUiState, onSave: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        state.bitmap?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "분석 이미지", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) }
        Spacer(Modifier.height(14.dp)); Text("AI 분석 결과", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("입력: ${state.source}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error)
        if (state.labels.isEmpty() && state.error == null) Text("식별된 항목이 없습니다.", Modifier.padding(top = 12.dp))
        state.labels.forEach { label -> Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label.text); Text("${(label.confidence * 100).toInt()}%", fontWeight = FontWeight.Bold) } }
        if (state.labels.isNotEmpty()) Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("분석 결과 저장") }
    }
}

@Composable private fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val granted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("권한 확인", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp)); Text("Miso Eyes는 필요한 기능에만 권한을 사용합니다.")
        Spacer(Modifier.height(24.dp))
        PermissionRow("카메라", if (granted) "허용됨" else "허용되지 않음", "사진을 촬영해 AI 분석에 사용합니다.")
        Divider(Modifier.padding(vertical = 16.dp))
        PermissionRow("사진 및 파일", "선택한 파일만 접근", "Android 사진 선택기를 사용하므로 전체 저장소 권한이 필요하지 않습니다.")
        Divider(Modifier.padding(vertical = 16.dp))
        PermissionRow("네트워크", "사용 가능", "입력한 이미지 URL을 불러오고 분석합니다.")
        Spacer(Modifier.weight(1f))
        if (!granted) Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text("카메라 권한 허용") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("돌아가기") }
    }
}
@Composable private fun PermissionRow(name: String, status: String, detail: String) { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, fontWeight = FontWeight.Bold); Text(status, color = MaterialTheme.colorScheme.primary) }; Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) } }

@Composable private fun SavedScreen(vm: MainViewModel, onBack: () -> Unit) {
    val records = remember { vm.saved() }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("저장된 분석", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (records.isEmpty()) Text("아직 저장된 분석 결과가 없습니다.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(records) { r -> Card { Column(Modifier.padding(14.dp)) { Text(r.createdAt, style = MaterialTheme.typography.labelMedium); Text(r.source, fontWeight = FontWeight.SemiBold); Text(r.labels, Modifier.padding(top = 4.dp)) } } } }
        Spacer(Modifier.weight(1f)); OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
    }
}
