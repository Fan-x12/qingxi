package com.example.videoparser

import android.content.Context
import androidx.activity.compose.BackHandler
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class ImageServiceConfig(val name: String = "", val endpoint: String = "", val model: String = "", val id: String = "default")

/** 使用设备密钥加密凭据，并将配置存放在不参与系统备份的目录。 */
internal class ImageServiceStore(context: Context) {
    private val file = java.io.File(context.noBackupFilesDir, "image-service.properties")
    private fun properties() = java.util.Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun loadAll(): List<ImageServiceConfig> {
        val props = properties()
        val ids = props.getProperty("profile.ids")?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
        if (ids.isNotEmpty()) return ids.map { id -> ImageServiceConfig(
            props.getProperty("profile.$id.name", ""), props.getProperty("profile.$id.endpoint", ""),
            props.getProperty("profile.$id.model", ""), id)
        }
        val legacy = ImageServiceConfig(props.getProperty("name", ""), props.getProperty("endpoint", ""), props.getProperty("model", ""))
        return if (legacy.name.isNotBlank() || legacy.endpoint.isNotBlank()) listOf(legacy) else emptyList()
    }
    fun load(): ImageServiceConfig = loadAll().firstOrNull { it.id == properties().getProperty("profile.active") }
        ?: loadAll().firstOrNull() ?: ImageServiceConfig()
    fun hasKey(id: String = load().id): Boolean {
        val props = properties()
        val legacyKey = if (id == "default" && props.getProperty("profile.ids").isNullOrBlank()) props.getProperty("key") else null
        return props.getProperty("profile.$id.key")?.isNotBlank() == true || legacyKey?.isNotBlank() == true
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("image-service", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("image-service", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveProfile(config: ImageServiceConfig, replacementKey: String, activate: Boolean = true) {
        val props = properties()
        val existingIds = props.getProperty("profile.ids")?.split(',')?.filter(String::isNotBlank).orEmpty().toMutableList()
        if (existingIds.isEmpty() && (props.getProperty("name").orEmpty().isNotBlank() || props.getProperty("endpoint").orEmpty().isNotBlank())) {
            props.setProperty("profile.default.name", props.getProperty("name", ""))
            props.setProperty("profile.default.endpoint", props.getProperty("endpoint", ""))
            props.setProperty("profile.default.model", props.getProperty("model", ""))
            props.getProperty("key")?.let { props.setProperty("profile.default.key", it) }
            props.getProperty("iv")?.let { props.setProperty("profile.default.iv", it) }
            existingIds += "default"
        }
        val ids = (existingIds + config.id).distinct()
        val oldEndpoint = props.getProperty("profile.${config.id}.endpoint", "").ifBlank { props.getProperty("endpoint", "") }
        require(replacementKey.isNotBlank() || oldEndpoint == config.endpoint || !hasKey(config.id)) { "修改接口地址时请重新填写密钥" }
        props.setProperty("profile.ids", ids.joinToString(","))
        props.setProperty("profile.${config.id}.name", config.name)
        props.setProperty("profile.${config.id}.endpoint", config.endpoint)
        props.setProperty("profile.${config.id}.model", config.model)
        if (activate) props.setProperty("profile.active", config.id)
        if (replacementKey.isNotBlank()) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            props.setProperty("profile.${config.id}.key", Base64.encodeToString(cipher.doFinal(replacementKey.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            props.setProperty("profile.${config.id}.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
        write(props)
    }
    fun save(config: ImageServiceConfig, replacementKey: String) = saveProfile(config, replacementKey)
    fun setActive(id: String) { val props = properties(); require(loadAll().any { it.id == id }); props.setProperty("profile.active", id); write(props) }
    fun delete(id: String) {
        val props = properties(); val ids = loadAll().map { it.id }.filterNot { it == id }
        props.setProperty("profile.ids", ids.joinToString(",")); listOf("name", "endpoint", "model", "key", "iv").forEach { props.remove("profile.$id.$it") }
        if (props.getProperty("profile.active") == id) props.setProperty("profile.active", ids.firstOrNull() ?: "")
        write(props)
    }
    fun apiKey(id: String = load().id): String {
        val props = properties()
        val useLegacy = id == "default" && props.getProperty("profile.ids").isNullOrBlank()
        val encrypted = (props.getProperty("profile.$id.key") ?: if (useLegacy) props.getProperty("key") else null) ?: return ""
        val iv = (props.getProperty("profile.$id.iv") ?: if (useLegacy) props.getProperty("iv") else null) ?: return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }
    private fun write(props: java.util.Properties) {
        val atomicFile = android.util.AtomicFile(file); val stream = atomicFile.startWrite()
        try { props.store(stream, null); atomicFile.finishWrite(stream) } catch (error: Exception) { atomicFile.failWrite(stream); throw error }
    }
}

@Composable
internal fun ImageServiceSettings(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ImageServiceStore(context.applicationContext) }
    var saved by remember { mutableStateOf(runCatching { store.load() }.getOrDefault(ImageServiceConfig())) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(runCatching { store.loadAll() }.getOrDefault(emptyList())) }
    var activeId by rememberSaveable { mutableStateOf(saved.id) }
    var name by rememberSaveable { mutableStateOf(saved.name) }
    var endpoint by rememberSaveable { mutableStateOf(saved.endpoint) }
    var model by rememberSaveable { mutableStateOf(saved.model) }
    var apiKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var probeFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val client = remember { ImageServiceClient() }
    val activeSaved = profiles.firstOrNull { it.id == activeId } ?: ImageServiceConfig(id = activeId)
    val hasKey = runCatching { store.hasKey(activeId) }.getOrDefault(false)
    val editable = !busy && !saving
    fun config() = ImageServiceConfig(name.trim(), endpoint.trim(), model.trim(), activeId)
    fun selectProfile(profile: ImageServiceConfig) {
        activeId = profile.id; name = profile.name; endpoint = profile.endpoint; model = profile.model
        apiKey = ""; models = emptyList(); filter = ""; message = null; saveMessage = null
        probeFailed = false
        editing = true
    }
    fun addProfile() {
        val profile = ImageServiceConfig(id = UUID.randomUUID().toString().take(8))
        selectProfile(profile)
    }
    val dirty = config() != activeSaved || apiKey.isNotBlank()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun navigate(action: () -> Unit) {
        if (!editable) return
        if (dirty) pendingAction = action else action()
    }
    fun back() {
        if (editing) navigate { apiKey = ""; editing = false } else onClose()
    }
    BackHandler { back() }
    if (pendingAction != null) {
        StudioConfirmDialog(title = "放弃未保存的修改？",
            message = "当前填写的内容尚未保存，你可以继续编辑或放弃修改。",
            confirmLabel = "放弃修改", dismissLabel = "继续编辑",
            onConfirm = { val action = pendingAction; pendingAction = null; action?.invoke() },
            onDismiss = { pendingAction = null })
    }
    fun credential(): String {
        require(apiKey.isNotBlank() || endpoint.trim() == activeSaved.endpoint || !hasKey) { "地址已修改，请重新填写密钥" }
        return apiKey.trim().ifBlank { store.apiKey(activeId) }
    }
    // Listing models doubles as the connection check: it exercises the address and credentials.
    fun fetchModels() {
        if (busy) return
        busy = true; message = null; probeFailed = false
        scope.launch {
            try {
                val current = config()
                val key = withContext(Dispatchers.IO) { credential() }
                val ids = client.models(current, key).distinct().sorted()
                models = ids; filter = ""
                message = if (ids.isEmpty()) "连接成功，但接口没有返回模型；可手动填写模型名称。"
                    else "连接成功，获取到 ${ids.size} 个模型。" + if (current.model in ids) "当前模型已在列表中。" else "请选择支持生图的模型。"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { probeFailed = true; message = e.message ?: "连接失败" }
            finally { busy = false }
        }
    }
    if (!editing) {
        StudioPage("生图接口", onClose) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioSection("已配置接口") {
                    if (profiles.isEmpty()) {
                        Text("还没有生图接口。添加一个兼容 OpenAI Images 的服务即可开始。",
                            color = StudioStyle.muted, fontSize = 13.sp, lineHeight = 19.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                    }
                    profiles.forEachIndexed { index, profile ->
                        if (index > 0) StudioDivider()
                        val active = profile.id == saved.id
                        val detail = listOf(
                            profile.endpoint.trim().toHttpUrlOrNull()?.host ?: profile.endpoint.ifBlank { "未填写地址" },
                            profile.model.ifBlank { "未设置模型" }
                        ).joinToString(" · ")
                        StudioNavigation(Icons.Default.CloudQueue, profile.name.ifBlank { "未命名接口" }, detail,
                            trailing = if (active) ({ StatusPill("使用中") }) else null) {
                            if (editable) selectProfile(profile)
                        }
                    }
                }
                StudioAction("添加新接口", Modifier.fillMaxWidth(), enabled = editable) { addProfile() }
                StudioFootnote("点击接口进入编辑，保存并启用后用于对话生图。密钥经设备密钥加密保存在本机，不会随备份导出。")
            }
        }
        return
    }
    StudioPage(if (profiles.any { it.id == activeId }) "编辑接口" else "添加接口", { back() }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioSection("接口") {
                    StudioFormRow("名称", name, { name = it; saveMessage = null }, "例如：我的生图服务", enabled = editable)
                    StudioDivider(FormRowDividerInset)
                    StudioFormRow("地址", endpoint, { endpoint = it; models = emptyList(); message = null; saveMessage = null },
                        "https://example.com/v1", enabled = editable, keyboardType = KeyboardType.Uri)
                    StudioDivider(FormRowDividerInset)
                    StudioFormRow("密钥", apiKey, { apiKey = it; message = null; models = emptyList() },
                        if (hasKey) "已保存，留空则保留原密钥" else "填写 API 密钥", secret = true, enabled = editable)
                }
                StudioFootnote("兼容 OpenAI Images 的 /v1/images/generations 同步接口，地址需使用 HTTPS。")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioSection("模型") {
                    StudioFormRow("模型", model, { model = it; message = null; saveMessage = null }, "填写或从列表选择", enabled = editable,
                        trailing = {
                            FormRowAction(
                                if (models.isEmpty()) "获取列表" else "刷新", busy,
                                enabled = editable && endpoint.isNotBlank() && (apiKey.isNotBlank() || hasKey)
                            ) { fetchModels() }
                        })
                    if (models.isNotEmpty()) {
                        StudioDivider(FormRowDividerInset)
                        StudioFormRow("筛选", filter, { filter = it }, "输入模型名称筛选", enabled = editable)
                        val filtered = models.filter { it.contains(filter, ignoreCase = true) }
                        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
                            .clip(StudioStyle.control).background(StudioStyle.soft)) {
                            if (filtered.isEmpty()) Text("没有匹配的模型", color = StudioStyle.muted, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp))
                            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 232.dp)) {
                                items(filtered.size, key = { filtered[it] }) { index ->
                                    val id = filtered[index]
                                    val chosen = id == model
                                    Row(Modifier.fillMaxWidth().appClickable(enabled = editable) { model = id; saveMessage = null }
                                        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(id, color = StudioStyle.ink, fontSize = 13.sp, lineHeight = 18.sp,
                                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                        if (chosen) Icon(Icons.Default.Check, "已选择", tint = StudioStyle.ink, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                val status = message
                if (status != null) ProbeStatus(status, probeFailed)
                else StudioFootnote("获取列表会同时验证地址与密钥；列表不代表模型已支持生图，也可直接手动填写。")
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            saveMessage?.let { Text(it, color = Color(0xFFA2533C), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            StudioAction(if (saving) "正在保存…" else "保存并启用", Modifier.fillMaxWidth(), primary = true, enabled = editable) {
                saveMessage = null
                val url = endpoint.trim().toHttpUrlOrNull()
                if ((!hasKey && apiKey.isBlank()) || name.isBlank() || model.isBlank() || url == null || !url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) {
                    saveMessage = "请填写名称、密钥、模型及有效的 HTTPS 地址"
                } else {
                    val current = config().copy(id = activeId)
                    val replacement = apiKey.trim()
                    saving = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { store.saveProfile(current, replacement) }
                            profiles = store.loadAll()
                            apiKey = ""; saved = current; editing = false
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { saveMessage = if (e is IllegalArgumentException) e.message else "保存失败，请重试" }
                        finally { saving = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Text(text, color = StudioStyle.accent, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(StudioStyle.accentSoft, RoundedCornerShape(50.dp)).padding(horizontal = 9.dp, vertical = 5.dp))
}

@Composable
private fun ProbeStatus(text: String, failed: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(if (failed) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null,
            tint = if (failed) Color(0xFFA2533C) else Color(0xFF2E8B57), modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Text(text, color = if (failed) Color(0xFFA2533C) else StudioStyle.ink, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

// Compact action living inside a form row; the spinner replaces the label while working.
@Composable
private fun FormRowAction(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 32.dp).widthIn(min = 72.dp).clip(RoundedCornerShape(50.dp))
        .background(if (enabled || busy) StudioStyle.ink else StudioStyle.soft)
        .appClickable(enabled = enabled && !busy, rippleColor = Color.White, onClick = onClick)
        .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(label, color = if (enabled) Color.White else StudioStyle.muted.copy(alpha = .6f),
            fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    }
}


private val FormRowLabelWidth = 56.dp
private val FormRowDividerInset = 16.dp + FormRowLabelWidth + 12.dp

// One grouped-form row: fixed label on the left, borderless single-line field on the right.
@Composable
internal fun StudioFormRow(label: String, value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false,
    enabled: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, trailing: (@Composable () -> Unit)? = null) {
    val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = if (secret) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = if (focused) StudioStyle.ink else StudioStyle.muted, fontSize = 14.sp, lineHeight = 20.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.width(FormRowLabelWidth))
        BasicTextField(value, onChange, enabled = enabled, singleLine = true, interactionSource = interactions,
            textStyle = TextStyle(color = StudioStyle.ink, fontSize = 14.sp, lineHeight = 20.sp), cursorBrush = SolidColor(Color.Black),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else keyboardType),
            visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.weight(1f).padding(vertical = 15.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, color = Color(0xFF9AA0A8), fontSize = 14.sp, lineHeight = 20.sp,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    inner()
                }
            })
        when {
            secret -> AppIconButton(onClick = { if (enabled) revealed = !revealed }, modifier = Modifier.size(44.dp)) {
                Icon(if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    if (revealed) "隐藏密钥" else "显示密钥", tint = StudioStyle.muted, modifier = Modifier.size(18.dp))
            }
            trailing != null -> trailing()
        }
    }
}
