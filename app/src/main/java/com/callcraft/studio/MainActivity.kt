package com.callcraft.studio

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.callcraft.studio.service.FakeCallHelper
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callcraft.studio.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
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
    Splash,
    Recents,
    Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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

    var currentScreen by remember { mutableStateOf(AppScreen.Splash) }
    var bottomToastMessage by remember { mutableStateOf<String?>(null) }
    var logs by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }

    fun showBottomToast(message: String) {
        bottomToastMessage = message
        scope.launch {
            kotlinx.coroutines.delay(2000)
            if (bottomToastMessage == message) {
                bottomToastMessage = null
            }
        }
    }
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

    // Fake Call Simulator States
    var showTopMenu by remember { mutableStateOf(false) }
    var isFakeCallSetupOpen by remember { mutableStateOf(false) }
    var fakeCallName by remember { mutableStateOf("Boss") }
    var fakeCallNumber by remember { mutableStateOf("+91 98765 43210") }
    var fakeCallSimSlot by remember { mutableStateOf(deviceSimState.defaultSlot) }
    var fakeCallBrand by remember { mutableStateOf("pixel") }
    var fakeCallSound by remember { mutableStateOf(true) }
    var fakeCallVibrate by remember { mutableStateOf(true) }
    var fakeCallSaveToLog by remember { mutableStateOf(true) }
    var fakeCallCountdownSec by remember { mutableStateOf(0) }
    var isFakeIncomingActive by remember { mutableStateOf(false) }
    var isFakeCallAnswered by remember { mutableStateOf(false) }
    var fakeCallAnswerTime by remember { mutableStateOf(0L) }
    val fakeCallHelper = remember { FakeCallHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            fakeCallHelper.cleanup()
        }
    }

    LaunchedEffect(fakeCallCountdownSec) {
        if (fakeCallCountdownSec > 0) {
            kotlinx.coroutines.delay(1000)
            if (fakeCallCountdownSec == 1) {
                fakeCallCountdownSec = 0
                isFakeIncomingActive = true
                fakeCallHelper.startRinging(fakeCallSound, fakeCallVibrate)
            } else {
                fakeCallCountdownSec -= 1
            }
        }
    }

    // Multi-tier Smooth Back Flow Handling
    BackHandler(enabled = isFakeIncomingActive) {
        fakeCallHelper.stopRinging()
        isFakeIncomingActive = false
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!isFakeIncomingActive && !isFakeCallAnswered && currentScreen != AppScreen.Splash) {
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
                                    Box {
                                        IconButton(
                                            onClick = { showTopMenu = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "Menu",
                                                tint = LilacTextMuted
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showTopMenu,
                                            onDismissRequest = { showTopMenu = false },
                                            shape = RoundedCornerShape(20.dp),
                                            containerColor = LilacBackground,
                                            shadowElevation = 8.dp,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder),
                                            modifier = Modifier.width(220.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            "Fake Call Simulator",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = LilacText
                                                        )
                                                        Text(
                                                            "Escape awkward situations",
                                                            fontSize = 10.sp,
                                                            color = LilacTextMuted
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                Icons.Default.Bolt,
                                                                contentDescription = null,
                                                                tint = Color(0xFFF59E0B),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                onClick = {
                                                    showTopMenu = false
                                                    isFakeCallSetupOpen = true
                                                }
                                            )

                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                                color = LilacBorder.copy(alpha = 0.6f)
                                            )

                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            "Settings",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = LilacText
                                                        )
                                                        Text(
                                                            "Dialer Role permission",
                                                            fontSize = 10.sp,
                                                            color = LilacTextMuted
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = LilacPrimary.copy(alpha = 0.12f),
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                Icons.Default.Settings,
                                                                contentDescription = null,
                                                                tint = LilacPrimary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                onClick = {
                                                    showTopMenu = false
                                                    currentScreen = AppScreen.Settings
                                                }
                                            )
                                        }
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
                            else -> {}
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
        }
        },
        floatingActionButton = {
            if (!isFakeIncomingActive && !isFakeCallAnswered && currentScreen == AppScreen.Recents && !isSelectionMode) {
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
                val isSplash = (initialState == AppScreen.Splash || targetState == AppScreen.Splash)
                val isForward = (initialState == AppScreen.Recents && targetState == AppScreen.Settings)
                if (isSplash) {
                    fadeIn(animationSpec = tween(300)).togetherWith(
                        fadeOut(animationSpec = tween(250))
                    )
                } else if (isForward) {
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
                        // Floating Fake Call Countdown Banner
                        AnimatedVisibility(
                            visible = fakeCallCountdownSec > 0,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF59E0B),
                                shadowElevation = 3.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Fake Call in ${fakeCallCountdownSec}s",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = "$fakeCallName ($fakeCallNumber)",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { fakeCallCountdownSec = 0 },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            "Cancel",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

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
                                val mainListState = rememberLazyListState()
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = mainListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(end = if (currentList.size > 1) 12.dp else 0.dp),
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
                                                            val remaining = selectedItemIds - groupCallIds
                                                            if (remaining.isEmpty()) isSelectionMode = false
                                                            remaining
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
                }

                AppScreen.Splash -> {
                    SplashScreen(
                        onSplashFinished = {
                            currentScreen = AppScreen.Recents
                        }
                    )
                }

                AppScreen.Settings -> {
                    SettingsScreen(
                        context = context,
                        onRequestRole = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                            }
                        },
                        onShowToast = { showBottomToast(it) }
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
                val current = viewingGroup
                if (current != null) {
                    val remaining = current.calls.filter { it.id != callId }
                    if (remaining.isEmpty()) viewingGroup = null
                    else viewingGroup = current.copy(calls = remaining, latestItem = remaining.first())
                }
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
                editingItem = null
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
                    editingItem = null
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

    // Fake Call Setup Bottom Sheet
    if (isFakeCallSetupOpen) {
        FakeCallBottomSheet(
            deviceSimState = deviceSimState,
            initialName = fakeCallName,
            initialNumber = fakeCallNumber,
            initialSimSlot = fakeCallSimSlot,
            initialBrand = fakeCallBrand,
            initialSound = fakeCallSound,
            initialVibrate = fakeCallVibrate,
            initialSaveToLog = fakeCallSaveToLog,
            onDismiss = { isFakeCallSetupOpen = false },
            onSchedule = { name, number, simSlot, brand, delaySeconds, sound, vibrate, saveToLog ->
                fakeCallName = name
                fakeCallNumber = number
                fakeCallSimSlot = simSlot
                fakeCallBrand = brand
                fakeCallSound = sound
                fakeCallVibrate = vibrate
                fakeCallSaveToLog = saveToLog
                isFakeCallSetupOpen = false

                if (delaySeconds <= 0) {
                    isFakeIncomingActive = true
                    fakeCallHelper.startRinging(sound, vibrate)
                } else {
                    fakeCallCountdownSec = delaySeconds
                    showBottomToast("Fake call scheduled in ${if (delaySeconds >= 60) "${delaySeconds / 60}m ${delaySeconds % 60}s" else "${delaySeconds}s"}")
                }
            }
        )
    }

    // Fake Incoming Call Screen (Authentic Multi-Brand Clone)
    if (isFakeIncomingActive) {
        FakeIncomingCallScreen(
            callerName = fakeCallName,
            callerNumber = fakeCallNumber,
            simSlot = fakeCallSimSlot,
            brand = fakeCallBrand,
            onAnswer = {
                fakeCallHelper.stopRinging()
                isFakeIncomingActive = false
                isFakeCallAnswered = true
                fakeCallAnswerTime = System.currentTimeMillis()
            },
            onDecline = {
                fakeCallHelper.stopRinging()
                isFakeIncomingActive = false
            }
        )
    }

    // Fake Active In-Call Screen (Authentic Multi-Brand Clone)
    if (isFakeCallAnswered) {
        FakeActiveCallScreen(
            callerName = fakeCallName,
            callerNumber = fakeCallNumber,
            simSlot = fakeCallSimSlot,
            brand = fakeCallBrand,
            fakeCallHelper = fakeCallHelper,
            onEndCall = { durationSec ->
                fakeCallHelper.stopCallMedia()
                isFakeCallAnswered = false
                if (fakeCallSaveToLog) {
                    scope.launch {
                        repository.insertCallLog(
                            name = fakeCallName,
                            number = fakeCallNumber,
                            type = CallLog.Calls.INCOMING_TYPE,
                            dateMillis = fakeCallAnswerTime,
                            durationSeconds = durationSec,
                            simSlot = fakeCallSimSlot
                        )
                        showBottomToast("Saved to Call Log (${formatDuration(durationSec)})")
                        kotlinx.coroutines.delay(150)
                        refreshLogs()
                    }
                }
            }
        )
    }

    // Bottom Pill Toast Host (with custom shrink and slide-down exit animation, elevated above FAB)
    AnimatedVisibility(
        visible = bottomToastMessage != null,
        enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(initialScale = 0.85f),
        exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { it / 2 }) + scaleOut(targetScale = 0.75f),
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.BottomCenter)
            .padding(bottom = 88.dp)
            .zIndex(100f)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF1E1F24),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isTimer = bottomToastMessage?.contains("scheduled", ignoreCase = true) == true ||
                              bottomToastMessage?.contains("Fake call", ignoreCase = true) == true
                Icon(
                    imageVector = if (isTimer) Icons.Default.Bolt else Icons.Default.Check,
                    contentDescription = null,
                    tint = if (isTimer) Color(0xFFF59E0B) else StatusIncoming,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = bottomToastMessage ?: "",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
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

// Eye-Catching Vertical Fast-Scroll Slider with Live Counter & Drag Support
@Composable
fun VerticalScrollSlider(
    listState: LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    val visibleCount by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1) } }
    if (totalItems <= 1) return

    val coroutineScope = rememberCoroutineScope()
    var sliderHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val isIndicatorActive = listState.isScrollInProgress || isDragging

    val alphaAnim by animateFloatAsState(
        targetValue = if (isIndicatorActive) 1f else 0.2f,
        animationSpec = tween(durationMillis = if (isIndicatorActive) 150 else 500),
        label = "sliderAlpha"
    )

    val thumbWidthAnim by animateDpAsState(
        targetValue = if (isIndicatorActive) 5.dp else 3.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumbWidth"
    )

    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val firstVisibleOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }
    val firstItemSize by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1 }
    }

    val itemFraction = (firstVisibleOffset.toFloat() / firstItemSize.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    val smoothIndex = firstVisibleIndex.toFloat() + itemFraction
    val maxScroll = (totalItems - visibleCount).coerceAtLeast(1).toFloat()
    val scrollFraction = (smoothIndex / maxScroll).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .width(28.dp)
            .onGloballyPositioned { layoutCoordinates ->
                sliderHeightPx = layoutCoordinates.size.height.toFloat()
            }
            .pointerInput(totalItems) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (sliderHeightPx > 0) {
                            val fraction = (offset.y / sliderHeightPx).coerceIn(0f, 1f)
                            val targetIndex = (fraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        if (sliderHeightPx > 0) {
                            val fraction = (change.position.y / sliderHeightPx).coerceIn(0f, 1f)
                            val targetIndex = (fraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopEnd
    ) {
        val totalHeightDp = maxHeight
        val minThumbHeightDp = 34.dp
        val computedThumbHeightDp = (totalHeightDp * (visibleCount.toFloat() / totalItems.toFloat()))
            .coerceIn(minThumbHeightDp, totalHeightDp * 0.7f)
        val availableTrackLengthDp = (totalHeightDp - computedThumbHeightDp).coerceAtLeast(0.dp)
        val thumbOffsetDp = availableTrackLengthDp * scrollFraction

        val smoothOffsetDp by animateDpAsState(
            targetValue = thumbOffsetDp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
            label = "smoothOffset"
        )

        // Subtle rounded background track
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .fillMaxHeight()
                .width(2.5.dp)
                .clip(RoundedCornerShape(50))
                .background(LilacPrimary.copy(alpha = 0.12f * alphaAnim))
        )

        // Ultra-smooth smart gradient thumb pill (No number badge, smooth glide)
        Box(
            modifier = Modifier
                .padding(end = 3.dp)
                .offset(y = smoothOffsetDp)
                .width(thumbWidthAnim)
                .height(computedThumbHeightDp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF7C5CE6),
                            LilacPrimary
                        )
                    )
                )
                .alpha(alphaAnim)
        )
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(44.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(LilacTextMuted.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Contact Monogram Avatar & Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = LilacText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = group.number,
                            fontSize = 13.sp,
                            color = LilacTextMuted,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LilacSurfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = LilacTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Quick Actions: + Log Call & Delete All History (Sleek Compact Pills)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onLogNewCall,
                    shape = RoundedCornerShape(50),
                    color = LilacPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Log Call", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Surface(
                    onClick = { showConfirmDeleteAll = true },
                    shape = RoundedCornerShape(50),
                    color = StatusMissed.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusMissed.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = StatusMissed, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Delete All", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = StatusMissed)
                    }
                }
            }

            // Timeline Section Header + Modern Horizontal Filter Slider Tabs
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

            // History Timeline List with Eye-Catching Vertical Scroll Slider
            val listState = rememberLazyListState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 410.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = if (group.calls.size > 1) 14.dp else 0.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
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
                                        color = LilacTextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(end = 2.dp)
                                ) {
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
                .padding(top = 10.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialItem == null) "New Call" else "Edit Call",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = LilacText
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LilacSurfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = LilacTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
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
                        } else {
                            Toast.makeText(context, "Please enter a phone number", Toast.LENGTH_SHORT).show()
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
                .padding(top = 10.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LilacSurfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = LilacTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
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
fun UpiOriginalIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f

        val greenPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(5f * sx, 4.5f * sy)
            lineTo(12.5f * sx, 12f * sy)
            lineTo(5f * sx, 19.5f * sy)
            lineTo(8.5f * sx, 19.5f * sy)
            lineTo(16f * sx, 12f * sy)
            lineTo(8.5f * sx, 4.5f * sy)
            close()
        }
        drawPath(greenPath, color = Color(0xFF097939))

        val orangePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(11.5f * sx, 4.5f * sy)
            lineTo(19f * sx, 12f * sy)
            lineTo(11.5f * sx, 19.5f * sy)
            lineTo(15f * sx, 19.5f * sy)
            lineTo(22.5f * sx, 12f * sy)
            lineTo(15f * sx, 4.5f * sy)
            close()
        }
        drawPath(orangePath, color = Color(0xFFF37021))
    }
}

@Composable
fun BinanceOriginalIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val color = Color(0xFFF0B90B)

        val center = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * sx, 9.36f * sy)
            lineTo(14.64f * sx, 12f * sy)
            lineTo(12f * sx, 14.64f * sy)
            lineTo(9.36f * sx, 12f * sy)
            close()
        }
        drawPath(center, color = color)

        val top = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * sx, 2f * sy)
            lineTo(16.62f * sx, 6.62f * sy)
            lineTo(13.91f * sx, 9.34f * sy)
            lineTo(12f * sx, 7.43f * sy)
            lineTo(10.09f * sx, 9.34f * sy)
            lineTo(7.38f * sx, 6.62f * sy)
            close()
        }
        drawPath(top, color = color)

        val bottom = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * sx, 22f * sy)
            lineTo(7.38f * sx, 17.38f * sy)
            lineTo(10.09f * sx, 14.66f * sy)
            lineTo(12f * sx, 16.57f * sy)
            lineTo(13.91f * sx, 14.66f * sy)
            lineTo(16.62f * sx, 17.38f * sy)
            close()
        }
        drawPath(bottom, color = color)

        val left = androidx.compose.ui.graphics.Path().apply {
            moveTo(2f * sx, 12f * sy)
            lineTo(4.72f * sx, 9.28f * sy)
            lineTo(7.43f * sx, 12f * sy)
            lineTo(4.72f * sx, 14.72f * sy)
            close()
        }
        drawPath(left, color = color)

        val right = androidx.compose.ui.graphics.Path().apply {
            moveTo(22f * sx, 12f * sy)
            lineTo(19.28f * sx, 9.28f * sy)
            lineTo(16.57f * sx, 12f * sy)
            lineTo(19.28f * sx, 14.72f * sy)
            close()
        }
        drawPath(right, color = color)
    }
}

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Checking : AppUpdateState()
    data class UpToDate(val version: String) : AppUpdateState()
    data class Available(val version: String, val downloadUrl: String, val releaseNotes: String) : AppUpdateState()
    data class Downloading(val progress: Int) : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val scale = remember { Animatable(0.75f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 800,
                    easing = FastOutSlowInEasing
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 600,
                    easing = LinearEasing
                )
            )
        }
        kotlinx.coroutines.delay(1200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LilacBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder),
                shadowElevation = 8.dp
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "CallCraft Logo",
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Call",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = LilacText
                    )
                    Text(
                        "Craft",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = LilacPrimary
                    )
                }
                Text(
                    "Modern Call Log Editor",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LilacTextMuted,
                    letterSpacing = 1.2.sp
                )
            }
        }

        Text(
            "v1.0.0",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = LilacTextMuted.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
fun SettingsScreen(
    context: Context,
    onRequestRole: () -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<AppUpdateState>(AppUpdateState.Idle) }

    var callLogGranted by remember { mutableStateOf(false) }
    var contactsGranted by remember { mutableStateOf(false) }
    var phoneStateGranted by remember { mutableStateOf(false) }
    var isRoleHeld by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    fun refreshDiagnostics() {
        callLogGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        phoneStateGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        isRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshDiagnostics()
    }

    fun requestPermission(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(Unit) {
        refreshDiagnostics()
    }

    val diagnosticReport = remember(callLogGranted, contactsGranted, phoneStateGranted, isRoleHeld) {
        buildString {
            appendLine("========================================")
            appendLine("CALLCRAFT SYSTEM & RUNTIME DIAGNOSTICS")
            appendLine("========================================")
            appendLine("App Version: 1.0.0 (Release)")
            appendLine("Package: ${context.packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("[PERMISSIONS]")
            appendLine("- Default Dialer Role: ${if (isRoleHeld) "GRANTED" else "NOT GRANTED"}")
            appendLine("- READ_CALL_LOG: ${if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
            appendLine("- WRITE_CALL_LOG: ${if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
            appendLine("- READ_CONTACTS: ${if (contactsGranted) "GRANTED" else "DENIED"}")
            appendLine("- READ_PHONE_STATE: ${if (phoneStateGranted) "GRANTED" else "DENIED"}")
            appendLine()
            appendLine("[HARDWARE & TELEPHONY]")
            appendLine("- Dual SIM Detection: Active")
            appendLine("- Primary Storage: Available")
            appendLine("- Diagnostic Check: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("[APP EVENT LOG]")
            appendLine("[INFO] Core SQLite CallLog Engine Initialized")
            appendLine("[INFO] Modern Lilac Material 3 Theme Loaded")
            appendLine("[INFO] In-Place GitHub Updater Configured")
            appendLine("[OK] System Ready")
            appendLine("========================================")
        }
    }

    fun sendLogsToDeveloper() {
        val subject = "CallCraft Diagnostic Report - ${Build.MODEL}"
        try {
            val mailtoUri = Uri.parse("mailto:sachinmandawi@gmail.com?subject=${Uri.encode(subject)}&body=${Uri.encode(diagnosticReport)}")
            val emailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, diagnosticReport)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(emailIntent, "Send Diagnostic Logs"))
        } catch (e: Exception) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("CallCraft Diagnostics", diagnosticReport))
            onShowToast("Logs copied! Open your email to paste.")
        }
    }

    fun openBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMail() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:sachinmandawi@gmail.com?subject=CallCraft%20Inquiry")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(emailIntent, "Send Email"))
        } catch (e: Exception) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Developer Email", "sachinmandawi@gmail.com"))
            onShowToast("Email copied: sachinmandawi@gmail.com")
        }
    }

    fun copyToClipboard(label: String, text: String, message: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        onShowToast(message)
    }

    fun checkForUpdates() {
        updateState = AppUpdateState.Checking
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/sachinmandawi/Call-Craft/releases/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "CallCraft-Android")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tagName = json.optString("tag_name", "").replace("v", "").trim()
                    val notes = json.optString("body", "Bug fixes and improvements")
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (downloadUrl.isNotEmpty()) {
                            if (tagName == "1.0.0") {
                                updateState = AppUpdateState.UpToDate(tagName)
                            } else {
                                updateState = AppUpdateState.Available(tagName, downloadUrl, notes)
                            }
                        } else {
                            updateState = AppUpdateState.UpToDate(tagName.ifEmpty { "1.0.0" })
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateState = AppUpdateState.Error("HTTP ${conn.responseCode}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateState = AppUpdateState.Error("Network error")
                }
            }
        }
    }

    fun downloadAndInstallUpdate(downloadUrl: String) {
        updateState = AppUpdateState.Downloading(0)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val updateDir = File(context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                val apkFile = File(updateDir, "CallCraft-latest.apk")
                if (apkFile.exists()) apkFile.delete()

                var currentUrl = downloadUrl
                var conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "CallCraft-Android")
                conn.instanceFollowRedirects = true

                var redirectStatus = conn.responseCode
                if (redirectStatus == HttpURLConnection.HTTP_MOVED_TEMP || redirectStatus == HttpURLConnection.HTTP_MOVED_PERM || redirectStatus == 307 || redirectStatus == 308) {
                    val redirectedUrl = conn.getHeaderField("Location")
                    if (!redirectedUrl.isNullOrEmpty()) {
                        currentUrl = redirectedUrl
                        conn = URL(currentUrl).openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", "CallCraft-Android")
                    }
                }

                val totalLength = conn.contentLength
                val input = conn.inputStream
                val output = FileOutputStream(apkFile)
                val buffer = ByteArray(8192)
                var downloaded: Long = 0
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    downloaded += bytesRead
                    output.write(buffer, 0, bytesRead)
                    if (totalLength > 0) {
                        val progress = ((downloaded * 100) / totalLength).toInt()
                        withContext(Dispatchers.Main) {
                            updateState = AppUpdateState.Downloading(progress)
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()

                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                        Toast.makeText(context, "Allow unknown app installs for CallCraft", Toast.LENGTH_SHORT).show()
                        val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(manageIntent)
                    }

                    val apkUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(installIntent)
                    updateState = AppUpdateState.Idle
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateState = AppUpdateState.Error("Update failed")
                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // SECTION 1: PERMISSIONS & SYSTEM DIAGNOSTICS
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "SYSTEM & PERMISSIONS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = LilacPrimary,
                modifier = Modifier.padding(start = 4.dp),
                letterSpacing = 0.8.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = LilacBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Item: Dialer Role
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (isRoleHeld) Icons.Default.Shield else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isRoleHeld) StatusIncoming else LilacPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Default Dialer Role",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LilacText
                            )
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
                            )
                        )
                    }

                    HorizontalDivider(color = LilacBorder.copy(alpha = 0.4f))

                    // Item: Call Log
                    DiagnosticCleanRow(
                        title = "Call Logs Access",
                        isGranted = callLogGranted,
                        onGrant = {
                            requestPermission(
                                arrayOf(
                                    Manifest.permission.READ_CALL_LOG,
                                    Manifest.permission.WRITE_CALL_LOG
                                )
                            )
                        }
                    )

                    // Item: Contacts
                    DiagnosticCleanRow(
                        title = "Contacts Access",
                        isGranted = contactsGranted,
                        onGrant = {
                            requestPermission(arrayOf(Manifest.permission.READ_CONTACTS))
                        }
                    )

                    // Item: Phone State
                    DiagnosticCleanRow(
                        title = "Phone State Access",
                        isGranted = phoneStateGranted,
                        onGrant = {
                            requestPermission(arrayOf(Manifest.permission.READ_PHONE_STATE))
                        }
                    )

                    // Shortcut to App Settings
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "Open System App Settings",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LilacPrimary
                        )
                    }
                }
            }
        }

        // SECTION 2: DEVELOPER & COMMUNITY (Clean, authentic icons)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "DEVELOPER & COMMUNITY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = LilacPrimary,
                modifier = Modifier.padding(start = 4.dp),
                letterSpacing = 0.8.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = LilacBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Sachin Mandawi Profile (GitHub Brand Icon)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openBrowser("https://github.com/sachinmandawi") }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF24292E))
                                .border(1.dp, LilacBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = "GitHub",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sachin Mandawi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                            Text("GitHub Profile", fontSize = 10.sp, color = LilacTextMuted)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = LilacTextMuted, modifier = Modifier.size(14.dp))
                    }

                    HorizontalDivider(color = LilacBorder.copy(alpha = 0.4f))

                    // CallCraft Repository (Real CallCraft Logo)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openBrowser("https://github.com/sachinmandawi/Call-Craft") }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "CallCraft",
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, LilacBorder, RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CallCraft Repository", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                            Text("Source code & releases", fontSize = 10.sp, color = LilacTextMuted)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = LilacTextMuted, modifier = Modifier.size(14.dp))
                    }

                    HorizontalDivider(color = LilacBorder.copy(alpha = 0.4f))

                    // Contact Developer (Official Gmail Icon)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openMail() }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, LilacBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_gmail),
                                contentDescription = "Gmail",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Contact Developer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                            Text("sachinmandawi@gmail.com", fontSize = 10.sp, color = LilacTextMuted)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = LilacTextMuted, modifier = Modifier.size(14.dp))
                    }

                    HorizontalDivider(color = LilacBorder.copy(alpha = 0.4f))

                    // Report Issue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openBrowser("https://github.com/sachinmandawi/Call-Craft/issues/new") }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusMissed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = StatusMissed, modifier = Modifier.size(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Report an Issue", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                            Text("GitHub Issues", fontSize = 10.sp, color = LilacTextMuted)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = LilacTextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // SECTION 3: OTHER APPS (CleanBar with real logo)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "OTHER APPS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = LilacPrimary,
                modifier = Modifier.padding(start = 4.dp),
                letterSpacing = 0.8.sp
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openBrowser("https://github.com/sachinmandawi/CleanBar") },
                shape = RoundedCornerShape(14.dp),
                color = LilacBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cleanbar_logo),
                        contentDescription = "CleanBar",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, LilacBorder, RoundedCornerShape(8.dp))
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("CleanBar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = LilacPrimaryContainer
                            ) {
                                Text("No Root", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = LilacOnPrimaryContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Text("Status Bar Hider via Shizuku", fontSize = 10.sp, color = LilacTextMuted)
                    }

                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = LilacTextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }

        // SECTION 4: SUPPORT DEVELOPER (App-Themed, Original Icons, Zero Clutter)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "SUPPORT DEVELOPER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = LilacPrimary,
                modifier = Modifier.padding(start = 4.dp),
                letterSpacing = 0.8.sp
            )

            // UPI Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        copyToClipboard("UPI ID", "darkcaptain@ybl", "UPI ID copied: darkcaptain@ybl")
                    },
                shape = RoundedCornerShape(14.dp),
                color = LilacBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, LilacBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            UpiOriginalIcon(modifier = Modifier.size(18.dp))
                        }

                        Column {
                            Text("UPI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                            Text("darkcaptain@ybl", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                        }
                    }

                    IconButton(
                        onClick = {
                            copyToClipboard("UPI ID", "darkcaptain@ybl", "UPI ID copied: darkcaptain@ybl")
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy UPI ID",
                            tint = LilacPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Binance Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        copyToClipboard("Binance ID", "1138545342", "Binance ID copied: 1138545342")
                    },
                shape = RoundedCornerShape(14.dp),
                color = LilacBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF0B90B).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFF0B90B).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BinanceOriginalIcon(modifier = Modifier.size(18.dp))
                        }

                        Column {
                            Text("Binance Pay", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                            Text("1138545342", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                        }
                    }

                    IconButton(
                        onClick = {
                            copyToClipboard("Binance ID", "1138545342", "Binance ID copied: 1138545342")
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Binance ID",
                            tint = LilacPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // SECTION 5: APP VERSION & IN-PLACE UPDATER (Clean)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "ABOUT & UPDATES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = LilacPrimary,
                modifier = Modifier.padding(start = 4.dp),
                letterSpacing = 0.8.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = LilacBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Call", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LilacText)
                                    Text("Craft", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LilacPrimary)
                                }
                                Text("Version 1.0.0", fontSize = 10.sp, color = LilacTextMuted)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = updateState !is AppUpdateState.Checking && updateState !is AppUpdateState.Downloading) {
                                    checkForUpdates()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading)
                                LilacPrimary.copy(alpha = 0.5f) else LilacPrimary
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (updateState is AppUpdateState.Checking) "Checking..." else "Check Update",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    when (val state = updateState) {
                        is AppUpdateState.UpToDate -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusIncoming.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = StatusIncoming, modifier = Modifier.size(14.dp))
                                    Text("v${state.version} is the latest version", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusIncoming)
                                }
                            }
                        }

                        is AppUpdateState.Available -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = LilacPrimaryContainer.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LilacPrimary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Update Available: v${state.version}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacOnPrimaryContainer)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { downloadAndInstallUpdate(state.downloadUrl) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = LilacPrimary
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 5.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Install Update", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        is AppUpdateState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Downloading update...", fontSize = 10.sp, color = LilacText)
                                    Text("${state.progress}%", fontSize = 10.sp, color = LilacPrimary, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = LilacPrimary,
                                    trackColor = LilacSurfaceVariant
                                )
                            }
                        }

                        is AppUpdateState.Error -> {
                            Text(state.message, fontSize = 10.sp, color = StatusMissed, modifier = Modifier.padding(start = 2.dp))
                        }

                        else -> {}
                    }
                }
            }
        }

        // SECTION 6: APP LOGS & DIAGNOSTICS
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "APP LOGS & DIAGNOSTICS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = LilacPrimary,
                modifier = Modifier.padding(start = 4.dp),
                letterSpacing = 0.8.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = LilacBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LilacPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = LilacPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text("System Diagnostics", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                                Text("Error tracking & telemetry", fontSize = 10.sp, color = LilacTextMuted)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StatusIncoming.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(StatusIncoming))
                                Text("Active", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StatusIncoming)
                            }
                        }
                    }

                    Text(
                        "Encountering an issue? Inspect system logs, copy the diagnostic telemetry report, or send it directly to the developer for instant analysis.",
                        fontSize = 10.sp,
                        color = LilacTextMuted,
                        lineHeight = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // View Logs Button (Sleek Slim Pill)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showLogsDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = LilacBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("View Logs", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = LilacText)
                            }
                        }

                        // Copy Logs Button (Sleek Slim Pill)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    copyToClipboard("CallCraft Diagnostics", diagnosticReport, "Diagnostic report copied to clipboard")
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = LilacBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(11.dp), tint = LilacPrimary)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Copy", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = LilacText)
                            }
                        }

                        // Send Button (Sleek Slim Pill)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { sendLogsToDeveloper() },
                            shape = RoundedCornerShape(8.dp),
                            color = LilacPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Send", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard("CallCraft Diagnostics", diagnosticReport, "Diagnostic report copied to clipboard")
                    }
                ) {
                    Text("Copy Report", color = LilacPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("Close", color = LilacTextMuted, fontSize = 12.sp)
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = LilacPrimary, modifier = Modifier.size(20.dp))
                    Text("Diagnostics & Error Logs", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LilacText)
                }
            },
            text = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E1E2E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF313244))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = diagnosticReport,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFA6ADC8),
                            lineHeight = 14.sp
                        )
                    }
                }
            },
            containerColor = LilacBackground,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
fun DiagnosticCleanRow(
    title: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LilacText)

        if (isGranted) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = StatusIncoming.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(StatusIncoming))
                    Text("Granted", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StatusIncoming)
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onGrant() },
                shape = RoundedCornerShape(6.dp),
                color = LilacPrimary
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Grant", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
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

// ─────────────────────────────────────────────────────────────
// FAKE CALL SIMULATOR COMPOSABLES (COMPACT & 4-BRAND CLONES)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeCallBottomSheet(
    deviceSimState: DeviceSimState,
    initialName: String,
    initialNumber: String,
    initialSimSlot: Int,
    initialBrand: String = "pixel",
    initialSound: Boolean,
    initialVibrate: Boolean,
    initialSaveToLog: Boolean,
    onDismiss: () -> Unit,
    onSchedule: (name: String, number: String, simSlot: Int, brand: String, delaySeconds: Int, sound: Boolean, vibrate: Boolean, saveToLog: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf(initialName) }
    var number by remember { mutableStateOf(initialNumber) }
    var simSlot by remember { mutableStateOf(if (initialSimSlot == 2 && !deviceSimState.hasSim2 && deviceSimState.hasSim1) 1 else initialSimSlot) }
    var selectedBrand by remember { mutableStateOf(initialBrand) }
    var soundEnabled by remember { mutableStateOf(initialSound) }
    var vibrateEnabled by remember { mutableStateOf(initialVibrate) }
    var saveToLog by remember { mutableStateOf(initialSaveToLog) }

    // Timing (Presets + Custom Delay)
    val delayPresets = remember {
        listOf(
            3 to "Now (3s)",
            10 to "10s",
            30 to "30s",
            60 to "1m",
            300 to "5m"
        )
    }
    var delayMinsText by remember { mutableStateOf("0") }
    var delaySecsText by remember { mutableStateOf("3") }
    var selectedPresetSec by remember { mutableStateOf<Int?>(3) }

    val namePresets = listOf("Boss", "Mom", "Doctor", "Pizza", "Police")

    // Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                context.contentResolver.query(
                    contactUri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (numCol != -1) number = cursor.getString(numCol) ?: number
                        if (nameCol != -1) name = cursor.getString(nameCol) ?: name
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LilacBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        "Fake Call Simulator",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = LilacText
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(LilacSurfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = LilacTextMuted, modifier = Modifier.size(15.dp))
                }
            }

            // 1. Phone Brand Style Selector (Google Pixel, Samsung One UI, Xiaomi HyperOS, Apple iPhone)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "CALL SCREEN BRAND / THEME",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF79747E),
                    letterSpacing = 0.5.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair("pixel", "Google Pixel"),
                            Pair("samsung", "Samsung One UI")
                        ).forEach { (bId, bName) ->
                            val isSelected = selectedBrand == bId
                            Surface(
                                onClick = { selectedBrand = bId },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) LilacPrimary else LilacSurfaceVariant,
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, LilacBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = bName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else LilacText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth()
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair("hyperos", "Xiaomi HyperOS"),
                            Pair("ios", "Apple iPhone")
                        ).forEach { (bId, bName) ->
                            val isSelected = selectedBrand == bId
                            Surface(
                                onClick = { selectedBrand = bId },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) LilacPrimary else LilacSurfaceVariant,
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, LilacBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = bName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else LilacText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // 2. Quick Caller Presets
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "QUICK CALLER PRESET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF79747E),
                    letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    namePresets.forEach { preset ->
                        val isSelected = name == preset
                        Surface(
                            onClick = {
                                name = preset
                                when (preset) {
                                    "Boss" -> number = "+91 98123 45678"
                                    "Mom" -> number = "+91 98980 12345"
                                    "Doctor" -> number = "+91 98234 56789"
                                    "Pizza" -> number = "+91 98765 00000"
                                    "Police" -> number = "112"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) LilacPrimary else LilacSurfaceVariant,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else LilacText,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // 3. Compact Caller Name Input
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Caller Name", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LilacSurface)
                        .border(1.dp, LilacBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LilacText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. Compact Caller Phone Number + Pick Contact
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Phone Number", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                    Surface(
                        onClick = {
                            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            contactPickerLauncher.launch(pickIntent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = LilacPrimaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            Icon(Icons.Default.Contacts, contentDescription = null, tint = LilacPrimary, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Pick Contact", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LilacPrimary)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LilacSurface)
                        .border(1.dp, LilacBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = number,
                        onValueChange = { number = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LilacText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 5. Compact SIM Slot Selection
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("SIM Card", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LilacSurfaceVariant)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf(
                        Pair(1, deviceSimState.hasSim1),
                        Pair(2, deviceSimState.hasSim2)
                    ).forEach { (slot, isInserted) ->
                        val isSlotSelected = simSlot == slot && isInserted
                        val displayLabel = if (isInserted) "SIM $slot" else "SIM $slot (No SIM)"

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .alpha(if (isInserted) 1f else 0.45f)
                                .background(if (isSlotSelected) (if (slot == 1) LilacPrimary else Color(0xFF0284C7)) else Color.Transparent)
                                .clickable(enabled = isInserted) { simSlot = slot }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSlotSelected) Color.White else LilacText
                            )
                        }
                    }
                }
            }

            // 6. Compact Trigger Delay & Inputs
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val totalCalculatedSec = ((delayMinsText.toIntOrNull() ?: 0) * 60) + (delaySecsText.toIntOrNull() ?: 0)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TRIGGER DELAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF79747E), letterSpacing = 0.5.sp)
                    Text(
                        text = "Ringing in ${if (totalCalculatedSec >= 60) "${totalCalculatedSec / 60}m ${totalCalculatedSec % 60}s" else "${totalCalculatedSec}s"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LilacPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    delayPresets.forEach { (sec, label) ->
                        val isSelected = selectedPresetSec == sec
                        Surface(
                            onClick = {
                                selectedPresetSec = sec
                                delayMinsText = (sec / 60).toString()
                                delaySecsText = (sec % 60).toString()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) LilacPrimaryContainer else LilacSurfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.2.dp, LilacPrimary) else null
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) LilacPrimary else LilacText,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LilacSurface)
                            .border(1.dp, LilacBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicTextField(
                                value = delayMinsText,
                                onValueChange = { newText ->
                                    val clean = newText.filter { it.isDigit() }
                                    delayMinsText = clean
                                    val m = clean.toIntOrNull() ?: 0
                                    val s = delaySecsText.toIntOrNull() ?: 0
                                    val total = (m * 60) + s
                                    selectedPresetSec = delayPresets.find { it.first == total }?.first
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText),
                                modifier = Modifier.weight(1f)
                            )
                            Text("min", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LilacSurface)
                            .border(1.dp, LilacBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicTextField(
                                value = delaySecsText,
                                onValueChange = { newText ->
                                    val clean = newText.filter { it.isDigit() }
                                    val sVal = clean.toIntOrNull() ?: 0
                                    delaySecsText = if (sVal > 59) "59" else clean
                                    val m = delayMinsText.toIntOrNull() ?: 0
                                    val s = delaySecsText.toIntOrNull() ?: 0
                                    val total = (m * 60) + s
                                    selectedPresetSec = delayPresets.find { it.first == total }?.first
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText),
                                modifier = Modifier.weight(1f)
                            )
                            Text("sec", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LilacTextMuted)
                        }
                    }
                }
            }

            // 7. Compact Switches Container
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = LilacSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, LilacBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = LilacPrimary, modifier = Modifier.size(16.dp))
                            Text("Ringtone Sound", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it },
                            modifier = Modifier.scale(0.8f),
                            colors = SwitchDefaults.colors(checkedTrackColor = LilacPrimary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = LilacPrimary, modifier = Modifier.size(16.dp))
                            Text("Vibration Pattern", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                        }
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = { vibrateEnabled = it },
                            modifier = Modifier.scale(0.8f),
                            colors = SwitchDefaults.colors(checkedTrackColor = LilacPrimary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.History, contentDescription = null, tint = LilacPrimary, modifier = Modifier.size(16.dp))
                            Column {
                                Text("Auto-Save to Call History", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LilacText)
                                Text("Records in real Android Call Log", fontSize = 9.sp, color = LilacTextMuted)
                            }
                        }
                        Switch(
                            checked = saveToLog,
                            onCheckedChange = { saveToLog = it },
                            modifier = Modifier.scale(0.8f),
                            colors = SwitchDefaults.colors(checkedTrackColor = LilacPrimary)
                        )
                    }
                }
            }

            // 8. Schedule Action Button
            Button(
                onClick = {
                    val m = delayMinsText.toIntOrNull() ?: 0
                    val s = delaySecsText.toIntOrNull() ?: 0
                    val totalSec = (m * 60) + s
                    onSchedule(
                        name.ifBlank { "Unknown" },
                        number.ifBlank { "+91 98765 43210" },
                        simSlot,
                        selectedBrand,
                        totalSec,
                        soundEnabled,
                        vibrateEnabled,
                        saveToLog
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LilacPrimary)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Schedule Fake Call", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// AUTHENTIC 4-BRAND INCOMING CALL SCREENS
// ─────────────────────────────────────────────────────────────

@Composable
fun FakeIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    simSlot: Int,
    brand: String = "pixel",
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AnswerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    when (brand) {
        "samsung" -> {
            // Samsung One UI Authentic Incoming Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF132039), Color(0xFF0C1424))
                        )
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 28.dp)
                    ) {
                        Text(
                            text = callerName,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Jio 5G (SIM $simSlot) • $callerNumber",
                            fontSize = 13.sp,
                            color = Color(0xFF93C5FD)
                        )
                    }

                    // Samsung Frosted Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .border(1.5.dp, Color(0x44FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = callerName.take(1).uppercase(),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Samsung Swipe-style Circles (Answer Left, Decline Right) + Send Message Pill
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                        .clickable(onClick = onAnswer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Answer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .clickable(onClick = onDecline),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Decline", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }

                        // Samsung Signature "Send message" Pull-up Pill
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                Text("Send message", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }
                }
            }
        }
        "hyperos" -> {
            // Xiaomi HyperOS Authentic Incoming Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF17181A))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 28.dp)
                    ) {
                        Text(
                            text = callerName,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mobile | India • SIM $simSlot",
                            fontSize = 13.sp,
                            color = Color(0xFFA1A1AA)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = callerNumber,
                            fontSize = 12.sp,
                            color = Color(0xFF71717A)
                        )
                    }

                    // HyperOS Avatar with Ring
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF27272A))
                            .border(2.dp, Color(0xFF3F3F46), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = callerName.take(1).uppercase(),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF27272A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F3F46))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(14.dp))
                                Text("Quick response", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFA1A1AA))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .clickable(onClick = onDecline),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Decline", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                        .clickable(onClick = onAnswer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Answer", fontSize = 11.sp, color = Color(0xFFA1A1AA))
                            }
                        }
                    }
                }
            }
        }
        "ios" -> {
            // Apple iPhone iOS Authentic Incoming Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 28.dp)
                    ) {
                        Text(
                            text = callerName,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "mobile • $callerNumber",
                            fontSize = 14.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }

                    // iOS Round Monogram Avatar
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1E))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = callerName.take(1).uppercase(),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        // iOS Signature Auxiliary Controls: Remind Me & Message
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("remind me", fontSize = 11.sp, color = Color.White)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("message", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        // iOS Primary Action Controls: Decline (red) & Accept (green)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3B30))
                                        .clickable(onClick = onDecline),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("decline", fontSize = 12.sp, color = Color.White)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34C759))
                                        .clickable(onClick = onAnswer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("accept", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        else -> {
            // Google Pixel Material You Standard Incoming Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121214))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF2B2930),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PhoneCallback, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(14.dp))
                            Text(
                                "Incoming call • SIM $simSlot",
                                color = Color(0xFFD0BCFF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(LilacPrimary, LilacPrimaryDark)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = callerName.take(1).uppercase(),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = callerName,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = callerNumber,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCAC4D0)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2B2930),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(14.dp))
                                Text("Reply", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFBA1A1A))
                                        .clickable(onClick = onDecline),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCAC4D0))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                        .clickable(onClick = onAnswer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Answer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCAC4D0))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// AUTHENTIC 4-BRAND ACTIVE IN-CALL SCREENS
// ─────────────────────────────────────────────────────────────

@Composable
fun FakeActiveCallScreen(
    callerName: String,
    callerNumber: String,
    simSlot: Int,
    brand: String = "pixel",
    fakeCallHelper: FakeCallHelper?,
    onEndCall: (Long) -> Unit
) {
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isHold by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isKeypadOpen by remember { mutableStateOf(false) }
    var dialedDigits by remember { mutableStateOf("") }
    var isNearEar by remember { mutableStateOf(false) }

    LaunchedEffect(isHold) {
        while (!isHold) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
    }

    DisposableEffect(Unit) {
        fakeCallHelper?.startProximityTracking { isNear -> isNearEar = isNear }
        onDispose { fakeCallHelper?.stopProximityTracking() }
    }

    BackHandler {
        onEndCall(elapsedSeconds)
    }

    // Proximity Ear Blackout
    if (isNearEar) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    } else {
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        val formattedDuration = String.format("%02d:%02d", mins, secs)

        // Dynamic Brand Theme Styling
        val bgModifier = when (brand) {
            "samsung" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF132039), Color(0xFF0C1424))))
            "hyperos" -> Modifier.background(Color(0xFF17181A))
            "ios" -> Modifier.background(Color.Black)
            else -> Modifier.background(Color(0xFF121214))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header (Brand specific)
                when (brand) {
                    "samsung" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = callerName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "Jio 5G (SIM $simSlot) • $callerNumber", fontSize = 13.sp, color = Color(0xFF93C5FD))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isHold) "Call on hold" else formattedDuration,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isHold) Color(0xFFFBBF24) else Color(0xFF38BDF8)
                            )
                        }
                    }
                    "hyperos" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = callerName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "Mobile | India • SIM $simSlot", fontSize = 12.sp, color = Color(0xFFA1A1AA))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isHold) "Call on hold" else formattedDuration,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHold) Color(0xFFFBBF24) else Color(0xFF22C55E)
                            )
                        }
                    }
                    "ios" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 20.dp)
                        ) {
                            Text(text = callerName, fontSize = 28.sp, fontWeight = FontWeight.Light, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isHold) "call on hold" else formattedDuration,
                                fontSize = 14.sp,
                                color = if (isHold) Color(0xFFFBBF24) else Color(0xFF8E8E93)
                            )
                        }
                    }
                    else -> {
                        // Pixel
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2B2930),
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Text(
                                    text = "SIM $simSlot • HD",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0BCFF),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(LilacPrimary, LilacPrimaryDark))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = callerName.take(1).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = callerName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = callerNumber, fontSize = 13.sp, color = Color(0xFFCAC4D0))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isHold) "Call on hold" else formattedDuration,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isHold) Color(0xFFFBBF24) else Color(0xFFC4C7C5)
                            )
                        }
                    }
                }

                // Center Area: Dialpad OR Brand Action Grid
                if (isKeypadOpen) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = dialedDigits.ifBlank { " " },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().height(28.dp)
                        )

                        val keypadButtons = listOf(
                            listOf('1', '2', '3'),
                            listOf('4', '5', '6'),
                            listOf('7', '8', '9'),
                            listOf('*', '0', '#')
                        )

                        keypadButtons.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { char ->
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(if (brand == "ios") Color(0x33FFFFFF) else Color(0xFF2B2930))
                                            .clickable {
                                                dialedDigits += char
                                                fakeCallHelper?.playDtmfTone(char)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char.toString(),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        TextButton(onClick = { isKeypadOpen = false }) {
                            Text("Hide Keypad", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Brand Controls
                    val buttonBg = when (brand) {
                        "ios" -> Color(0x26FFFFFF)
                        "samsung" -> Color(0x33FFFFFF)
                        else -> Color(0xFF2B2930)
                    }
                    val buttonShape = when (brand) {
                        "pixel" -> RoundedCornerShape(18.dp)
                        else -> CircleShape
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        // Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Mute
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(buttonShape)
                                        .background(if (isMuted) Color.White else buttonBg)
                                        .clickable { isMuted = !isMuted },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Mute",
                                        tint = if (isMuted) Color.Black else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (brand == "ios") "mute" else if (isMuted) "Unmute" else "Mute",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCAC4D0)
                                )
                            }

                            // Keypad
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(buttonShape)
                                        .background(buttonBg)
                                        .clickable { isKeypadOpen = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Dialpad, contentDescription = "Keypad", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (brand == "ios") "keypad" else "Keypad",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCAC4D0)
                                )
                            }

                            // Audio / Speaker
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(buttonShape)
                                        .background(if (isSpeakerOn) Color.White else buttonBg)
                                        .clickable { isSpeakerOn = !isSpeakerOn },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Speaker",
                                        tint = if (isSpeakerOn) Color.Black else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (brand == "ios") "audio" else "Speaker",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCAC4D0)
                                )
                            }
                        }

                        // Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Add call
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(buttonShape)
                                        .background(buttonBg)
                                        .clickable { },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add call", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (brand == "ios") "add call" else "Add call",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCAC4D0)
                                )
                            }

                            // Hold
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(buttonShape)
                                        .background(if (isHold) Color(0xFFFBBF24) else buttonBg)
                                        .clickable { isHold = !isHold },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (brand == "ios") Icons.Default.Videocam else Icons.Default.Pause,
                                        contentDescription = "Hold",
                                        tint = if (isHold) Color.Black else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (brand == "ios") "FaceTime" else if (isHold) "Unhold" else "Hold",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCAC4D0)
                                )
                            }

                            // Record / Contacts
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(buttonShape)
                                        .background(if (isRecording) Color(0xFFF43F5E) else buttonBg)
                                        .clickable { isRecording = !isRecording },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (brand == "ios") Icons.Default.Contacts else Icons.Default.FiberManualRecord,
                                        contentDescription = "Record",
                                        tint = if (isRecording) Color.White else if (brand == "ios") Color.White else Color(0xFFF43F5E),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (brand == "ios") "contacts" else if (isRecording) "Recording" else "Record",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCAC4D0)
                                )
                            }
                        }
                    }
                }

                // Bottom End Call (Brand Authentic)
                val endCallColor = when (brand) {
                    "ios" -> Color(0xFFFF3B30)
                    "samsung" -> Color(0xFFE11D48)
                    else -> Color(0xFFBA1A1A)
                }

                Box(
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                        .then(
                            if (brand == "samsung") Modifier.size(width = 76.dp, height = 54.dp).clip(RoundedCornerShape(27.dp))
                            else Modifier.size(68.dp).clip(CircleShape)
                        )
                        .background(endCallColor)
                        .clickable { onEndCall(elapsedSeconds) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
