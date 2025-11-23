package com.round.todo

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// 基础组件
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
// Wear OS 组件
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
// 工具库
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.Calendar

// ================= 数据模型 (保持不变) =================
data class TodoList(val id: String, val displayName: String)
data class ListsResponse(val value: List<TodoList>)
data class DateTimeTimeZone(val dateTime: String, val timeZone: String = "UTC")
data class TodoTask(val id: String, val title: String, val status: String, val dueDateTime: DateTimeTimeZone? = null)
data class TaskResponse(val value: List<TodoTask>)
data class CreateTaskPayload(val title: String, val dueDateTime: DateTimeTimeZone? = null, val isReminderOn: Boolean = false, val recurrence: PatternedRecurrence? = null)
data class UpdateTaskPayload(val status: String)
data class PatternedRecurrence(val pattern: RecurrencePattern, val range: RecurrenceRange)
data class RecurrencePattern(val type: String, val interval: Int = 1)
data class RecurrenceRange(val type: String = "noEnd", val startDate: String = "2024-01-01")

// ================= API 接口 (保持不变) =================
interface MicrosoftTodoApi {
    @GET("v1.0/me/todo/lists") suspend fun getLists(@Header("Authorization") token: String): ListsResponse
    @GET("v1.0/me/todo/lists/{listId}/tasks") suspend fun getTasksInList(@Header("Authorization") token: String, @Path("listId") listId: String): TaskResponse
    @POST("v1.0/me/todo/lists/{listId}/tasks") suspend fun createTask(@Header("Authorization") token: String, @Path("listId") listId: String, @Body task: CreateTaskPayload): TodoTask
    @PATCH("v1.0/me/todo/lists/{listId}/tasks/{taskId}") suspend fun updateTask(@Header("Authorization") token: String, @Path("listId") listId: String, @Path("taskId") taskId: String, @Body body: UpdateTaskPayload): TodoTask
    @DELETE("v1.0/me/todo/lists/{listId}/tasks/{taskId}") suspend fun deleteTask(@Header("Authorization") token: String, @Path("listId") listId: String, @Path("taskId") taskId: String)
}

// ================= 缓存管理 (保持不变) =================
object CacheManager {
    fun saveFolders(context: Context, list: List<TodoList>) = context.getSharedPreferences("todo_cache", Context.MODE_PRIVATE).edit().putString("folders", Gson().toJson(list)).apply()
    fun getFolders(context: Context): List<TodoList> = Gson().fromJson(context.getSharedPreferences("todo_cache", Context.MODE_PRIVATE).getString("folders", "[]"), object : TypeToken<List<TodoList>>() {}.type)
    fun saveTasks(context: Context, listId: String, list: List<TodoTask>) = context.getSharedPreferences("todo_cache", Context.MODE_PRIVATE).edit().putString("tasks_$listId", Gson().toJson(list)).apply()
    fun getTasks(context: Context, listId: String): List<TodoTask> = Gson().fromJson(context.getSharedPreferences("todo_cache", Context.MODE_PRIVATE).getString("tasks_$listId", "[]"), object : TypeToken<List<TodoTask>>() {}.type)
}

// ================= 主 Activity =================
class MainActivity : ComponentActivity() {
    private var msalApp: ISingleAccountPublicClientApplication? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PublicClientApplication.createSingleAccountPublicClientApplication(this, R.raw.auth_config, object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
            override fun onCreated(app: ISingleAccountPublicClientApplication) { msalApp = app; setContent { WearRoundToDoApp(msalApp) } }
            override fun onError(e: MsalException) { Log.e("Auth", "Error: ${e.message}") }
        })
    }
}

@Composable
fun WearRoundToDoApp(msalApp: ISingleAccountPublicClientApplication?) {
    val navController = rememberSwipeDismissableNavController()
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(false) }
    var isCheckingLogin by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf("") }
    val retrofit = remember { Retrofit.Builder().baseUrl("https://graph.microsoft.com/").addConverterFactory(GsonConverterFactory.create()).build().create(MicrosoftTodoApi::class.java) }

    fun signIn() {
        msalApp?.signIn(context as Activity, "", arrayOf("Tasks.Read", "Tasks.ReadWrite"), object : AuthenticationCallback {
            override fun onSuccess(r: IAuthenticationResult) { token = r.accessToken; isLoggedIn = true }
            override fun onError(e: MsalException) { Toast.makeText(context, "请在手机上确认", Toast.LENGTH_SHORT).show() }
            override fun onCancel() {}
        })
    }

    LaunchedEffect(Unit) {
        msalApp?.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) {
                if (activeAccount != null) {
                    msalApp.acquireTokenSilentAsync(arrayOf("Tasks.Read", "Tasks.ReadWrite"), activeAccount.authority, object : SilentAuthenticationCallback {
                        override fun onSuccess(r: IAuthenticationResult) { token = r.accessToken; isLoggedIn = true; isCheckingLogin = false }
                        override fun onError(e: MsalException) { isCheckingLogin = false }
                    })
                } else { isCheckingLogin = false }
            }
            override fun onAccountChanged(p: IAccount?, c: IAccount?) {}
            override fun onError(e: MsalException) { isCheckingLogin = false }
        })
    }

    androidx.wear.compose.material.MaterialTheme(colors = Colors(primary = Color(0xFF4767E8), surface = Color(0xFF202124), onSurface = Color.White)) {
        Scaffold(timeText = { TimeText() }) {
            if (isCheckingLogin) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(androidx.wear.compose.material.MaterialTheme.colors.background)) {
                    CircularProgressIndicator(indicatorColor = Color(0xFF4767E8))
                }
            } else if (!isLoggedIn) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(androidx.wear.compose.material.MaterialTheme.colors.background)) {
                    Chip(onClick = { signIn() }, label = { Text("登录微软 ToDo") }, icon = { Icon(Icons.Default.Login, null) }, colors = ChipDefaults.primaryChipColors())
                }
            } else {
                SwipeDismissableNavHost(navController = navController, startDestination = "folders") {
                    composable("folders") { FolderListScreen(retrofit, token, navController) }
                    composable("tasks/{listId}/{listName}") { TaskListScreen(retrofit, token, it.arguments?.getString("listId") ?: "", it.arguments?.getString("listName") ?: "", navController) }
                }
            }
        }
    }
}

// ★★★ 核心修改：原生吸附 + 机械震动封装 ★★★
@Composable
fun SnapScalingLazyColumn(
    modifier: Modifier = Modifier,
    content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    // 1. 请求焦点，确保能接收到滚轮事件
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // 2. 核心：监听“中心Item”的变化来实现震动
    // 只要中间那个任务变了，就震动一下。这比监听滚动距离更精准、更省电、更像机械齿轮。
    LaunchedEffect(listState.centerItemIndex) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.wear.compose.material.MaterialTheme.colors.background)
            .onRotaryScrollEvent {
                coroutineScope.launch {
                    // 3. 滚动速度控制：这里用 1.0f 保证跟手（如果觉得快可以改成 0.5f）
                    // 关键在于下面那个 flingBehavior 会自动帮你停在任务上
                    listState.scrollBy(it.verticalScrollPixels)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        state = listState,
        anchorType = ScalingLazyListAnchorType.ItemStart,
        // 4. 核心：开启原生吸附 (Snap)
        // 这会让列表在停止滚动时，自动回弹对齐到最近的一个任务，
        // 就像滚轮里有卡槽一样，非常舒服。
        flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState)
    ) {
        content()
    }
}

@Composable
fun FolderListScreen(api: MicrosoftTodoApi, token: String, navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf(CacheManager.getFolders(context)) }

    LaunchedEffect(Unit) {
        try {
            val response = withContext(Dispatchers.IO) { api.getLists("Bearer $token") }
            folders = response.value
            CacheManager.saveFolders(context, folders)
        } catch (e: Exception) {}
    }

    SnapScalingLazyColumn {
        item { ListHeader { Text("我的清单") } }
        items(folders) { folder ->
            Chip(
                onClick = { navController.navigate("tasks/${folder.id}/${folder.displayName}") },
                label = { Text(folder.displayName) },
                icon = { Icon(Icons.Default.Folder, null, tint = Color(0xFF5C7CFA)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(api: MicrosoftTodoApi, token: String, listId: String, listName: String, navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(CacheManager.getTasks(context, listId)) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refreshTasks() {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) { api.getTasksInList("Bearer $token", listId) }
                tasks = response.value
                CacheManager.saveTasks(context, listId, tasks)
            } catch (e: Exception) {}
        }
    }
    LaunchedEffect(Unit) { refreshTasks() }

    fun toggleTask(task: TodoTask) {
        val newStatus = if (task.status == "completed") "notStarted" else "completed"
        tasks = tasks.map { if (it.id == task.id) it.copy(status = newStatus) else it }
        scope.launch { try { api.updateTask("Bearer $token", listId, task.id, UpdateTaskPayload(newStatus)); refreshTasks() } catch (e: Exception) { refreshTasks() } }
    }

    fun deleteTask(task: TodoTask) {
        tasks = tasks.filter { it.id != task.id }
        scope.launch { try { api.deleteTask("Bearer $token", listId, task.id) } catch (e: Exception) { refreshTasks() } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnapScalingLazyColumn {
            item { ListHeader { Text(listName) } }
            item {
                CompactChip(
                    onClick = { showAddDialog = true },
                    label = { Text("添加任务") },
                    icon = { Icon(Icons.Default.Add, null) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(tasks, key = { it.id }) { task ->
                val isCompleted = task.status == "completed"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if(isCompleted) Color(0xFF1A1A1A) else Color(0xFF2D2D2D))
                        .combinedClickable(onClick = {}, onLongClick = { deleteTask(task) })
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isCompleted) Color.Gray else Color(0xFF5C7CFA),
                        modifier = Modifier.size(24.dp).clickable { toggleTask(task) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = task.title,
                            color = if (isCompleted) Color.Gray else Color.White,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                        )
                        if (task.dueDateTime != null) {
                            Text(text = "截止: ${task.dueDateTime.dateTime.take(10)}", color = Color(0xFFFFA500), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            CompactAddTaskDialog(context, { showAddDialog = false }) { title, date, repeat ->
                showAddDialog = false
                scope.launch {
                    val dt = if (date != null) DateTimeTimeZone("${date}T18:00:00") else null
                    val rec = if (repeat != "none") PatternedRecurrence(RecurrencePattern(repeat), RecurrenceRange()) else null
                    try { withContext(Dispatchers.IO) { api.createTask("Bearer $token", listId, CreateTaskPayload(title, dt, false, rec)) }; refreshTasks() } catch (e: Exception) {}
                }
            }
        }
    }
}

// 弹窗代码
@Composable
fun CompactAddTaskDialog(context: Context, onDismiss: () -> Unit, onConfirm: (String, String?, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var repeatType by remember { mutableStateOf("none") }
    val calendar = Calendar.getInstance()
    val datePicker = DatePickerDialog(context, { _, y, m, d -> selectedDate = "$y-${(m + 1).toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}" }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF252525)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("新任务", fontSize = 12.sp, color = Color.LightGray)
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("内容", fontSize = 12.sp, color = Color.Gray) }, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White), singleLine = true, modifier = Modifier.fillMaxWidth().height(50.dp), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, cursorColor = Color.White, focusedBorderColor = Color(0xFF5C7CFA), unfocusedBorderColor = Color.Gray))
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SmallOptionButton(Icons.Default.CalendarToday, selectedDate ?: "日期", selectedDate != null) { datePicker.show() }
                Spacer(modifier = Modifier.width(4.dp))
                SmallOptionButton(Icons.Default.Repeat, when(repeatType){"daily"->"每天";"weekly"->"每周";else->"重复"}, repeatType != "none") { repeatType = when(repeatType) { "none" -> "daily"; "daily" -> "weekly"; else -> "none" } }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CompactButton("取消", Color.DarkGray, onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                CompactButton("添加", Color(0xFF4767E8)) { if (title.isNotBlank()) onConfirm(title, selectedDate, repeatType) }
            }
        }
    }
}
@Composable
fun SmallOptionButton(icon: ImageVector, text: String, isActive: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(backgroundColor = if(isActive) Color(0xFF3E5FBD) else Color.DarkGray), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(28.dp).defaultMinSize(minWidth = 1.dp)) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = Color.White); Spacer(modifier = Modifier.width(2.dp)); Text(text, fontSize = 10.sp, maxLines = 1, color = Color.White)
    }
}
@Composable
fun RowScope.CompactButton(text: String, color: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(backgroundColor = color), modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(0.dp)) { Text(text, fontSize = 12.sp, color = Color.White) }
}