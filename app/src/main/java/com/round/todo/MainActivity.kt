package com.round.todo

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

// ... (数据模型保持不变) ...
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

// ... (API 接口保持不变) ...
interface MicrosoftTodoApi {
    @GET("v1.0/me/todo/lists") suspend fun getLists(@Header("Authorization") token: String): ListsResponse
    @GET("v1.0/me/todo/lists/{listId}/tasks") suspend fun getTasksInList(@Header("Authorization") token: String, @Path("listId") listId: String): TaskResponse
    @POST("v1.0/me/todo/lists/{listId}/tasks") suspend fun createTask(@Header("Authorization") token: String, @Path("listId") listId: String, @Body task: CreateTaskPayload): TodoTask
    @PATCH("v1.0/me/todo/lists/{listId}/tasks/{taskId}") suspend fun updateTask(@Header("Authorization") token: String, @Path("listId") listId: String, @Path("taskId") taskId: String, @Body body: UpdateTaskPayload): TodoTask
    @DELETE("v1.0/me/todo/lists/{listId}/tasks/{taskId}") suspend fun deleteTask(@Header("Authorization") token: String, @Path("listId") listId: String, @Path("taskId") taskId: String)
}

class MainActivity : ComponentActivity() {
    private var msalApp: ISingleAccountPublicClientApplication? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PublicClientApplication.createSingleAccountPublicClientApplication(this, R.raw.auth_config, object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
            override fun onCreated(app: ISingleAccountPublicClientApplication) { msalApp = app; setContent { RoundToDoApp(msalApp) } }
            override fun onError(e: MsalException) { Log.e("Auth", "Error: ${e.message}") }
        })
    }
}

object CacheManager {
    fun saveFolders(context: Context, list: List<TodoList>) = context.getSharedPreferences("todo_cache", Context.MODE_PRIVATE).edit().putString("folders", Gson().toJson(list)).apply()
    fun getFolders(context: Context): List<TodoList> = Gson().fromJson(context.getSharedPreferences("todo_cache", Context.MODE_PRIVATE).getString("folders", "[]"), object : TypeToken<List<TodoList>>() {}.type)
    fun saveTasks(context: Context, listId: String, list: List<TodoTask>) = context.getSharedPreferences("todo_cache", Context.MODE_PRIVATE).edit().putString("tasks_$listId", Gson().toJson(list)).apply()
    fun getTasks(context: Context, listId: String): List<TodoTask> = Gson().fromJson(context.getSharedPreferences("todo_cache", Context.MODE_PRIVATE).getString("tasks_$listId", "[]"), object : TypeToken<List<TodoTask>>() {}.type)
}

@Composable
fun RoundToDoApp(msalApp: ISingleAccountPublicClientApplication?) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    val retrofit = remember { Retrofit.Builder().baseUrl("https://graph.microsoft.com/").addConverterFactory(GsonConverterFactory.create()).build().create(MicrosoftTodoApi::class.java) }

    fun signIn() {
        msalApp?.signIn(context as Activity, "", arrayOf("Tasks.Read", "Tasks.ReadWrite"), object : AuthenticationCallback {
            override fun onSuccess(r: IAuthenticationResult) { token = r.accessToken; isLoggedIn = true }
            override fun onError(e: MsalException) { Toast.makeText(context, "Login Error", Toast.LENGTH_SHORT).show() }
            override fun onCancel() {}
        })
    }

    LaunchedEffect(Unit) {
        msalApp?.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) {
                if (activeAccount != null) msalApp.acquireTokenSilentAsync(arrayOf("Tasks.Read", "Tasks.ReadWrite"), activeAccount.authority, object : SilentAuthenticationCallback {
                    override fun onSuccess(r: IAuthenticationResult) { token = r.accessToken; isLoggedIn = true }
                    override fun onError(e: MsalException) {}
                })
            }
            override fun onAccountChanged(p: IAccount?, c: IAccount?) {}
            override fun onError(e: MsalException) {}
        })
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        if (!isLoggedIn) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Button(onClick = { signIn() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4767E8))) { Text("登录 ToDo") }
            }
        } else {
            NavHost(navController = navController, startDestination = "folders") {
                composable("folders") { FolderListScreen(retrofit, token, navController) }
                composable("tasks/{listId}/{listName}") {
                    TaskListScreen(retrofit, token, it.arguments?.getString("listId") ?: "", it.arguments?.getString("listName") ?: "", navController)
                }
            }
        }
    }
}

@Composable
fun FolderListScreen(api: MicrosoftTodoApi, token: String, navController: NavController) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    var folders by remember { mutableStateOf(CacheManager.getFolders(context)) }

    LaunchedEffect(Unit) {
        try {
            val response = withContext(Dispatchers.IO) { api.getLists("Bearer $token") }
            folders = response.value
            CacheManager.saveFolders(context, folders)
            focusRequester.requestFocus() // 请求焦点以便接收旋钮事件
        } catch (e: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent {
                coroutineScope.launch {
                    // ★★★ 核心修改：降低滚动速度 ★★★
                    // 这里的 0.4f 就是阻尼系数，越小越慢
                    listState.scrollBy(it.verticalScrollPixels * 0.4f)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("我的清单", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 15.dp, bottom = 5.dp))
        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(folders) { folder ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF333333)).clickable { navController.navigate("tasks/${folder.id}/${folder.displayName}") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = Color(0xFF5C7CFA), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(folder.displayName, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(api: MicrosoftTodoApi, token: String, listId: String, listName: String, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    var tasks by remember { mutableStateOf(CacheManager.getTasks(context, listId)) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refreshTasks() {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) { api.getTasksInList("Bearer $token", listId) }
                tasks = response.value
                CacheManager.saveTasks(context, listId, tasks)
                focusRequester.requestFocus()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent {
                scope.launch {
                    // ★★★ 降低滚动速度 ★★★
                    listState.scrollBy(it.verticalScrollPixels * 0.4f)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 20 && navController.previousBackStackEntry != null) navController.popBackStack()
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            Text(listName, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 15.dp))
            Text("长按删除 | 右滑返回", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(bottom = 5.dp))

            LazyColumn(state = listState, contentPadding = PaddingValues(top = 5.dp, bottom = 80.dp, start = 15.dp, end = 15.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF4767E8)).clickable { showAddDialog = true }.padding(10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text(" 添加任务", color = Color.White, fontSize = 13.sp)
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF252525)).combinedClickable(onClick = {}, onLongClick = { deleteTask(task) }).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (task.status == "completed") Icons.Default.Check else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (task.status == "completed") Color.Gray else Color(0xFF5C7CFA), modifier = Modifier.size(22.dp).clickable { toggleTask(task) })
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = task.title, color = if (task.status == "completed") Color.Gray else Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (task.status == "completed") TextDecoration.LineThrough else null)
                            if (task.dueDateTime != null) Text("截止: ${task.dueDateTime.dateTime.take(10)}", color = Color(0xFFFFA500), fontSize = 9.sp)
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

// ★★★ 紧凑型弹窗设计 (适配小圆屏) ★★★
@Composable
fun CompactAddTaskDialog(context: Context, onDismiss: () -> Unit, onConfirm: (String, String?, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var repeatType by remember { mutableStateOf("none") }

    val calendar = Calendar.getInstance()
    val datePicker = DatePickerDialog(context, { _, y, m, d -> selectedDate = "$y-${(m + 1).toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}" }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

    // 使用全屏 Box 遮罩，而不是标准的 AlertDialog
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)) // 半透明黑背景
            .clickable(enabled = false) {}, // 拦截点击
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f) // 宽度占屏幕 85%
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF252525))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("新任务", fontSize = 14.sp, color = Color.LightGray, modifier = Modifier.padding(bottom = 8.dp))

            // 输入框
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("输入内容...", fontSize = 12.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF5C7CFA), unfocusedBorderColor = Color.Gray)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 选项按钮行 (变得非常紧凑)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                // 日期按钮
                SmallOptionButton(
                    icon = Icons.Default.CalendarToday,
                    text = selectedDate ?: "日期",
                    isActive = selectedDate != null,
                    onClick = { datePicker.show() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 重复按钮
                SmallOptionButton(
                    icon = Icons.Default.Repeat,
                    text = when(repeatType){"daily"->"每天";"weekly"->"每周";else->"重复"},
                    isActive = repeatType != "none",
                    onClick = { repeatType = when(repeatType) { "none" -> "daily"; "daily" -> "weekly"; else -> "none" } }
                )
            }

            Spacer(modifier = Modifier.height(15.dp))

            // 确认/取消行
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.weight(1f).height(35.dp), contentPadding = PaddingValues(0.dp)) { Text("取消", fontSize = 12.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Button(onClick = { if (title.isNotBlank()) onConfirm(title, selectedDate, repeatType) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4767E8)), modifier = Modifier.weight(1f).height(35.dp), contentPadding = PaddingValues(0.dp)) { Text("添加", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun SmallOptionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isActive: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = if(isActive) Color(0xFF3E5FBD) else Color.DarkGray),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(30.dp).defaultMinSize(minWidth = 1.dp) // 允许非常小
    ) {
        Icon(icon, null, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, maxLines = 1)
    }
}