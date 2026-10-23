package com.callcraft.studio

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callcraft.studio.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Material You Dynamic Lilac Theme Palette
val LilacPrimary = Color(0xFF6750A4)
val LilacPrimaryDark = Color(0xFF4F378B)
val LilacBackground = Color(0xFFFEF7FF)
val LilacSurface = Color(0xFFF3EDF7)
val LilacSurfaceVariant = Color(0xFFECE6F0)
val LilacPrimaryContainer = Color(0xFFE8DEF8)
val LilacOnPrimaryContainer = Color(0xFF21005D)
val LilacBorder = Color(0xFFE7E0EC)
val LilacText = Color(0xFF1D1B20)
val LilacTextMuted = Color(0xFF49454F)

// Call Type Status Colors
val StatusIncoming = Color(0xFF10B981) // Mint / Emerald
val StatusOutgoing = Color(0xFF3B82F6) // Royal Electric Blue
val StatusMissed = Color(0xFFF43F5E)   // Soft Crimson / Coral Rose
val StatusRejected = Color(0xFFF59E0B) // Warm Amber / Tangerine

enum class AppScreen {
    Recents,
    Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = CallLogRepository(applicationContext)

        val lilacColorScheme = lightColorScheme(
            primary = LilacPrimary,
            background = LilacBackground,
            surface = LilacSurface,
            surfaceVariant = LilacSurfaceVariant,
            primaryContainer = LilacPrimaryContainer,
            onPrimaryContainer = LilacOnPrimaryContainer
        )

        setContent {
            MaterialTheme(colorScheme = lilacColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CallLogEditorApp(repository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogEditorApp(repository: CallLogRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AppScreen.Recents) }
    var logs by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Device SIM Hardware State
    var deviceSimState by remember { mutableStateOf(repository.getDeviceSimState()) }

    // Multi-Select States
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBulkActionDialog by remember { mutableStateOf(false) }

    // Contact Call History Sheet (Google Phone Style)
    var viewingGroup by remember { mutableStateOf<ContactCallGroup?>(null) }

    // Add / Edit Modal States
    var editingItem by remember { mutableStateOf<CallLogItem?>(null) }
    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var prefilledName by remember { mutableStateOf("") }
    var prefilledNumber by remember { mutableStateOf("") }
    var prefilledType by remember { mutableStateOf<Int?>(null) }
    var prefilledDuration by remember { mutableStateOf<Long?>(null) }
    var prefilledSimSlot by remember { mutableStateOf(deviceSimState.defaultSlot) }

    // Multi-tier Smooth Back Flow Handling
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedItemIds = emptySet()
    }

    BackHandler(enabled = !isSelectionMode && viewingGroup != null) {
        viewingGroup = null
    }

    BackHandler(enabled = !isSelectionMode && viewingGroup == null && currentScreen != AppScreen.Recents) {
        currentScreen = AppScreen.Recents
    }

    fun refreshLogs() {
        scope.launch {
            deviceSimState = repository.getDeviceSimState()
            runCatching { repository.getCallLogs() }
                .onSuccess { logs = it }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshLogs() }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshLogs() }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }

    // Google Phone Standard Contact Grouping
    val groupedLogs = remember(logs) {
        repository.groupCallLogs(logs)
    }

    // Update viewingGroup if logs change
    LaunchedEffect(groupedLogs) {
        if (viewingGroup != null) {
            val updated = groupedLogs.find { it.key == viewingGroup!!.key }
            viewingGroup = updated
        }
    }

    val allGroups = remember(groupedLogs, searchQuery) {
        if (searchQuery.isBlank()) groupedLogs
        else groupedLogs.filter { g ->
            g.name.contains(searchQuery, ignoreCase = true) ||
                    g.number.contains(searchQuery, ignoreCase = true)
        }
    }

    val missedGroups = remember(groupedLogs, searchQuery) {
        val filtered = groupedLogs.filter { g ->
            g.latestItem.type == CallLog.Calls.MISSED_TYPE || g.consecutiveMissedCount > 0
        }
        if (searchQuery.isBlank()) filtered
        else filtered.filter { g ->
            g.name.contains(searchQuery, ignoreCase = true) ||
                    g.number.contains(searchQuery, ignoreCase = true)
        }
    }

    val activeGroups = if (pagerState.currentPage == 0) allGroups else missedGroups

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        val selectedGroupCount = activeGroups.count { g -> g.calls.any { it.id in selectedItemIds } }
                        val titleText = if (selectedItemIds.size > selectedGroupCount && selectedGroupCount > 0) {
                            "$selectedGroupCount selected (${selectedItemIds.size} calls)"
                        } else {
                            "$selectedGroupCount selected"
                        }
                        Text(
                            text = titleText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = LilacText
                        )
                    } else {
                        when (currentScreen) {
                            AppScreen.Recents -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Call ",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = LilacText
                                        )
                                        Text(
                                            text = "Craft",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = LilacPrimary
                                        )
                                    }
                                    IconButton(
                                        onClick = { currentScreen = AppScreen.Settings },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Settings",
                                            tint = LilacTextMuted
                                        )
                                    }
                                }
                            }
                            AppScreen.Settings -> {
                                Column {
                                    Text(
                                        text = "Settings",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = LilacText
                                    )
                                    Text(
                                        text = "Preferences & Permissions",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LilacPrimary
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedItemIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection", tint = LilacText)
                        }
                    } else if (currentScreen != AppScreen.Recents) {
                        IconButton(onClick = { currentScreen = AppScreen.Recents }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = LilacText
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // Select All Toggle
                        IconButton(onClick = {
                            val activeIds = activeGroups.flatMap { it.calls.map { c -> c.id } }.toSet()
                            selectedItemIds = if (selectedItemIds.containsAll(activeIds)) {
                                emptySet()
                            } else {
                                activeIds
                            }
                        }) {
                            Icon(
                                Icons.Default.SelectAll,
                                contentDescription = "Select All",
                                tint = LilacPrimary
                            )
                        }

                        if (selectedItemIds.isNotEmpty()) {
                            IconButton(onClick = { showBulkActionDialog = true }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Bulk Actions",
                                    tint = LilacPrimary
                                )
                            }

                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = StatusMissed
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LilacBackground)
            )
        },
        floatingActionButton = {
            if (currentScreen == AppScreen.Recents && !isSelectionMode) {
                FloatingActionButton(
                    onClick = {
                        editingItem = null
                        prefilledName = ""
                        prefilledNumber = ""
                        prefilledType = null
                        prefilledDuration = null
                        prefilledSimSlot = deviceSimState.defaultSlot
                        isBottomSheetOpen = true
                    },
                    containerColor = LilacPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .padding(bottom = 16.dp, end = 6.dp)
                        .size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add New Call",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val isForward = (initialState == AppScreen.Recents && targetState == AppScreen.Settings)
                if (isForward) {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 3 },
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180))
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180))
                    )
                }
            },
            label = "ScreenTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { screen ->
            when (screen) {
                AppScreen.Recents -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Sliding Segmented Control: All Calls | Missed
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(LilacSurfaceVariant)
                                .padding(4.dp)
                        ) {
                            val tabWidth = maxWidth / 2
                            val indicatorOffset by animateDpAsState(
                                targetValue = if (pagerState.currentPage == 0) 0.dp else tabWidth,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "IndicatorOffset"
                            )

                            Box(
                                modifier = Modifier
                                    .offset(x = indicatorOffset)
                                    .width(tabWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LilacBackground)
                            )

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "All Calls",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pagerState.currentPage == 0) LilacText else LilacTextMuted
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch { pagerState.animateScrollToPage(1) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Missed",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pagerState.currentPage == 1) StatusMissed else LilacTextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Full-Width Sleek Search Bar (NO RIGHT BUTTON, ZERO WRAP)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = LilacPrimary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = LilacTextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            placeholder = {
                                Text("Search by name or number", fontSize = 14.sp, color = Color(0xFF79747E), maxLines = 1)
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = LilacSurface,
                                unfocusedContainerColor = LilacSurface,
                                focusedBorderColor = LilacPrimary,
                                unfocusedBorderColor = LilacBorder
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Pager for Grouped Recents
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { page ->
                            val currentList = if (page == 0) allGroups else missedGroups
                            val emptyMsg = if (page == 0) "No calls found" else "No missed calls found"

                            if (currentList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        emptyMsg,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF79747E)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 88.dp)
                                ) {
                                    items(currentList, key = { it.key }) { group ->
                                        val groupCallIds = group.calls.map { it.id }.toSet()
                                        val isItemSelected = groupCallIds.all { selectedItemIds.contains(it) }
                                        ContactGroupCardView(
                                            group = group,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = isItemSelected,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedItemIds = if (isItemSelected) {
                                                        selectedItemIds - groupCallIds
                                                    } else {
                                                        selectedItemIds + groupCallIds
                                                    }
                                                } else {
                                                    // Google Phone Standard: Open Call History Sheet
                                                    viewingGroup = group
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    isSelectionMode = true
                                                    selectedItemIds = groupCallIds
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AppScreen.Settings -> {
                    SettingsScreen(
                        context = context,
                        onRequestRole = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                            }
                        }
                    )
                }
            }
        }
    }

    // Google Phone Standard: Contact Call History Bottom Sheet
    if (viewingGroup != null) {
        val group = viewingGroup!!
        ContactHistoryBottomSheet(
            group = group,
            onDismiss = { viewingGroup = null },
            onLogNewCall = {
                viewingGroup = null
                editingItem = null
                prefilledName = group.name
                prefilledNumber = group.number
                prefilledType = CallLog.Calls.OUTGOING_TYPE
                prefilledDuration = 60L
                prefilledSimSlot = group.simSlot
                isBottomSheetOpen = true
            },
            onEditCall = { callItem ->
                viewingGroup = null
                editingItem = callItem
                isBottomSheetOpen = true
            },
            onDeleteCall = { callId ->
                scope.launch {
                    logs = logs.filter { it.id != callId }
                    repository.deleteCallLog(callId)
                    kotlinx.coroutines.delay(100)
                    refreshLogs()
                }
            },
            onDeleteAllHistory = {
                scope.launch {
                    val ids = group.calls.map { it.id }.toSet()
                    logs = logs.filter { it.id !in ids }
                    viewingGroup = null
                    repository.deleteCallLogs(ids)
                    kotlinx.coroutines.delay(100)
                    refreshLogs()
                }
            }
        )
    }

    // Material 3 Modal Bottom Sheet for Add / Edit
    if (isBottomSheetOpen) {
        AddEditCallBottomSheet(
            initialItem = editingItem,
            initialName = prefilledName,
            initialNumber = prefilledNumber,
            initialType = prefilledType,
            initialDuration = prefilledDuration,
            initialSimSlot = prefilledSimSlot,
            deviceSimState = deviceSimState,
            onDismiss = {
                isBottomSheetOpen = false
                prefilledName = ""
                prefilledNumber = ""
                prefilledType = null
                prefilledDuration = null
                prefilledSimSlot = deviceSimState.defaultSlot
            },
            onConfirm = { name, number, type, dateMillis, durationSec, simSlot ->
                scope.launch {
                    val isEditing = editingItem != null
                    val targetId = editingItem?.id ?: 0L
                    val finalName = name.trim().ifBlank { number.trim() }
                    val finalNumber = number.trim()

                    if (isEditing) {
                        logs = logs.map {
                            if (it.id == targetId) {
                                it.copy(
                                    name = finalName,
                                    number = finalNumber,
                                    type = type,
                                    dateMillis = dateMillis,
                                    durationSeconds = durationSec,
                                    simSlot = simSlot
                                )
                            } else it
                        }
                    }

                    isBottomSheetOpen = false
                    prefilledName = ""
                    prefilledNumber = ""
                    prefilledType = null
                    prefilledDuration = null
                    prefilledSimSlot = deviceSimState.defaultSlot

                    val success = if (!isEditing) {
                        repository.insertCallLog(
                            name = finalName,
                            number = finalNumber,
                            type = type,
                            dateMillis = dateMillis,
                            durationSeconds = durationSec,
                            simSlot = simSlot
                        ) != null
                    } else {
                        repository.updateCallLog(
                            editingItem!!.copy(
                                name = finalName,
                                number = finalNumber,
                                type = type,
                                dateMillis = dateMillis,
                                durationSeconds = durationSec,
                                simSlot = simSlot
                            )
                        )
                    }

                    if (!success) {
                        android.widget.Toast.makeText(
                            context,
                            "Could not save changes. Please ensure Call Log permission or Dialer Role is enabled.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }

                    kotlinx.coroutines.delay(150)
                    refreshLogs()
                }
            }
        )
    }

    // Material 3 Bulk Actions Bottom Sheet
    if (showBulkActionDialog && selectedItemIds.isNotEmpty()) {
        val selectedItems = logs.filter { it.id in selectedItemIds }
        BulkActionsBottomSheet(
            selectedCount = selectedItemIds.size,
            deviceSimState = deviceSimState,
            onDismiss = { showBulkActionDialog = false },
            onChangeType = { targetType ->
                showBulkActionDialog = false
                isSelectionMode = false
                val idsToUpdate = selectedItemIds
                selectedItemIds = emptySet()
                scope.launch {
                    logs = logs.map {
                        if (it.id in idsToUpdate) {
                            val dur = if (targetType == CallLog.Calls.MISSED_TYPE || targetType == CallLog.Calls.REJECTED_TYPE) 0L else if (it.durationSeconds == 0L) 60L else it.durationSeconds
                            it.copy(type = targetType, durationSeconds = dur)
                        } else it
                    }
                    repository.bulkUpdateCallType(selectedItems, targetType)
                    kotlinx.coroutines.delay(150)
                    refreshLogs()
                }
            },
            onChangeSimSlot = { targetSim ->
                showBulkActionDialog = false
                isSelectionMode = false
                val idsToUpdate = selectedItemIds
                selectedItemIds = emptySet()
                scope.launch {
                    logs = logs.map {
                        if (it.id in idsToUpdate) it.copy(simSlot = targetSim) else it
                    }
                    repository.bulkUpdateSimSlot(selectedItems, targetSim)
                    kotlinx.coroutines.delay(150)
                    refreshLogs()
                }
            }
        )
    }

    // Safe Delete Confirmation Dialog
    if (showDeleteConfirmDialog && selectedItemIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = LilacBackground,
            title = {
                Text(
                    "Delete Call Logs",
                    fontWeight = FontWeight.Bold,
                    color = LilacText
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete ${selectedItemIds.size} selected call log(s)? This action cannot be undone.",
                    color = LilacTextMuted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        val idsToDelete = selectedItemIds
                        isSelectionMode = false
                        selectedItemIds = emptySet()
                        scope.launch {
                            logs = logs.filter { it.id !in idsToDelete }
                            repository.deleteCallLogs(idsToDelete)
                            kotlinx.coroutines.delay(150)
                            refreshLogs()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusMissed),
                    shape = CircleShape
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = LilacPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// Google Phone Standard: Grouped Call Card (Ultra-Clean, NO TRASH ICON, NO CHEVRON)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactGroupCardView(
    group: ContactCallGroup,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val latest = group.latestItem
    val (icon, color) = when (latest.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived to StatusIncoming
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade to StatusOutgoing
        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed to StatusMissed
        CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.PhoneMissed to StatusRejected
        else -> Icons.Default.Call to Color.Gray
    }

    val isMissed = latest.type == CallLog.Calls.MISSED_TYPE

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) LilacPrimaryContainer.copy(alpha = 0.50f) else LilacSurface,
        shape = RoundedCornerShape(20.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, LilacPrimary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated Selection Checkbox
            AnimatedVisibility(visible = isSelectionMode) {
                Box(
                    modifier = Modifier.padding(end = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) LilacPrimary else Color.Transparent)
                            .border(
                                width = 2.dp,
                                color = if (isSelected) LilacPrimary else Color(0xFF79747E),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Call Type Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle Column: Full Width, Clean Name, Consecutive Missed Alert, SIM Badge & Number
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = group.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isMissed) StatusMissed else LilacText
                    )

                    // Google Phone Standard: Red alert count ONLY if 2+ consecutive missed calls
                    if (group.consecutiveMissedCount >= 2) {
                        Text(
                            text = "(${group.consecutiveMissedCount})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = StatusMissed
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (latest.simSlot == 1) LilacPrimaryContainer else Color(0xFFE0F2FE)
                    ) {
                        Text(
                            text = if (latest.simSlot == 1) "SIM 1" else "SIM 2",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (latest.simSlot == 1) LilacPrimary else Color(0xFF0284C7),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Text(
                        text = "${group.number} ${if (latest.durationSeconds > 0) "• ${formatDuration(latest.durationSeconds)}" else ""}",
                        fontSize = 12.sp,
                        color = LilacTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Time ONLY (NO TRASH, NO CHEVRON - CLEAN!)
            Text(
                text = formatCallTime(latest.dateMillis),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF79747E)
            )
        }
    }
}

// Google Phone Standard: Contact Call History Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactHistoryBottomSheet(
    group: ContactCallGroup,
    onDismiss: () -> Unit,
    onLogNewCall: () -> Unit,
    onEditCall: (CallLogItem) -> Unit,
    onDeleteCall: (Long) -> Unit,
    onDeleteAllHistory: () -> Unit
) {
    var showConfirmDeleteAll by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LilacBackground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Contact Monogram Avatar & Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(LilacPrimary, LilacPrimaryDark))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = group.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column {
                        Text(
                            text = group.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = LilacText
                        )
                        Text(
                            text = group.number,
                            fontSize = 13.sp,
                            color = LilacTextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LilacSurfaceVariant)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = LilacTextMuted, modifier = Modifier.size(16.dp))
                }
            }

            // Quick Actions: + Log Call & Delete All History
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onLogNewCall,
                    shape = RoundedCornerShape(14.dp),
                    color = LilacPrimary,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Call", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Surface(
                    onClick = { showConfirmDeleteAll = true },
                    shape = RoundedCornerShape(14.dp),
                    color = StatusMissed.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = StatusMissed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusMissed)
                    }
                }
            }

            // Timeline Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CALL HISTORY (${group.calls.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LilacPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            // History Timeline List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(group.calls, key = { it.id }) { call ->
                    val (icon, color) = when (call.type) {
                        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived to StatusIncoming
                        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade to StatusOutgoing
                        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed to StatusMissed
                        CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.PhoneMissed to StatusRejected
                        else -> Icons.Default.Call to Color.Gray
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = LilacSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditCall(call) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = CallLogItem.typeLabel(call.type),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LilacText
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (call.simSlot == 1) LilacPrimaryContainer else Color(0xFFE0F2FE)
                                    ) {
                                        Text(
                                            text = if (call.simSlot == 1) "SIM 1" else "SIM 2",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (call.simSlot == 1) LilacPrimary else Color(0xFF0284C7),
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${formatFullDateTime(call.dateMillis)} ${if (call.durationSeconds > 0) "• ${formatDuration(call.durationSeconds)}" else ""}",
                                    fontSize = 11.sp,
                                    color = LilacTextMuted
                                )
                            }

                            IconButton(
                                onClick = { onEditCall(call) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Call", tint = LilacPrimary, modifier = Modifier.size(15.dp))
                            }

                            IconButton(
                                onClick = { onDeleteCall(call.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Call", tint = StatusMissed, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAll = false },
            containerColor = LilacBackground,
            title = {
                Text("Delete All Calls", fontWeight = FontWeight.Bold, color = LilacText)
            },
            text = {
                Text(
                    "Are you sure you want to delete all calls with ${group.name}? This action cannot be undone.",
                    color = LilacTextMuted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteAll = false
                        onDeleteAllHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusMissed),
                    shape = CircleShape
                ) {
                    Text("Delete All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteAll = false }) {
                    Text("Cancel", color = LilacPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// Add & Edit Bottom Sheet with Real Hardware SIM Awareness
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCallBottomSheet(
    initialItem: CallLogItem?,
    initialName: String = "",
    initialNumber: String = "",
    initialType: Int? = null,
    initialDuration: Long? = null,
    initialSimSlot: Int = 1,
    deviceSimState: DeviceSimState,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Long, Long, Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var name by remember(initialItem?.id, initialName) { mutableStateOf(initialItem?.name?.takeIf { it != "Unknown" } ?: initialName) }
    var number by remember(initialItem?.id, initialNumber) { mutableStateOf(initialItem?.number ?: initialNumber) }
    var callType by remember(initialItem?.id, initialType) { mutableStateOf(initialItem?.type ?: (initialType ?: CallLog.Calls.INCOMING_TYPE)) }
    var selectedDateMillis by remember(initialItem?.id) { mutableStateOf(initialItem?.dateMillis ?: System.currentTimeMillis()) }

    // Auto select valid SIM slot based on phone hardware
    val effectiveSimSlot = if (initialItem != null) {
        initialItem.simSlot
    } else {
        if (initialSimSlot == 2 && !deviceSimState.hasSim2 && deviceSimState.hasSim1) 1
        else if (initialSimSlot == 1 && !deviceSimState.hasSim1 && deviceSimState.hasSim2) 2
        else initialSimSlot
    }

    var simSlot by remember(initialItem?.id, effectiveSimSlot) { mutableStateOf(effectiveSimSlot) }

    val initialSec = initialItem?.durationSeconds ?: (initialDuration ?: 60L)
    var durationHoursText by remember(initialItem?.id) { mutableStateOf((initialSec / 3600).toString()) }
    var durationMinsText by remember(initialItem?.id) { mutableStateOf(((initialSec % 3600) / 60).toString()) }
    var durationSecsText by remember(initialItem?.id) { mutableStateOf((initialSec % 60).toString()) }
    val scrollState = rememberScrollState()

    val currentDurationSec = remember(durationHoursText, durationMinsText, durationSecsText) {
        val h = durationHoursText.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val m = durationMinsText.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val s = durationSecsText.filter { it.isDigit() }.toLongOrNull() ?: 0L
        h * 3600L + m * 60L + s
    }

    val formattedDuration = remember(currentDurationSec) {
        val hrs = currentDurationSec / 3600
        val mins = (currentDurationSec % 3600) / 60
        val secs = currentDurationSec % 60
        when {
            hrs > 0 && mins > 0 && secs > 0 -> "${hrs}h ${mins}m ${secs}s"
            hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
            hrs > 0 && secs > 0 -> "${hrs}h ${secs}s"
            hrs > 0 -> "${hrs}h"
            mins > 0 && secs > 0 -> "${mins}m ${secs}s"
            mins > 0 -> "${mins}m"
            else -> "${secs}s"
        }
    }

    val applyPreset = { sec: Long ->
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        durationHoursText = h.toString()
        durationMinsText = m.toString()
        durationSecsText = s.toString()
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            if (contactUri != null) {
                val cursor = context.contentResolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (numberIndex != -1) {
                            val pickedNum = it.getString(numberIndex)
                            if (!pickedNum.isNullOrBlank()) number = pickedNum
                        }
                        if (nameIndex != -1) {
                            val pickedName = it.getString(nameIndex)
                            if (!pickedName.isNullOrBlank()) name = pickedName
                        }
                    }
                }
            }
        }
    }

    val callTypes = listOf(
        Triple(CallLog.Calls.INCOMING_TYPE, "Incoming", StatusIncoming),
        Triple(CallLog.Calls.OUTGOING_TYPE, "Outgoing", StatusOutgoing),
        Triple(CallLog.Calls.MISSED_TYPE, "Missed", StatusMissed),
        Triple(CallLog.Calls.REJECTED_TYPE, "Rejected", StatusRejected)
    )

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LilacBackground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF79747E).copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialItem == null) "New Call" else "Edit Call",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = LilacText
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LilacSurfaceVariant)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = LilacTextMuted, modifier = Modifier.size(16.dp))
                }
            }

            // Real Hardware SIM Detection in Add/Edit Sheet
            Column {
                Text("SIM Card", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LilacSurfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val simOptions = listOf(
                        Pair(1, deviceSimState.hasSim1),
                        Pair(2, deviceSimState.hasSim2)
                    )

                    simOptions.forEach { (slot, isInserted) ->
                        val isSlotSelected = simSlot == slot && isInserted
                        val displayLabel = if (isInserted) "SIM $slot" else "SIM $slot (No SIM)"

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .alpha(if (isInserted) 1f else 0.45f)
                                .background(if (isSlotSelected) (if (slot == 1) LilacPrimary else Color(0xFF0284C7)) else Color.Transparent)
                                .clickable(enabled = isInserted) { simSlot = slot }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = if (isSlotSelected) Color.White else LilacTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    displayLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSlotSelected) Color.White else LilacTextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Name
            Column {
                Text("Name", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g., Rohan Verma") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = LilacSurface,
                        unfocusedContainerColor = LilacSurface,
                        focusedBorderColor = LilacPrimary,
                        unfocusedBorderColor = LilacBorder
                    )
                )
            }

            // Phone Number
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Phone Number *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                    Surface(
                        onClick = {
                            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            contactPickerLauncher.launch(pickIntent)
                        },
                        shape = CircleShape,
                        color = LilacPrimaryContainer,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Contacts,
                                contentDescription = null,
                                tint = LilacPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Pick Contact",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LilacPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    placeholder = { Text("+91 98765 43210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = LilacSurface,
                        unfocusedContainerColor = LilacSurface,
                        focusedBorderColor = LilacPrimary,
                        unfocusedBorderColor = LilacBorder
                    )
                )
            }

            // Call Type
            Column {
                Text("Call Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LilacSurfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    callTypes.forEach { (typeVal, label, color) ->
                        val isSelected = callType == typeVal
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) LilacBackground else Color.Transparent)
                                .clickable {
                                    callType = typeVal
                                    if (typeVal != CallLog.Calls.MISSED_TYPE && typeVal != CallLog.Calls.REJECTED_TYPE) {
                                        if (currentDurationSec == 0L) {
                                            applyPreset(60L)
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) color else LilacTextMuted
                            )
                        }
                    }
                }
            }

            // Date & Time
            Column {
                Text("Date & Time", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    cal.set(Calendar.YEAR, year)
                                    cal.set(Calendar.MONTH, month)
                                    cal.set(Calendar.DAY_OF_MONTH, day)
                                    selectedDateMillis = cal.timeInMillis
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            cal.set(Calendar.HOUR_OF_DAY, hour)
                                            cal.set(Calendar.MINUTE, minute)
                                            selectedDateMillis = cal.timeInMillis
                                        },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        android.text.format.DateFormat.is24HourFormat(context)
                                    ).show()
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    color = LilacSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = LilacPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dateFormat.format(Date(selectedDateMillis)),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = LilacText
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(LilacPrimaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Change", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LilacPrimary)
                        }
                    }
                }
            }

            // Duration Inputs
            if (callType != CallLog.Calls.MISSED_TYPE && callType != CallLog.Calls.REJECTED_TYPE) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(LilacSurface)
                        .border(1.dp, LilacBorder, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = LilacPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Call Duration",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LilacText
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(LilacPrimaryContainer)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                formattedDuration,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = LilacPrimary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = durationHoursText,
                            onValueChange = { newText ->
                                durationHoursText = newText.filter { it.isDigit() }
                                coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = LilacText
                            ),
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LilacPrimaryContainer)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("hr", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LilacPrimary)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = LilacBackground,
                                unfocusedContainerColor = LilacBackground,
                                focusedBorderColor = LilacPrimary,
                                unfocusedBorderColor = LilacBorder
                            )
                        )

                        OutlinedTextField(
                            value = durationMinsText,
                            onValueChange = { newText ->
                                val clean = newText.filter { it.isDigit() }
                                val m = clean.toLongOrNull() ?: 0L
                                durationMinsText = if (m > 59 && (durationHoursText.toLongOrNull() ?: 0L) > 0) "59" else clean
                                coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = LilacText
                            ),
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LilacPrimaryContainer)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("min", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LilacPrimary)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = LilacBackground,
                                unfocusedContainerColor = LilacBackground,
                                focusedBorderColor = LilacPrimary,
                                unfocusedBorderColor = LilacBorder
                            )
                        )

                        OutlinedTextField(
                            value = durationSecsText,
                            onValueChange = { newText ->
                                val clean = newText.filter { it.isDigit() }
                                val s = clean.toLongOrNull() ?: 0L
                                durationSecsText = if (s > 59) "59" else clean
                                coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = LilacText
                            ),
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LilacPrimaryContainer)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("sec", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LilacPrimary)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = LilacBackground,
                                unfocusedContainerColor = LilacBackground,
                                focusedBorderColor = LilacPrimary,
                                unfocusedBorderColor = LilacBorder
                            )
                        )
                    }

                    // Quick Presets
                    Column {
                        Text(
                            "QUICK PRESETS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF79747E),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                30L to "30s",
                                60L to "1m",
                                300L to "5m",
                                900L to "15m",
                                3600L to "1h"
                            ).forEach { (sec, label) ->
                                val isSelected = currentDurationSec == sec
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) LilacPrimary else LilacBackground)
                                        .border(1.dp, if (isSelected) LilacPrimary else LilacBorder, RoundedCornerShape(12.dp))
                                        .clickable { applyPreset(sec) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else LilacText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = LilacSurfaceVariant),
                    shape = CircleShape
                ) {
                    Text("Cancel", color = LilacPrimary, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (number.isNotBlank()) {
                            val dur = if (callType == CallLog.Calls.MISSED_TYPE || callType == CallLog.Calls.REJECTED_TYPE) 0L else currentDurationSec
                            onConfirm(
                                name.trim(),
                                number.trim(),
                                callType,
                                selectedDateMillis,
                                dur,
                                simSlot
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = LilacPrimary),
                    shape = CircleShape
                ) {
                    Text("Save Log", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Material 3 Bulk Actions Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkActionsBottomSheet(
    selectedCount: Int,
    deviceSimState: DeviceSimState,
    onDismiss: () -> Unit,
    onChangeType: (Int) -> Unit,
    onChangeSimSlot: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LilacBackground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Bulk Actions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = LilacText
                    )
                    Text(
                        "$selectedCount call log(s) selected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LilacPrimary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LilacSurfaceVariant)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = LilacTextMuted, modifier = Modifier.size(16.dp))
                }
            }

            // Convert Call Type in Bulk
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "CONVERT CALL TYPE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LilacTextMuted,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple(CallLog.Calls.INCOMING_TYPE, "Incoming", StatusIncoming),
                        Triple(CallLog.Calls.OUTGOING_TYPE, "Outgoing", StatusOutgoing),
                        Triple(CallLog.Calls.MISSED_TYPE, "Missed", StatusMissed),
                        Triple(CallLog.Calls.REJECTED_TYPE, "Rejected", StatusRejected)
                    ).forEach { (t, label, color) ->
                        Surface(
                            onClick = { onChangeType(t) },
                            shape = RoundedCornerShape(12.dp),
                            color = color.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }

            // Set SIM Card in Bulk (Hardware aware)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "SET SIM CARD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LilacTextMuted,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { if (deviceSimState.hasSim1) onChangeSimSlot(1) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (deviceSimState.hasSim1) LilacPrimaryContainer else LilacSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (deviceSimState.hasSim1) 1f else 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SimCard, contentDescription = null, tint = LilacPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (deviceSimState.hasSim1) "Switch to SIM 1" else "SIM 1 (No SIM)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LilacPrimary
                            )
                        }
                    }

                    Surface(
                        onClick = { if (deviceSimState.hasSim2) onChangeSimSlot(2) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (deviceSimState.hasSim2) Color(0xFFE0F2FE) else LilacSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (deviceSimState.hasSim2) 1f else 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SimCard, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (deviceSimState.hasSim2) "Switch to SIM 2" else "SIM 2 (No SIM)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    context: Context,
    onRequestRole: () -> Unit
) {
    val isRoleHeld = remember {
        derivedStateOf {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                true
            }
        }
    }.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Permissions & System",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = LilacPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = LilacBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isRoleHeld) StatusIncoming.copy(alpha = 0.15f) else LilacPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isRoleHeld) Icons.Default.Shield else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isRoleHeld) StatusIncoming else LilacPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    "Dialer Role",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LilacText
                                )
                                Text(
                                    "Default Phone App permission",
                                    fontSize = 12.sp,
                                    color = LilacTextMuted
                                )
                            }
                        }

                        Switch(
                            checked = isRoleHeld,
                            onCheckedChange = { onRequestRole() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = LilacPrimary,
                                uncheckedThumbColor = Color(0xFF79747E),
                                uncheckedTrackColor = LilacSurfaceVariant,
                                uncheckedBorderColor = LilacBorder
                            ),
                            thumbContent = {
                                if (isRoleHeld) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = LilacPrimary
                                    )
                                }
                            }
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRoleHeld) StatusIncoming.copy(alpha = 0.10f) else StatusRejected.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRoleHeld) StatusIncoming else StatusRejected)
                            )
                            Text(
                                text = if (isRoleHeld) "Role Active • Ready to edit logs" else "Role Inactive • Turn switch ON to edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRoleHeld) StatusIncoming else StatusRejected
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = LilacPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Restore original dialer anytime from Phone Settings → Default Apps.",
                            fontSize = 11.sp,
                            color = LilacTextMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = LilacSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Call ", fontSize = 12.sp, fontWeight = FontWeight.Black, color = LilacText)
                    Text("Craft", fontSize = 12.sp, fontWeight = FontWeight.Black, color = LilacPrimary)
                }
                Text("v1.0.0", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacPrimary)
            }
        }
    }
}

fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    if (hrs > 0) return "${hrs}h ${mins}m"
    if (mins > 0) return "${mins}m ${secs}s"
    return "${secs}s"
}

fun formatCallTime(dateMillis: Long): String {
    val date = Date(dateMillis)
    val now = Calendar.getInstance()
    val callCal = Calendar.getInstance().apply { time = date }

    val isToday = now.get(Calendar.YEAR) == callCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == callCal.get(Calendar.DAY_OF_YEAR)

    now.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = now.get(Calendar.YEAR) == callCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == callCal.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    if (isToday) return timeFormat.format(date)
    if (isYesterday) return "Yesterday"
    return SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
}

fun formatFullDateTime(dateMillis: Long): String {
    val date = Date(dateMillis)
    val now = Calendar.getInstance()
    val callCal = Calendar.getInstance().apply { time = date }

    val isToday = now.get(Calendar.YEAR) == callCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == callCal.get(Calendar.DAY_OF_YEAR)

    now.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = now.get(Calendar.YEAR) == callCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == callCal.get(Calendar.DAY_OF_YEAR)

    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
    if (isToday) return "Today • $timeStr"
    if (isYesterday) return "Yesterday • $timeStr"
    return SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()).format(date)
}
