package com.saulocastrodev.youtubemusicconnect

import com.saulocastrodev.youtubemusicconnect.BuildConfig

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.net.InetAddress
import android.net.ConnectivityManager
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.color.utilities.Variant
import io.socket.client.Socket
import io.socket.client.IO
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log

const val API_BASE_URL = "https://youtubeconnect.app.br/api/"
const val COMPANION_PORT = 9863
const val PREF_KEY_COMPANION_IP = "companion_ip"

interface ApiService {
    @POST("register")
    suspend fun registerUser(@Body request: RegisterRequest): ApiResponse

    @POST("login")
    suspend fun loginUser(@Body request: LoginRequest): ApiResponse

    @POST("login/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): ApiResponse
}

data class RegisterRequest(val email: String, val password: String, val username: String, val image: String)
data class LoginRequest(val email: String, val password: String)
data class ApiResponse(
    val success: Boolean,
    val token: String?,
    val error: String?,
    val google_id_token: String?,
    val user: UserInfo? // novo campo
)

data class MusicMetadata(
    val title: String,
    val artist: String,
    val albumArt: String?
)

data class Thumbnails(
    val url: String,
    var width: Number,
    val height: Number
)

data class  QueueItens(
    val thumbnails: Array<Thumbnails>,
    val title: String,
    val author: String,
    val duration: Number,
    val selected: Boolean,
    val videoId: String,
    val conterparts: Array<QueueItens>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueueItens

        if (selected != other.selected) return false
        if (!thumbnails.contentEquals(other.thumbnails)) return false
        if (title != other.title) return false
        if (author != other.author) return false
        if (duration != other.duration) return false
        if (videoId != other.videoId) return false
        if (!conterparts.contentEquals(other.conterparts)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = selected.hashCode()
        result = 31 * result + thumbnails.contentHashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + videoId.hashCode()
        result = 31 * result + (conterparts?.contentHashCode() ?: 0)
        return result
    }
}

data class Queue(
    var autoPlay: Boolean,
    val items: Array<QueueItens>,
    val autoMixItems: Array<QueueItens>,
    val isGenerating: Boolean,
    val isInfinite: Boolean,
    val repeatMode: Number,
    val selectedItemIndex: Number,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Queue

        if (autoPlay != other.autoPlay) return false
        if (isGenerating != other.isGenerating) return false
        if (isInfinite != other.isInfinite) return false
        if (!items.contentEquals(other.items)) return false
        if (!autoMixItems.contentEquals(other.autoMixItems)) return false
        if (repeatMode != other.repeatMode) return false
        if (selectedItemIndex != other.selectedItemIndex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = autoPlay.hashCode()
        result = 31 * result + isGenerating.hashCode()
        result = 31 * result + isInfinite.hashCode()
        result = 31 * result + items.contentHashCode()
        result = 31 * result + autoMixItems.contentHashCode()
        result = 31 * result + repeatMode.hashCode()
        result = 31 * result + selectedItemIndex.hashCode()
        return result
    }
}

data class Video(
    val author: String?,
    val channelId: String?,
    val title: String?,
    val album: String?,
    val albumId: String?,
    val likeStatus: Number?,
    val thumbnails: Array<Thumbnails>?,
    val durationSeconds: Number?,
    val id: String?,
    val isLive: Boolean?,
    val videoType: Number?,
    val metadataFilled: Boolean?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Video

        if (isLive != other.isLive) return false
        if (metadataFilled != other.metadataFilled) return false
        if (author != other.author) return false
        if (channelId != other.channelId) return false
        if (title != other.title) return false
        if (album != other.album) return false
        if (albumId != other.albumId) return false
        if (likeStatus != other.likeStatus) return false
        if (!thumbnails.contentEquals(other.thumbnails)) return false
        if (durationSeconds != other.durationSeconds) return false
        if (id != other.id) return false
        if (videoType != other.videoType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isLive?.hashCode() ?: 0
        result = 31 * result + (metadataFilled?.hashCode() ?: 0)
        result = 31 * result + author.hashCode()
        result = 31 * result + channelId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (albumId?.hashCode() ?: 0)
        result = 31 * result + (likeStatus?.hashCode() ?: 0)
        result = 31 * result + thumbnails.contentHashCode()
        result = 31 * result + durationSeconds.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + (videoType?.hashCode() ?: 0)
        return result
    }
}

data class Player(
    val trackState: Int?,
    var videoProgress: Number?,
    val volume: Number?,
    val adPlaying: Boolean?,
    val queue: Queue?,
    val playlistId: String?
)

data class MediaState (
    val player: Player,
    val video: Video?,
    val playlistId: String?
)

data class GoogleLoginRequest(val googleToken: String)

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Main : Screen("main")
}

class MainActivity : ComponentActivity() {
    private val api = Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    val youtubeKey = BuildConfig.YOUTUBE_API_KEY
    val googleClientKey = BuildConfig.GOOGLE_CLIENT_KEY

    private var companionIP: String? = null
    private var webSocket: WebSocket? = null
    private var metadata by mutableStateOf(MediaState(Player(null,null,null,null,null,null), Video(null, null,null,null,null,null,null,null,null,null,null,null), null))
    private var isScanning by mutableStateOf(true)
    private var websocketConnected: Boolean = false

    private lateinit var userPrefs: UserPreferences
    private var loggedInUser by mutableStateOf<UserInfo?>(null)
    private var isShuffleOn = false
    private var isCompanionServerActive = false

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userPrefs = UserPreferences(applicationContext)
        companionIP = userPrefs.getString(PREF_KEY_COMPANION_IP)
        val savedToken = userPrefs.getString("token")
        val isLoggedIn = !savedToken.isNullOrEmpty()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_WIFI_STATE),
                1001
            )
        } else if (companionIP == null) {
            //lifecycleScope.launch(Dispatchers.IO) { scanNetworkForCompanion() }
        } else {
            isScanning = false
            //startWebSocket()
        }

        lifecycleScope.launch {
            val user = userPrefs.getUser()
            if (user != null) {
                loggedInUser = user
            }
        }

        setContent {
            val navController = rememberNavController()
            NavHost(navController, startDestination = if (isLoggedIn) Screen.Main.route else Screen.Login.route) {
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(Screen.Main.route)
                        },
                        onNavigateToRegister = {
                            navController.navigate(Screen.Register.route)
                        }
                    )
                }
                composable(Screen.Register.route) {
                    RegisterScreen(onRegisterSuccess = {
                        navController.navigate(Screen.Login.route)
                    })
                }
                composable(Screen.Main.route) {
                    var companionIP = userPrefs.getString(PREF_KEY_COMPANION_IP)
                    if (!companionIP.isNullOrBlank()) {
                        Log.i("Companion", "IP recuperado do storage: $companionIP")
                        startWebSocket()
                    } else {
                        scanNetworkForCompanion()
                    }

                    MainScreen(
                        user = userPrefs.getUser(),
                        onLogout = {
                            loggedInUser = null
                            lifecycleScope.launch { userPrefs.clear() }
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Main.route) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun LinkText(
        prefix: String,
        linkText: String,
        url: String
    ) {
        val uriHandler = LocalUriHandler.current


        val duracao = formatDuration(metadata.player.videoProgress);
        //Log.i("Duração", "$duracao")
        //Log.i("Duração", metadata.player.videoProgress.toString())

        val annotatedText = buildAnnotatedString {
            append(prefix)
            pushStringAnnotation(tag = "URL", annotation = url + "&t=${duracao}")
            withStyle(
                style = SpanStyle(
                    color = Color.Cyan,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(linkText)
            }
            pop()
        }

        ClickableText(
            text = annotatedText,
            onClick = { offset ->
                annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        uriHandler.openUri(annotation.item)
                    }
            },
            style = TextStyle(
                fontSize = 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }


    fun getNowPlayingFromMediaSession(context: Context): Pair<String, String>? {
        val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = mediaSessionManager.getActiveSessions(ComponentName(context, NotificationListener::class.java))

        controllers.forEach { controller ->
            val metadata = controller.metadata
            if (metadata != null) {
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                if (title.isNotBlank() && artist.isNotBlank()) {
                    return title to artist
                }
            }
        }
        return null
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, YTMusicNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())
    }


    fun searchYouTubeVideo(
        title: String,
        artist: String,
        apiKey: String,
        onResult: (videoId: String?, channelId: String?, playlistId: String?) -> Unit
    ) {
        val query = "$title $artist"
        val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=${query}&key=$youtubeKey&type=video&maxResults=1"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val searchRequest = Request.Builder().url(searchUrl).build()
                val searchResponse = client.newCall(searchRequest).execute()
                val body = searchResponse.body?.string()
                val json = JSONObject(body ?: "")
                val items = json.getJSONArray("items")

                if (items.length() > 0) {
                    val item = items.getJSONObject(0)
                    val videoId = item.getJSONObject("id").getString("videoId")
                    val channelId = item.getJSONObject("snippet").getString("channelId")

                    // Segunda tentativa: buscar uma playlist relacionada a esse vídeo
                    val playlistSearchUrl =
                        "https://www.googleapis.com/youtube/v3/search?part=snippet&q=$title&type=playlist&maxResults=1&key=$youtubeKey"
                    val playlistRequest = Request.Builder().url(playlistSearchUrl).build()
                    val playlistResponse = client.newCall(playlistRequest).execute()
                    val playlistBody = playlistResponse.body?.string()
                    val playlistJson = JSONObject(playlistBody ?: "")
                    Log.i("Playlist JSON", playlistJson.toString())
                    Log.i("Video ID", videoId)
                    val playlistItems = playlistJson.getJSONArray("items")

                    val playlistId: String? = if (playlistItems.length() > 0) {
                        playlistItems.getJSONObject(0).getJSONObject("id").getString("playlistId")
                    } else null

                    withContext(Dispatchers.Main) {
                        onResult(videoId, channelId, playlistId)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(null, null, null)
                    }
                }
            } catch (e: Exception) {
                Log.e("YTSearch", "Erro ao consultar YouTube API", e)
                withContext(Dispatchers.Main) {
                    onResult(null, null, null)
                }
            }
        }
    }

    private suspend fun isActiveCompanionServer(): Boolean = withContext(Dispatchers.IO) {
        val ip = userPrefs.getString(PREF_KEY_COMPANION_IP)
        val url = "http://$ip:$COMPANION_PORT/metadata"

        val client = OkHttpClient()
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.i("Network Scan", "Companion encontrado em $ip")
                startWebSocket()
                response.close()
                return@withContext true
            }

            response.close()
        } catch (e: Exception) {
            Log.w("Network Scan", "Falha ao tentar $url", e)
        }
        return@withContext false
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun scanNetworkForCompanion() {
        isScanning = true
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            Log.e("Network Scan", "ConnectivityManager não disponível")
            return
        }

        val linkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)

        val gateway = linkProperties?.routes
            ?.firstOrNull { it.hasGateway() }
            ?.gateway?.hostAddress

        if (gateway == null) {
            Log.e("Network Scan", "Gateway não encontrado")
            return
        }

        val ipBase = gateway.substringBeforeLast(".")

        val client = OkHttpClient()
        for (i in 1..254) {
            val ip = "$ipBase.$i"
            val url = "http://$ip:$COMPANION_PORT/metadata"

            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    companionIP = ip
                    userPrefs.saveString(PREF_KEY_COMPANION_IP, ip)
                    Log.i("Network Scan", "Companion encontrado em $companionIP")
                    startWebSocket()
                    response.close()
                    break
                }

                response.close()
            } catch (e: Exception) {
                Log.w("Network Scan", "Falha ao tentar $url", e)
            }
        }

        if (companionIP == null) {
            Log.w("Network Scan", "Nenhum companion encontrado na rede")
        }

        isScanning = false
    }

    fun formatDuration(seconds: Number?): String {
        if (seconds is Number) {
            val totalSeconds = seconds.toInt()
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            return String.format("%dm%02ds", minutes, remainingSeconds)
        }
        return "0m0s";
    }

    private fun startWebSocket() {
        val user = userPrefs.getUser()
        val options = IO.Options()
        options.transports = arrayOf("websocket")
        options.auth = mapOf(
            "token" to user?.tokenYtmd
        )

        val socket = IO.socket("ws://$companionIP:$COMPANION_PORT/api/v1/realtime", options)

        socket.on(Socket.EVENT_CONNECT) {
            Log.i("Socket.IO", "✅ Conectado com sucesso!")
            websocketConnected = true
        }

        socket.on("state-update") { args ->
            val json = args[0].toString() // transforma o JSONObject em String pura
            val mediaState = Gson().fromJson(json, MediaState::class.java)
            metadata = mediaState
            //Log.i("Socket.IO", "🎵 Atualização de estado: $metadata")
        }

        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e("Socket.IO", "❌ Erro na conexão: ${args[0]}")
            websocketConnected = false
        }

        socket.connect()
    }

    @Composable
    fun LoginScreen(
                    onLoginSuccess: (UserInfo) -> Unit,
                    onNavigateToRegister: () -> Unit) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        val context = LocalContext.current
        val activity = context as ComponentActivity

        val googleSignInClient = remember {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(googleClientKey) // substitua aqui
                .build()

            GoogleSignIn.getClient(context, gso)
        }

        val googleSignInLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                val token = account.idToken
                val name = account.displayName ?: ""
                val email = account.email ?: ""
                val photoUrl = account.photoUrl?.toString() ?: ""

                lifecycleScope.launch {
                    try {
                        Log.i("GoogleLogin", "Token recebido: $token")
                        val response = api.googleLogin(GoogleLoginRequest(token!!))
                        if (response.success && response.user != null && response.token != null) {
                            Log.i("GoogleLogin", "Info: ${response.user}")
                            userPrefs.saveUser(response.user, response.token)
                            onLoginSuccess(userPrefs.getUser()!!)
                        } else error = response.error
                    } catch (e: Exception) {
                        Log.e("GoogleLogin", "Exceção: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Falhou: ${e.message}")
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Login", style = MaterialTheme.typography.headlineMedium, color = colorResource(id = R.color.white))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = colorResource(id = R.color.white)) },
                placeholder = { Text("Digite seu email", color = colorResource(id = R.color.white)) },
                textStyle = TextStyle(color = colorResource(id = R.color.white)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorResource(id = R.color.red),
                    unfocusedBorderColor = colorResource(id = R.color.red),
                    cursorColor = colorResource(id = R.color.white)
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha", color = colorResource(id = R.color.white)) },
                placeholder = { Text("Digite sua senha", color = colorResource(id = R.color.white)) },
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TextStyle(color = colorResource(id = R.color.white)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorResource(id = R.color.red),
                    unfocusedBorderColor = colorResource(id = R.color.red),
                    cursorColor = colorResource(id = R.color.white)
                )
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                lifecycleScope.launch {
                    try {
                        val response = api.loginUser(LoginRequest(email, password))
                        Log.i("Info", response.toString());
                        if (response.success && response.user != null && response.token != null) {
                            userPrefs.saveUser(response.user, response.token)
                            onLoginSuccess(userPrefs.getUser()!!)
                        } else error = response.error
                    } catch (e: Exception) {
                        error = "Erro de conexão"
                    }
                }
            }, colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.red),
                contentColor = colorResource(id = R.color.white)
            )
            ) { Text("Entrar") }

            Button(
                onClick = {
                    // Aqui você vai chamar a função de login com Google
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.red),
                    contentColor = colorResource(id = R.color.white)
                ),
                modifier = Modifier
                    .padding(vertical = 8.dp)
            ) {
                Text("Entrar com Google")
            }

            TextButton(onClick = onNavigateToRegister, colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.red),
                contentColor = colorResource(id = R.color.white)
            )) { Text("Criar conta") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    @Composable
    fun RegisterScreen(onRegisterSuccess: () -> Unit) {
        val context = LocalContext.current
        var email by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = context.contentResolver.openInputStream(it)
                imageBitmap = BitmapFactory.decodeStream(inputStream)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Cadastro", color = colorResource(id = R.color.white), style = MaterialTheme.typography.headlineMedium)

            imageBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(100.dp))
            } ?: Image(
                painter = painterResource(id = R.drawable.person),
                contentDescription = null,
                modifier = Modifier.size(100.dp)
            )

            TextButton(onClick = {
                imageLauncher.launch("image/*")
            }) {
                Text("Selecionar imagem", color = colorResource(id = R.color.white))
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuário", color = colorResource(id = R.color.white)) },
                placeholder = { Text("Digite seu nome", color = colorResource(id = R.color.white)) },
                textStyle = TextStyle(color = colorResource(id = R.color.white)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorResource(id = R.color.red),
                    unfocusedBorderColor = colorResource(id = R.color.red),
                    cursorColor = colorResource(id = R.color.white)
                )
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = colorResource(id = R.color.white)) },
                placeholder = { Text("Digite seu email", color = colorResource(id = R.color.white)) },
                textStyle = TextStyle(color = colorResource(id = R.color.white)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorResource(id = R.color.red),
                    unfocusedBorderColor = colorResource(id = R.color.red),
                    cursorColor = colorResource(id = R.color.white)
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha", color = colorResource(id = R.color.white)) },
                placeholder = { Text("Digite sua senha", color = colorResource(id = R.color.white)) },
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TextStyle(color = colorResource(id = R.color.white)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorResource(id = R.color.red),
                    unfocusedBorderColor = colorResource(id = R.color.red),
                    cursorColor = colorResource(id = R.color.white)
                )
            )

            Spacer(Modifier.height(16.dp))

            Button(onClick = {
                (context as? ComponentActivity)?.lifecycleScope?.launch {
                    try {
                        val imageBase64 = imageBitmap?.let { bitmap ->
                            val stream = java.io.ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                        } ?: ""

                        val response = api.registerUser(RegisterRequest(email, password, username, imageBase64))
                        if (response.success) onRegisterSuccess()
                        else error = response.error
                    } catch (e: Exception) {
                        error = "Erro de conexão"
                    }
                }
            }, colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.red),
                contentColor = colorResource(id = R.color.white)
            )) {
                Text("Registrar")
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    @Composable
    fun SyncButton(title: String, artist: String, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = "Sincronizar", tint = colorResource(id = R.color.white))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = title, color = colorResource(id = R.color.white))
                    Text(text = artist, color = colorResource(id = R.color.white), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(id = R.color.red),
                    contentColor = colorResource(id = R.color.white)
                )
            ) {
                Text("Sincronizar com Desktop")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Composable
    fun MainScreen(user: UserInfo?, onLogout: () -> Unit) {

        var isCompanionActive by remember { mutableStateOf<Boolean?>(null) }
        var capturedSongTitle by remember { mutableStateOf<String?>(null) }
        var capturedSongArtist by remember { mutableStateOf<String?>(null) }

        val context = LocalContext.current
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000) // verifica a cada segundo (ajuste conforme necessário)

                if (isNotificationListenerEnabled(context)) {
                    val info = getNowPlayingFromMediaSession(context)
                    if (info != null) {
                        capturedSongTitle = info.first
                        capturedSongArtist = info.second
                    } else {
                        capturedSongTitle = NotificationListener.lastSongTitle
                        capturedSongArtist = NotificationListener.lastSongArtist
                    }
                }
            }
        }


        LaunchedEffect(Unit) {
            isCompanionActive = isActiveCompanionServer()
            val info = getNowPlayingFromMediaSession(context)
            if (info != null) {
                val (title, artist) = info
                capturedSongTitle = title
                capturedSongArtist = artist
            } else {
                Log.i("Sync", "Nenhuma música ativa encontrada via MediaSession")
            }
        }

        if (isScanning) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Procurando Companion API na rede...")
            }
        } else isCompanionActive?.let {
            if (!it) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), // opcional, para espaçamento lateral
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Servidor do Companion Server do YouTube Music Desktop não encontrado! Clique abaixo se quiser procurá-lo:",
                        color = colorResource(id = R.color.white),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isScanning) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Procurando Companion API na rede...",
                            color = colorResource(id = R.color.white)
                        )
                    } else {
                        Button(
                            onClick = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    isScanning = true
                                    scanNetworkForCompanion()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(id = R.color.red),
                                contentColor = colorResource(id = R.color.white)
                            )
                        ) {
                            Text("Buscar Companion")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    user?.let {
                        val imageModifier = Modifier.size(80.dp)
                        if (!it.foto.isNullOrBlank()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = it.foto),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.person),
                                contentDescription = null,
                                modifier = imageModifier
                            )
                        }
                        Text(text = it.name, color = colorResource(id = R.color.white))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(id = R.color.red),
                                contentColor = colorResource(id = R.color.white)
                            )
                        ) {
                            Text("Sair")
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    LinkText(
                        prefix = "Now Playing: ",
                        linkText = metadata.video?.title ?: "N/A",
                        url = "https://music.youtube.com/watch?v=${metadata.video?.id}&list=${metadata.player.playlistId}"
                    )
                    LinkText(
                        prefix = "Artist: ",
                        linkText = metadata.video?.author ?: "N/A",
                        url = "https://music.youtube.com/channel/${metadata.video?.channelId}"
                    )

                    val imageModifier = Modifier.size(120.dp)

                    val imageUrl: String? = try {
                        if (metadata.video?.videoType?.toInt() == 1) {
                            metadata.video?.thumbnails?.get(0)?.url
                        } else {
                            metadata.video?.thumbnails?.get(3)?.url
                        }

                    } catch (e: Exception) {
                        Log.i("Carregando Imagem", "🎵Não foi possível obter a URL da imagem", e)
                        null
                    }

                    if (imageUrl != null) {
                        imageUrl?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = "Capa da música",
                                modifier = Modifier.size(200.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_music_placeholder),
                            contentDescription = "Placeholder",
                            modifier = imageModifier
                        )
                    }

                    var companionIP = userPrefs.getString(PREF_KEY_COMPANION_IP)
                    if (!companionIP.isNullOrBlank()) {
                        Row {
                            Button(
                                onClick = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            //Log.i("TrackAtate", metadata.player.trackState?.toInt().toString())
                                            if (metadata.player.trackState?.toInt() == 1) {
                                                sendControlCommand("playPause")
                                            } else {
                                                sendControlCommand("play")
                                            }

                                        } catch (e: Exception) {
                                            Log.e("CompanionAPI", "Erro ao enviar comando", e)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(id = R.color.red),
                                    contentColor = colorResource(id = R.color.white)
                                )
                            ) { Text("Play/Pause") }
                            Button(
                                onClick = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            sendControlCommand("next")
                                        } catch (e: Exception) {
                                            Log.e("CompanionAPI", "Erro ao enviar comando", e)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(id = R.color.red),
                                    contentColor = colorResource(id = R.color.white)
                                )
                            ) { Text("Next") }
                            Button(
                                onClick = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            sendControlCommand("previous")
                                        } catch (e: Exception) {
                                            Log.e("CompanionAPI", "Erro ao enviar comando", e)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(id = R.color.red),
                                    contentColor = colorResource(id = R.color.white)
                                )
                            ) { Text("Previous") }
                        }

                        Row(
                            modifier = Modifier.padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val repeatMode = metadata.player.queue?.repeatMode ?: 0

                            val likeStatus = metadata.video?.likeStatus?.toInt()
                            var isLiked by remember { mutableStateOf(likeStatus?.toInt() == 2) }
                            var isDisliked by remember { mutableStateOf(likeStatus?.toInt() == 0 || likeStatus?.toInt() == -1 || likeStatus?.toInt() == 1) }

                            // ⚡️ Atualiza os estados de like/dislike conforme o metadata recebido
                            LaunchedEffect(metadata.video?.likeStatus) {
                                Log.i("LIke Status", metadata.video?.likeStatus?.toInt().toString())
                                when (metadata.video?.likeStatus?.toInt()) {
                                    2 -> {
                                        isLiked = true
                                        isDisliked = false
                                    }

                                    0 -> {
                                        isLiked = false
                                        isDisliked = true
                                    }

                                    1, -1 -> {
                                        isLiked = false
                                        isDisliked = false
                                    }

                                    else -> {
                                        isLiked = false
                                        isDisliked = false
                                    }
                                }
                            }

                            // Mostra botão de "Like" se ainda não está liked
                            if (!isLiked) {
                                Button(
                                    onClick = {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                sendControlCommand("toggleLike")
                                                isLiked = true
                                                isDisliked = false
                                            } catch (e: Exception) {
                                                Log.e("CompanionAPI", "Erro ao enviar comando", e)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Green,
                                        contentColor = colorResource(id = R.color.white)
                                    )
                                ) {
                                    Text("Like")
                                }
                            }

                            // Mostra botão de "Dislike" se ainda não está disliked
                            if (!isDisliked) {
                                Button(
                                    onClick = {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                sendControlCommand("toggleDislike")
                                                isDisliked = true
                                                isLiked = false
                                            } catch (e: Exception) {
                                                Log.e("CompanionAPI", "Erro ao enviar comando", e)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Blue,
                                        contentColor = colorResource(id = R.color.white)
                                    )
                                ) {
                                    Text("Dislike")
                                }
                            }

                            Button(
                                onClick = {
                                    val newRepeat = when (repeatMode.toString()) {
                                        "0" -> "1"
                                        "1" -> "2"
                                        else -> "0"
                                    }

                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            sendControlCommand("repeatMode", data = newRepeat)
                                        } catch (e: Exception) {
                                            Log.e("CompanionAPI", "Erro ao enviar comando", e)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(id = R.color.red),
                                    contentColor = colorResource(id = R.color.white)
                                )
                            ) {
                                val label = when (repeatMode.toString()) {
                                    "0" -> "No Repeat"
                                    "1" -> "Repeat All"
                                    else -> "Repeat One"
                                }
                                Text(label)
                            }

                            Button(
                                onClick = {
                                    isShuffleOn = !isShuffleOn
                                    if (isShuffleOn) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                sendControlCommand("shuffle")
                                            } catch (e: Exception) {
                                                Log.e("CompanionAPI", "Erro ao enviar comando", e)
                                            }
                                        }
                                    }// ajuste conforme seu backend espera
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isShuffleOn) colorResource(id = R.color.red) else Color.Gray,
                                    contentColor = colorResource(id = R.color.white)
                                )
                            ) {
                                Text(if (isShuffleOn) "Shuffle On" else "Shuffle Off")
                            }
                        }
                    }
                    Row {
                        if (companionIP == null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isScanning) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Procurando Companion API na rede...",
                                        color = colorResource(id = R.color.white)
                                    )
                                } else {
                                    Button(
                                        onClick = {
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                isScanning = true
                                                scanNetworkForCompanion()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(id = R.color.red),
                                            contentColor = colorResource(id = R.color.white)
                                        )
                                    ) {
                                        Text("Buscar Companion")
                                    }
                                }
                            }
                        }

                        if (!websocketConnected) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isScanning) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Conectando ao Web Socket...",
                                        color = colorResource(id = R.color.white)
                                    )
                                } else {
                                    Button(
                                        onClick = {
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                isScanning = true
                                                startWebSocket()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(id = R.color.red),
                                            contentColor = colorResource(id = R.color.white)
                                        )
                                    ) {
                                        Text("Conectar ao Websocket")
                                    }
                                }
                            }
                        }
                    }

                    Row {
                        if (!isNotificationListenerEnabled(context)) {
                            Button(
                                onClick = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                        } catch (e: Exception) {
                                            Log.e(
                                                "Notification Listener",
                                                "Erro ao pedir permissão de notificação ao usuário",
                                                e
                                            )
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(id = R.color.red),
                                    contentColor = colorResource(id = R.color.white)
                                )
                            ) {
                                Text("Habilitar Notificação para capturar musica do youtube music!")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Exibe botão de sincronização caso a música tenha sido capturada
                    capturedSongTitle?.let { title ->
                        capturedSongArtist?.let { artist ->
                            SyncButton(title = title, artist = artist) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    searchYouTubeVideo(title, artist, youtubeKey) { videoId, channelId, playlistId ->
                                        if (videoId != null && channelId != null) {
                                            Log.i("Resultado", "Vídeo: $videoId - Canal: $channelId")

                                            var playlistPadraoId = "RDAMVM${videoId}";

                                            val json = JSONObject().apply {
                                                put("videoId", videoId)
                                                put("playlistId", playlistPadraoId)
                                            }

                                            // Aqui é onde corrigimos — lançamos uma coroutine dentro do callback
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                try {
                                                    sendControlCommand("changeVideo", json)
                                                } catch (e: Exception) {
                                                    Log.e("Sync", "Erro ao sincronizar com o Desktop", e)
                                                }
                                            }
                                        } else {
                                            Log.w("Resultado", "Não foi possível obter o vídeo")
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

    }

    private suspend fun sendControlCommand(command: String, data: Any? = null) {
        val user = userPrefs.getUser()
        val tokenYtmd = user?.tokenYtmd
        val ip = companionIP ?: return

        try {
            val url = "http://$ip:$COMPANION_PORT/api/v1/command"
            val client = OkHttpClient()

            val jsonBody = JSONObject().apply {
                put("command", command)
                when (data) {
                    is JSONObject -> put("data", data) // já é um JSON estruturado
                    is String, is Int, is Boolean, is Double -> put("data", data) // tipos primitivos
                    null -> {} // nada
                    else -> put("data", data.toString()) // fallback genérico
                }
            }

            Log.i("JSON Command", jsonBody.toString())

            val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "$tokenYtmd")
                .post(body)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            Log.i("Resposta API:", response.toString())
            response.close()

        } catch (e: Exception) {
            Log.e("CompanionAPI", "Erro ao enviar comando", e)
        }
    }

    private fun loadImage(url: String): Bitmap {
        val inputStream = java.net.URL(url).openStream()
        return BitmapFactory.decodeStream(inputStream)
    }
}
