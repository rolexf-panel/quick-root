package com.example.rootrunner

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

// Model Data
data class CommandItem(val id: Long, val name: String, val script: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request Root Access Start
        Shell.getShell()
        
        setContent {
            RootRunnerApp()
        }
    }
}

// --- Utils (Prefs & Storage) ---
fun saveCommandsToPrefs(context: Context, list: List<CommandItem>) {
    val json = Gson().toJson(list)
    val prefs = context.getSharedPreferences("root_runner_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("commands", json).apply()
}

fun loadCommandsFromPrefs(context: Context): List<CommandItem> {
    val prefs = context.getSharedPreferences("root_runner_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("commands", null) ?: return emptyList()
    val type = object : TypeToken<List<CommandItem>>() {}.type
    return Gson().fromJson(json, type)
}

// Fungsi Export yang kompatibel dengan Android modern (MediaStore)
fun saveToDownloads(context: Context, json: String): String {
    val fileName = "quickroot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/quickroot")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { output ->
                output.write(json.toByteArray())
            }
            return fileName
        }
    } else {
        // Legacy Storage
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "quickroot")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, fileName)
        FileOutputStream(file).use { it.write(json.toByteArray()) }
        return fileName
    }
    throw Exception("Failed to create file")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootRunnerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State
    var commands by remember { mutableStateOf(loadCommandsFromPrefs(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showInitialChoice by remember { mutableStateOf(false) }
    var showImportConflictDialog by remember { mutableStateOf(false) }
    var tempImportedCommands by remember { mutableStateOf<List<CommandItem>>(emptyList()) }

    fun updateCommands(newList: List<CommandItem>) {
        commands = newList
        saveCommandsToPrefs(context, newList)
    }

    // Import Launcher
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.use { r -> r.readText() }
                val type = object : TypeToken<List<CommandItem>>() {}.type
                val importedList: List<CommandItem> = Gson().fromJson(jsonString, type)
                tempImportedCommands = importedList
                showImportConflictDialog = true
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_json_error)) }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    if (commands.isNotEmpty()) {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Export")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (commands.isEmpty()) {
                // Tampilan Awal (Kosong)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.no_commands), style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showCreateDialog = true }) { Text(stringResource(R.string.create_new)) }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { launcher.launch("application/json") }) { Text(stringResource(R.string.import_json)) }
                }
            } else {
                // Tampilan List
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(commands) { cmd ->
                        CommandCard(cmd, 
                            onRun = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { Shell.cmd(cmd.script).exec() }
                                    snackbarHostState.showSnackbar(context.getString(R.string.msg_exec_success, cmd.name))
                                }
                            },
                            onDelete = { updateCommands(commands.filter { it.id != cmd.id }) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }

                // FAB (Tombol +)
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                    FloatingActionButton(
                        onClick = { showInitialChoice = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    ) { Icon(Icons.Default.Add, contentDescription = "Add") }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showInitialChoice) {
        AlertDialog(
            onDismissRequest = { showInitialChoice = false },
            title = { Text(stringResource(R.string.add_command)) },
            text = { Text(stringResource(R.string.add_cmd_desc)) },
            confirmButton = {
                Button(onClick = { showInitialChoice = false; showCreateDialog = true }) { Text(stringResource(R.string.create_new)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInitialChoice = false; launcher.launch("application/json") }) { Text(stringResource(R.string.import_json)) }
            }
        )
    }

    if (showCreateDialog) {
        CreateCommandDialog(
            onDismiss = { showCreateDialog = false },
            onSaveAndNext = { name, script, shouldContinue ->
                val newCmd = CommandItem(System.currentTimeMillis(), name, script)
                updateCommands(commands + newCmd)
                if (!shouldContinue) showCreateDialog = false
            }
        )
    }

    if (showExportDialog) {
        ExportPreviewDialog(
            jsonContent = Gson().toJson(commands),
            onDismiss = { showExportDialog = false },
            onExport = {
                try {
                    saveToDownloads(context, Gson().toJson(commands))
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_export_success)) }
                    showExportDialog = false
                } catch (e: Exception) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_export_fail, e.message)) }
                }
            }
        )
    }

    if (showImportConflictDialog) {
        AlertDialog(
            onDismissRequest = { showImportConflictDialog = false },
            title = { Text(stringResource(R.string.import_config_title)) },
            text = { Text(stringResource(R.string.import_config_desc)) },
            confirmButton = {
                Button(onClick = {
                    val remapped = tempImportedCommands.map { it.copy(id = System.currentTimeMillis() + (0..9999).random()) }
                    updateCommands(commands + remapped)
                    showImportConflictDialog = false
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_appended)) }
                }) { Text(stringResource(R.string.btn_append)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    updateCommands(tempImportedCommands)
                    showImportConflictDialog = false
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_overwritten)) }
                }) { Text(stringResource(R.string.btn_overwrite)) }
            }
        )
    }
}

// --- Components ---

@Composable
fun CommandCard(cmd: CommandItem, onRun: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cmd.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(cmd.script, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, tint = MaterialTheme.colorScheme.error, contentDescription = "Delete") }
            FilledIconButton(onClick = onRun) { Icon(Icons.Default.PlayArrow, contentDescription = "Run") }
        }
    }
}

@Composable
fun CreateCommandDialog(onDismiss: () -> Unit, onSaveAndNext: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var script by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_command_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = script, onValueChange = { script = it }, label = { Text(stringResource(R.string.label_script)) }, shape = RoundedCornerShape(12.dp), minLines = 3)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { if (name.isNotEmpty()) onSaveAndNext(name, script, false) }) { Text(stringResource(R.string.btn_save_finish)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { 
                    if (name.isNotEmpty()) {
                        onSaveAndNext(name, script, true)
                        name = ""; script = ""
                    }
                }) { Text(stringResource(R.string.btn_create_another)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun ExportPreviewDialog(jsonContent: String, onDismiss: () -> Unit, onExport: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.preview_json), style = MaterialTheme.typography.labelLarge)
                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Text(text = jsonContent, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.btn_export_download)) }
            }
        }
    }
}
