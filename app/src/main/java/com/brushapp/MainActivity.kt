package com.brushapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

private val Burgundy = Color(0xFF800020)
private val Background = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF252126)
private val TextMuted = Color(0xFF777078)
private val Success = Color(0xFF16803C)

private const val MESSAGE_CHANNEL_ID = "brushapp_messages"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createMessageNotificationChannel()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Burgundy,
                    onPrimary = Color.White,
                    background = Background,
                    surface = Background,
                    onBackground = TextDark,
                    onSurface = TextDark
                )
            ) {
                BrushAppRoot()
            }
        }
    }

    private fun createMessageNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Pesan BrushApp",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pemberitahuan saat ada pesan BrushApp baru"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

@Composable
private fun BrushAppRoot() {
    val auth = remember { FirebaseAuth.getInstance() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUser?.uid) {
        val signedInUser = currentUser
        if (signedInUser != null) {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    saveFcmToken(signedInUser.uid, token)
                }
        }
        if (signedInUser != null &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (currentUser == null) {
        AuthScreen(auth)
    } else {
        UserArea(
            user = currentUser!!,
            onSignOut = { auth.signOut() }
        )
    }
}

@Composable
private fun AuthScreen(auth: FirebaseAuth) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).imePadding().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("BrushApp", color = Burgundy, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Sederhana. Nyaman. Terhubung.", color = TextMuted, fontSize = 15.sp)
        Spacer(Modifier.height(38.dp))
        Text(
            text = if (isRegister) "Buat akun baru" else "Selamat datang",
            color = TextDark, fontSize = 23.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(20.dp))
        AuthField(email, { email = it; message = "" }, "Alamat email", KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(password, { password = it; message = "" }, "Kata sandi", KeyboardType.Password, true)
        if (isRegister) {
            Spacer(Modifier.height(12.dp))
            AuthField(confirmPassword, { confirmPassword = it; message = "" }, "Ulangi kata sandi", KeyboardType.Password, true)
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = Burgundy, fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                val cleanEmail = email.trim()
                when {
                    cleanEmail.isBlank() || password.isBlank() -> message = "Email dan kata sandi wajib diisi."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> message = "Format email belum benar."
                    password.length < 6 -> message = "Kata sandi minimal 6 karakter."
                    isRegister && password != confirmPassword -> message = "Ulangan kata sandi tidak sama."
                    else -> {
                        busy = true
                        val task = if (isRegister) auth.createUserWithEmailAndPassword(cleanEmail, password)
                                   else auth.signInWithEmailAndPassword(cleanEmail, password)
                        task.addOnCompleteListener { result ->
                            busy = false
                            if (!result.isSuccessful) message = friendlyAuthError(result.exception)
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Burgundy, contentColor = Color.White)
        ) {
            if (busy) CircularProgressIndicator(Modifier.height(21.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (isRegister) "Daftar" else "Masuk", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        TextButton(
            onClick = { isRegister = !isRegister; message = ""; confirmPassword = "" },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (isRegister) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar", color = Burgundy)
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation()
                               else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Burgundy, focusedLabelColor = Burgundy, cursorColor = Burgundy,
            unfocusedBorderColor = Color(0xFFD8D2D6), focusedTextColor = TextDark, unfocusedTextColor = TextDark
        )
    )
}

@Composable
private fun UserArea(user: FirebaseUser, onSignOut: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = user.uid
    val email = user.email.orEmpty()
    var displayName by remember(uid) { mutableStateOf("") }
    var loadingProfile by remember(uid) { mutableStateOf(true) }
    var profileError by remember(uid) { mutableStateOf("") }
    var savingProfile by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var directoryUsers by remember(uid) { mutableStateOf<List<DirectoryUser>>(emptyList()) }
    var directoryLoading by remember(uid) { mutableStateOf(true) }
    var directoryError by remember(uid) { mutableStateOf("") }
    var selectedPeer by remember { mutableStateOf<ChatPeer?>(null) }
    var openingPeerUid by remember { mutableStateOf("") }

    BackHandler(enabled = selectedPeer != null) { selectedPeer = null }

    DisposableEffect(db, uid) {
        directoryLoading = true
        val listener = db.collection("user_directory")
            .orderBy("displayName", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    directoryError = exception.localizedMessage ?: "Tidak dapat memuat daftar pengguna."
                    directoryLoading = false
                } else if (snapshot != null) {
                    directoryUsers = snapshot.documents.mapNotNull { doc ->
                        val peerUid = doc.getString("uid") ?: doc.id
                        if (peerUid == uid) return@mapNotNull null
                        val name = doc.getString("displayName").orEmpty()
                        if (name.isBlank()) return@mapNotNull null
                        DirectoryUser(ChatPeer(peerUid, name))
                    }
                    directoryError = ""
                    directoryLoading = false
                }
            }
        onDispose { listener.remove() }
    }

    LaunchedEffect(uid) {
        loadingProfile = true
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                displayName = snapshot.getString("displayName").orEmpty()
                if (displayName.isNotBlank()) {
                    val directoryData = mapOf(
                        "uid" to uid,
                        "displayName" to displayName,
                        "email" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("user_directory").document(uid)
                        .set(directoryData, SetOptions.merge())
                }
                loadingProfile = false
            }
            .addOnFailureListener { error ->
                profileError = "Gagal memuat profil: ${error.localizedMessage ?: "Kesalahan tidak diketahui"}"
                loadingProfile = false
            }
    }

    if (selectedPeer != null) {
        ChatScreen(db, uid, selectedPeer!!) { selectedPeer = null }
        return
    }

    if (loadingProfile) {
        Column(
            modifier = Modifier.fillMaxSize().background(Background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Burgundy)
            Spacer(Modifier.height(12.dp))
            Text("Memuat profil BrushApp...", color = TextMuted)
        }
        return
    }

    if (displayName.isBlank()) {
        var nameInput by remember(uid) { mutableStateOf("") }
        Column(
            modifier = Modifier.fillMaxSize().background(Background).imePadding().padding(26.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("BrushApp", color = Burgundy, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Buat profil kamu", color = TextDark, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Nama ini akan ditampilkan saat akun kamu ditemukan oleh pengguna lain.", color = TextMuted)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it; profileError = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama tampilan") },
                singleLine = true,
                shape = RoundedCornerShape(13.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(email, color = TextMuted, fontSize = 13.sp)
            if (profileError.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(profileError, color = Burgundy, fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val cleanName = nameInput.trim()
                    if (cleanName.length < 2) {
                        profileError = "Nama minimal 2 karakter."
                    } else if (cleanName.length > 30) {
                        profileError = "Nama maksimal 30 karakter."
                    } else {
                        savingProfile = true
                        val profileData = hashMapOf<String, Any>(
                            "uid" to uid,
                            "email" to email,
                            "displayName" to cleanName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        val batch = db.batch()
                        batch.set(db.collection("users").document(uid), profileData, SetOptions.merge())
                        batch.set(db.collection("user_lookup").document(email.lowercase()), profileData, SetOptions.merge())
                        batch.set(db.collection("user_directory").document(uid), mapOf(
                            "uid" to uid,
                            "displayName" to cleanName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ), SetOptions.merge())
                        batch.commit()
                            .addOnSuccessListener {
                                displayName = cleanName
                                savingProfile = false
                                profileError = ""
                            }
                            .addOnFailureListener { error ->
                                savingProfile = false
                                profileError = "Gagal menyimpan profil: ${error.localizedMessage ?: "Kesalahan tidak diketahui"}"
                            }
                    }
                },
                enabled = !savingProfile,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Burgundy, contentColor = Color.White)
            ) {
                if (savingProfile) CircularProgressIndicator(Modifier.height(21.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("SIMPAN PROFIL", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Keluar akun", color = Burgundy)
            }
        }
        return
    }

    val filteredUsers = directoryUsers.filter { item ->
        val q = searchText.trim().lowercase()
        q.isBlank() || item.peer.name.lowercase().contains(q)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).imePadding().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("BrushApp", color = Burgundy, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Halo, $displayName!", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onSignOut) {
                Text("KELUAR", color = Burgundy, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("DAFTAR PENGGUNA", color = Burgundy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Pilih orang untuk mulai mengobrol.", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari nama pengguna") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Burgundy,
                focusedLabelColor = Burgundy,
                cursorColor = Burgundy,
                unfocusedBorderColor = Color(0xFFD8D2D6)
            )
        )
        Spacer(Modifier.height(10.dp))
        when {
            directoryLoading -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Burgundy)
                    Spacer(Modifier.height(10.dp))
                    Text("Memuat daftar pengguna...", color = TextMuted)
                }
            }
            directoryError.isNotBlank() -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Gagal memuat pengguna: $directoryError", color = Burgundy, fontSize = 13.sp)
                }
            }
            filteredUsers.isEmpty() -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (directoryUsers.isEmpty()) "Belum ada pengguna lain yang terdaftar."
                        else "Pengguna tidak ditemukan.",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                    if (directoryUsers.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Minta teman masuk ke BrushApp sekali agar muncul di daftar.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredUsers, key = { it.peer.uid }) { item ->
                        TextButton(
                            onClick = {
                                openingPeerUid = item.peer.uid
                                openConversation(db, uid, displayName, item.peer) { error ->
                                    openingPeerUid = ""
                                    if (error == null) selectedPeer = item.peer
                                    else directoryError = "Gagal membuka chat: $error"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = openingPeerUid.isBlank() || openingPeerUid == item.peer.uid
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.peer.name, color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (openingPeerUid == item.peer.uid) {
                                    CircularProgressIndicator(Modifier.height(20.dp), color = Burgundy, strokeWidth = 2.dp)
                                } else {
                                    Text("CHAT  ›", color = Burgundy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun friendlyAuthError(error: Exception?): String {
    val raw = error?.message.orEmpty()
    return when {
        raw.contains("email address is already in use", ignoreCase = true) -> "Email ini sudah terdaftar. Silakan masuk."
        raw.contains("password is invalid", ignoreCase = true) || raw.contains("wrong password", ignoreCase = true) -> "Email atau kata sandi salah."
        raw.contains("no user record", ignoreCase = true) || raw.contains("user-not-found", ignoreCase = true) -> "Akun belum terdaftar. Silakan daftar dahulu."
        raw.contains("network", ignoreCase = true) -> "Koneksi internet bermasalah. Coba lagi."
        raw.isNotBlank() -> raw
        else -> "Terjadi kesalahan. Silakan coba lagi."
    }
}


private data class ChatPeer(val uid: String, val name: String)
private data class DirectoryUser(val peer: ChatPeer)
private data class ChatMessage(val id: String, val senderUid: String, val text: String, val createdAt: Timestamp?)

private fun formatMessageTime(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    return SimpleDateFormat("HH.mm", Locale.getDefault()).format(timestamp.toDate())
}

private fun conversationId(firstUid: String, secondUid: String): String {
    val pair = listOf(firstUid, secondUid).sorted().joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256").digest(pair.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

private fun openConversation(
    db: FirebaseFirestore, currentUid: String, currentName: String,
    peer: ChatPeer, onFinished: (String?) -> Unit
) {
    val id = conversationId(currentUid, peer.uid)
    val ref = db.collection("conversations").document(id)
    ref.get().addOnSuccessListener { snapshot ->
        if (snapshot.exists()) onFinished(null)
        else {
            val data = hashMapOf<String, Any>(
                "members" to listOf(currentUid, peer.uid).sorted(),
                "memberInfo" to mapOf(
                    currentUid to mapOf("displayName" to currentName),
                    peer.uid to mapOf("displayName" to peer.name)
                ),
                "createdBy" to currentUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastMessage" to "",
                "lastSenderUid" to ""
            )
            ref.set(data).addOnSuccessListener { onFinished(null) }
                .addOnFailureListener { e -> onFinished(e.localizedMessage ?: "Tidak dapat membuat ruang chat") }
        }
    }.addOnFailureListener { e -> onFinished(e.localizedMessage ?: "Tidak dapat membuka ruang chat") }
}

@Composable
private fun ChatScreen(db: FirebaseFirestore, currentUid: String, peer: ChatPeer, onBack: () -> Unit) {
    val id = conversationId(currentUid, peer.uid)
    val conversation = remember(id) { db.collection("conversations").document(id) }
    var messages by remember(id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember(id) { mutableStateOf("") }
    var loading by remember(id) { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(id) {
        val listener = conversation.collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = "Gagal memuat pesan: ${exception.localizedMessage ?: "Kesalahan tidak diketahui"}"
                    loading = false
                } else if (snapshot != null) {
                    messages = snapshot.documents.map { doc ->
                        ChatMessage(doc.id, doc.getString("senderUid").orEmpty(), doc.getString("text").orEmpty(), doc.getTimestamp("createdAt"))
                    }
                    loading = false
                }
            }
        onDispose { listener.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(Background).imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF8FA)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Kembali", color = Burgundy) }
            Column(Modifier.padding(start = 6.dp)) {
                Text(peer.name, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        if (error.isNotBlank()) Text(error, color = Burgundy, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        if (loading) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Burgundy)
                Spacer(Modifier.height(8.dp))
                Text("Memuat pesan...", color = TextMuted)
            }
        } else if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Belum ada pesan", color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("Kirim pesan pertama untuk memulai percakapan.", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val mine = message.senderUid == currentUid
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(
                            Modifier.widthIn(max = 300.dp)
                                .background(if (mine) Color(0xFFF5E5EB) else Color(0xFFF1F1F1), RoundedCornerShape(14.dp))
                                .padding(horizontal = 13.dp, vertical = 9.dp)
                        ) {
                            Text(message.text, color = TextDark, fontSize = 15.sp)
                            Row(
                                modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(if (mine) "Kamu" else peer.name, color = TextMuted, fontSize = 10.sp)
                                Text(formatMessageTime(message.createdAt), color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFFF8F6F7)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 4000) input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tulis pesan...") },
                maxLines = 4,
                shape = RoundedCornerShape(17.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Burgundy, cursorColor = Burgundy, unfocusedBorderColor = Color(0xFFD8D2D6))
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val body = input.trim()
                    if (body.isNotBlank() && !sending) {
                        sending = true
                        error = ""
                        val messageRef = conversation.collection("messages").document()
                        val batch = db.batch()
                        batch.set(messageRef, mapOf("senderUid" to currentUid, "text" to body, "createdAt" to FieldValue.serverTimestamp()))
                        batch.update(conversation, mapOf("lastMessage" to body, "lastSenderUid" to currentUid, "updatedAt" to FieldValue.serverTimestamp()))
                        batch.commit().addOnSuccessListener {
                            input = ""
                            sending = false
                            sendWorkerNotification(id, messageRef.id)
                        }.addOnFailureListener { e ->
                            sending = false
                            error = "Pesan gagal dikirim: ${e.localizedMessage ?: "Kesalahan tidak diketahui"}"
                        }
                    }
                },
                enabled = input.trim().isNotEmpty() && !sending,
                colors = ButtonDefaults.buttonColors(containerColor = Burgundy),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (sending) CircularProgressIndicator(Modifier.height(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("KIRIM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}



private fun sendWorkerNotification(conversationId: String, messageId: String) {
    val mainHandler = Handler(Looper.getMainLooper())
    fun report(text: String) {
        mainHandler.post {
            Toast.makeText(FirebaseAuth.getInstance().app.applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }
    report("BrushApp: memulai pemeriksaan notifikasi.")
    val user = FirebaseAuth.getInstance().currentUser
    if (user == null) {
        report("Notifikasi gagal: akun pengirim tidak terbaca.")
        return
    }
    user.getIdToken(false)
        .addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token
            if (idToken.isNullOrBlank()) {
                report("Notifikasi gagal: token login kosong.")
                return@addOnSuccessListener
            }
            Thread {
                var connection: HttpURLConnection? = null
                try {
                    val health = (URL("https://brushapp-server.vercel.app/api/notify").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "BrushApp-Android/1.0")
                        connectTimeout = 10000
                        readTimeout = 10000
                    }
                    val healthCode = health.responseCode
                    val healthText = (if (healthCode in 200..299) health.inputStream else health.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    health.disconnect()
                    report("Server GET: HTTP $healthCode - " + healthText.take(70))
                    connection = (URL("https://brushapp-server.vercel.app/api/notify").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("User-Agent", "BrushApp-Android/1.0")
                        connectTimeout = 15000
                        readTimeout = 20000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    val payload = JSONObject().apply {
                        put("idToken", idToken)
                        put("conversationId", conversationId)
                        put("messageId", messageId)
                    }.toString()
                    connection.outputStream.use { output ->
                        output.write(payload.toByteArray(Charsets.UTF_8))
                    }
                    val responseCode = connection.responseCode
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val result = JSONObject(responseText)
                    if (responseCode in 200..299) {
                        val sent = result.optInt("sent", 0)
                        val total = result.optInt("total", 0)
                        if (sent > 0) report("FCM: berhasil diteruskan ke $sent dari $total token.")
                        else {
                            val failures = result.optJSONArray("failures")
                            val detail = if (failures != null && failures.length() > 0) {
                                failures.optJSONObject(0)?.optString("detail", "Ditolak FCM") ?: "Ditolak FCM"
                            } else "Tidak ada token penerima."
                            report("FCM gagal ($sent/$total): " + detail.take(180))
                        }
                    } else {
                        report("Worker gagal ($responseCode): " + result.optString("error", responseText).take(180))
                    }
                } catch (e: Exception) {
                    var cause: Throwable = e
                    while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
                    val errorType = cause.javaClass.simpleName
                    val errorDetail = cause.message ?: e.message ?: "tanpa detail"
                    report("Worker gagal [$errorType]: " + errorDetail.take(140))
                } finally {
                    connection?.disconnect()
                }
            }.start()
        }
        .addOnFailureListener { error ->
            report("Gagal mengambil token login: " + (error.localizedMessage ?: "kesalahan Firebase").take(140))
        }
}

private fun saveFcmToken(uid: String, token: String) {
    val tokenId = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    FirebaseFirestore.getInstance()
        .collection("users").document(uid)
        .collection("fcm_tokens").document(tokenId)
        .set(
            mapOf(
                "token" to token,
                "platform" to "android",
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
}
