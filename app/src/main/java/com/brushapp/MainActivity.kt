package com.brushapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import java.util.Calendar
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
    private var notificationPeerUid by mutableStateOf("")
    private var notificationPeerName by mutableStateOf("")
    private var notificationRequestId by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readNotificationIntent(intent)
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
                BrushAppRoot(notificationPeerUid, notificationPeerName, notificationRequestId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNotificationIntent(intent)
    }

    private fun readNotificationIntent(intent: Intent?) {
        notificationPeerUid = intent?.getStringExtra("notification_peer_uid").orEmpty()
        notificationPeerName = intent?.getStringExtra("notification_peer_name").orEmpty()
        notificationRequestId = System.currentTimeMillis()
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
private fun BrushAppRoot(notificationPeerUid: String, notificationPeerName: String, notificationRequestId: Long) {
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
    } else if (currentUser!!.isEmailVerified) {
        UserArea(
            user = currentUser!!,
            notificationPeerUid = notificationPeerUid,
            notificationPeerName = notificationPeerName,
            notificationRequestId = notificationRequestId,
            onSignOut = { auth.signOut() }
        )
    } else {
        LaunchedEffect(currentUser!!.uid) {
            auth.signOut()
        }
        AuthScreen(auth)
    }
}

@Composable
private fun AuthScreen(auth: FirebaseAuth) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetBusy by remember { mutableStateOf(false) }

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
        AuthField(
            password, { password = it; message = "" },
            "Kata sandi", KeyboardType.Password, true, passwordVisible
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = passwordVisible, onCheckedChange = { passwordVisible = it })
            Text("Tampilkan password", color = TextMuted, fontSize = 13.sp)
        }
        if (!isRegister) {
            TextButton(
                onClick = {
                    resetEmail = email
                    showResetDialog = true
                    message = ""
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Lupa Kata Sandi?", color = Burgundy, fontSize = 13.sp)
            }
        }
        if (isRegister) {
            Spacer(Modifier.height(4.dp))
            AuthField(
                confirmPassword, { confirmPassword = it; message = "" },
                "Ulangi kata sandi", KeyboardType.Password, true, passwordVisible
            )
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
                        if (isRegister) {
                            auth.createUserWithEmailAndPassword(cleanEmail, password)
                                .addOnCompleteListener { result ->
                                    busy = false
                                    if (result.isSuccessful) {
                                        val createdUser = auth.currentUser
                                        if (createdUser != null) {
                                            createdUser.sendEmailVerification()
                                                .addOnCompleteListener { verification ->
                                                    auth.signOut()
                                                    message = if (verification.isSuccessful) {
                                                        "Akun berhasil dibuat. Silakan cek email untuk verifikasi sebelum masuk."
                                                    } else {
                                                        "Akun berhasil dibuat, tetapi email verifikasi gagal dikirim. Coba masuk lagi untuk mengirim ulang."
                                                    }
                                                }
                                        } else {
                                            auth.signOut()
                                            message = "Akun berhasil dibuat. Silakan masuk dan verifikasi email."
                                        }
                                    } else {
                                        message = friendlyAuthError(result.exception)
                                    }
                                }
                        } else {
                            auth.signInWithEmailAndPassword(cleanEmail, password)
                                .addOnCompleteListener { result ->
                                    busy = false
                                    if (result.isSuccessful) {
                                        val signedInUser = auth.currentUser
                                        if (signedInUser?.isEmailVerified == true) {
                                            message = ""
                                        } else if (signedInUser != null) {
                                            signedInUser.sendEmailVerification()
                                                .addOnCompleteListener { verification ->
                                                    auth.signOut()
                                                    message = if (verification.isSuccessful) {
                                                        "Email belum diverifikasi. Tautan verifikasi telah dikirim ulang. Silakan cek email."
                                                    } else {
                                                        "Email belum diverifikasi. Gagal mengirim tautan verifikasi. Coba lagi nanti."
                                                    }
                                                }
                                        } else {
                                            auth.signOut()
                                            message = "Gagal memeriksa akun. Silakan coba lagi."
                                        }
                                    } else {
                                        message = friendlyLoginError(result.exception)
                                    }
                                }
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

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!resetBusy) showResetDialog = false },
            title = { Text("Lupa Kata Sandi?") },
            text = {
                Column {
                    Text("Masukkan alamat email akun BrushApp. Kami akan mengirim tautan untuk membuat kata sandi baru.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Alamat email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(13.dp)
                    )
                    if (resetBusy) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.height(18.dp), color = Burgundy, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Mengirim email...", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !resetBusy,
                    onClick = {
                        val cleanResetEmail = resetEmail.trim()
                        when {
                            cleanResetEmail.isBlank() -> message = "Alamat email wajib diisi."
                            !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanResetEmail).matches() -> message = "Format email belum benar."
                            else -> {
                                resetBusy = true
                                auth.setLanguageCode("id")
                                auth.sendPasswordResetEmail(cleanResetEmail)
                                    .addOnCompleteListener { result ->
                                        resetBusy = false
                                        showResetDialog = false
                                        message = if (result.isSuccessful) {
                                            "Jika email terdaftar, tautan untuk mengatur ulang kata sandi akan dikirim. Silakan periksa kotak masuk email."
                                        } else {
                                            val raw = result.exception?.message.orEmpty()
                                            when {
                                                raw.contains("network", ignoreCase = true) -> "Koneksi internet bermasalah. Coba lagi."
                                                raw.contains("too many", ignoreCase = true) -> "Terlalu banyak percobaan. Coba lagi nanti."
                                                else -> "Email pemulihan gagal dikirim. Periksa alamat email atau coba lagi nanti."
                                            }
                                        }
                                    }
                            }
                        }
                    }
                ) {
                    Text("KIRIM TAUTAN", color = Burgundy)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !resetBusy,
                    onClick = { showResetDialog = false }
                ) {
                    Text("BATAL", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation()
                               else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Burgundy, focusedLabelColor = Burgundy, cursorColor = Burgundy,
            unfocusedBorderColor = Color(0xFFD8D2D6), focusedTextColor = TextDark, unfocusedTextColor = TextDark
        )
    )
}

@Composable
private fun UserArea(
    user: FirebaseUser,
    notificationPeerUid: String,
    notificationPeerName: String,
    notificationRequestId: Long,
    onSignOut: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = user.uid
    val email = user.email.orEmpty()
    var displayName by remember(uid) { mutableStateOf("") }
    var profilePhoto by remember(uid) { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf(false) }
    var editedName by remember(uid) { mutableStateOf("") }
    var editedPhoto by remember(uid) { mutableStateOf("") }
    var loadingProfile by remember(uid) { mutableStateOf(true) }
    var profileError by remember(uid) { mutableStateOf("") }
    var savingProfile by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    val scale = minOf(256f / bitmap.width, 256f / bitmap.height, 1f)
                    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                    val output = java.io.ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 68, output)
                    editedPhoto = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                    if (scaled !== bitmap) scaled.recycle()
                    bitmap.recycle()
                    profileError = ""
                } else profileError = "Foto tidak dapat dibaca."
            } catch (_: Exception) {
                profileError = "Gagal memproses foto. Coba foto lain."
            }
        }
    }
    var directoryUsers by remember(uid) { mutableStateOf<List<DirectoryUser>>(emptyList()) }
    var friendships by remember(uid) { mutableStateOf<Map<String, FriendLink>>(emptyMap()) }
    var requesterFriendships by remember(uid) { mutableStateOf<Map<String, FriendLink>>(emptyMap()) }
    var recipientFriendships by remember(uid) { mutableStateOf<Map<String, FriendLink>>(emptyMap()) }
    var directoryLoading by remember(uid) { mutableStateOf(true) }
    var directoryError by remember(uid) { mutableStateOf("") }
    var friendActionError by remember(uid) { mutableStateOf("") }
    var friendshipError by remember(uid) { mutableStateOf("") }
    var selectedPeer by remember { mutableStateOf<ChatPeer?>(null) }
    var openingPeerUid by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            showExitDialog -> showExitDialog = false
            selectedPeer != null -> selectedPeer = null
            editingProfile -> { editingProfile = false; profileError = "" }
            else -> showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Keluar dari BrushApp?") },
            text = { Text("Yakin ingin menutup aplikasi?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        context.findActivity()?.finish()
                    }
                ) { Text("KELUAR", color = Burgundy) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("BATAL", color = TextMuted)
                }
            }
        )
    }

    DisposableEffect(db, uid) {
        val requesterListener = db.collection("friendships")
            .whereEqualTo("requesterUid", uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    friendshipError = "Gagal membaca permintaan terkirim: ${exception.localizedMessage ?: "Periksa izin Firestore."}"
                } else if (snapshot != null) {
                    requesterFriendships = snapshot.documents.associate { doc ->
                        doc.id to FriendLink(doc.getString("status").orEmpty(), doc.getString("requesterUid").orEmpty(), doc.getString("recipientUid").orEmpty())
                    }
                    friendships = requesterFriendships + recipientFriendships
                    friendshipError = ""
                }
            }
        val recipientListener = db.collection("friendships")
            .whereEqualTo("recipientUid", uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    friendshipError = "Gagal membaca permintaan masuk: ${exception.localizedMessage ?: "Periksa izin Firestore."}"
                } else if (snapshot != null) {
                    recipientFriendships = snapshot.documents.associate { doc ->
                        doc.id to FriendLink(doc.getString("status").orEmpty(), doc.getString("requesterUid").orEmpty(), doc.getString("recipientUid").orEmpty())
                    }
                    friendships = requesterFriendships + recipientFriendships
                    friendshipError = ""
                }
            }
        directoryLoading = true
        directoryError = ""
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
                        val photo = doc.getString("photoBase64").orEmpty()
                        DirectoryUser(ChatPeer(peerUid, name, photo), photo)
                    }
                    directoryError = ""
                    directoryLoading = false
                }
            }
        onDispose { listener.remove(); requesterListener.remove(); recipientListener.remove() }
    }



    LaunchedEffect(notificationRequestId, notificationPeerUid, directoryUsers, directoryLoading, loadingProfile, friendships) {
        if (notificationPeerUid.isNotBlank() && !directoryLoading && !loadingProfile) {
            val peer = directoryUsers.firstOrNull { it.peer.uid == notificationPeerUid }?.peer
            if (peer != null) {
                openingPeerUid = peer.uid
                if (friendships[conversationId(uid, peer.uid)]?.status == "accepted") {
                    openingPeerUid = peer.uid
                    openConversation(db, uid, displayName, peer) { error ->
                        openingPeerUid = ""
                        if (error == null) selectedPeer = peer
                        else directoryError = "Gagal membuka chat dari notifikasi: $error"
                    }
                } else {
                    directoryError = "Pengirim notifikasi belum menjadi teman kamu."
                }
            } else if (directoryUsers.isNotEmpty()) {
                directoryError = "Pengirim notifikasi tidak ditemukan di daftar pengguna."
            }
        }
    }

    LaunchedEffect(uid) {
        loadingProfile = true
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                displayName = snapshot.getString("displayName").orEmpty()
                profilePhoto = snapshot.getString("photoBase64").orEmpty()
                editedName = displayName
                editedPhoto = profilePhoto
                if (displayName.isNotBlank()) {
                    val directoryData = mapOf(
                        "uid" to uid,
                        "displayName" to displayName,
                        "photoBase64" to profilePhoto,
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

    Column(
        modifier = Modifier.fillMaxSize().background(Background).imePadding().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!editingProfile) {
                Column(Modifier.weight(1f)) {
                    Text("BrushApp", color = Burgundy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = {
                    editedName = displayName
                    editedPhoto = profilePhoto
                    editingProfile = true
                    profileError = ""
                }) { Text("PROFIL", color = Burgundy, fontWeight = FontWeight.Bold) }
            } else {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { editingProfile = false; profileError = "" }) {
                    Text("SELESAI", color = Burgundy, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (editingProfile) {
            Column(Modifier.fillMaxWidth().background(Color(0xFFFFF8FA), RoundedCornerShape(14.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(editedPhoto, 68.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {                        Text("FOTO PROFIL", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextButton(onClick = { photoPicker.launch("image/*") }) {
                            Text("PILIH DARI GALERI", color = Burgundy)
                        }
                    }
                }
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama pengguna") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                if (profileError.isNotBlank()) Text(profileError, color = Burgundy, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editingProfile = false; profileError = "" }) {
                        Text("BATAL", color = TextMuted)
                    }
                    TextButton(enabled = !savingProfile, onClick = {
                        val cleanName = editedName.trim()
                        when {
                            cleanName.length < 2 -> profileError = "Nama minimal 2 karakter."
                            cleanName.length > 30 -> profileError = "Nama maksimal 30 karakter."
                            else -> {
                                savingProfile = true
                                val profileData = mapOf(
                                    "uid" to uid,
                                    "displayName" to cleanName,
                                    "photoBase64" to editedPhoto,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                                val batch = db.batch()
                                batch.set(db.collection("users").document(uid), profileData, SetOptions.merge())
                                batch.set(db.collection("user_directory").document(uid), profileData, SetOptions.merge())
                                batch.commit().addOnSuccessListener {
                                    displayName = cleanName
                                    profilePhoto = editedPhoto
                                    savingProfile = false
                                    editingProfile = false
                                    profileError = ""
                                }.addOnFailureListener { e ->
                                    savingProfile = false
                                    profileError = "Gagal menyimpan profil: ${e.localizedMessage ?: "Coba lagi."}"
                                }
                            }
                        }
                    }) {
                        Text(if (savingProfile) "MENYIMPAN..." else "SIMPAN", color = Burgundy, fontWeight = FontWeight.Bold)
                    }
                }
            }
                Spacer(Modifier.height(12.dp))
        }
        if (editingProfile) {
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Keluar dari Akun", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
            }
        } else {
        Spacer(Modifier.height(18.dp))
        Text("SEMUA PENGGUNA", color = Burgundy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (friendshipError.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(friendshipError, color = Color(0xFFD32F2F), fontSize = 12.sp)
        }
        if (friendActionError.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(friendActionError, color = TextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        when {
            directoryLoading -> {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Burgundy)
                    Spacer(Modifier.height(10.dp))
                    Text("Memuat anggota BrushApp...", color = TextMuted)
                }
            }
            directoryError.isNotBlank() -> {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Daftar pengguna belum dapat dimuat.", color = TextMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(directoryError, color = TextMuted, fontSize = 12.sp)
                }
            }
            directoryUsers.isEmpty() -> {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Belum ada anggota lain.", color = TextMuted, fontSize = 14.sp)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val incoming = directoryUsers.filter { friendships[conversationId(uid, it.peer.uid)]?.let { f -> f.status == "pending" && f.recipientUid == uid } == true }
                    val friends = directoryUsers.filter { friendships[conversationId(uid, it.peer.uid)]?.status == "accepted" }
                    val others = directoryUsers.filter { item ->
                        val f = friendships[conversationId(uid, item.peer.uid)]
                        f?.status != "accepted" && !(f?.status == "pending" && f.recipientUid == uid)
                    }
                    item { Text("PERMINTAAN MASUK (${incoming.size})", color = Burgundy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    if (incoming.isEmpty()) item { Text("Belum ada permintaan teman.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                    items(incoming, key = { "incoming_${it.peer.uid}" }) { item ->
                        FriendDirectoryRow(item, "TERIMA", "TOLAK", openingPeerUid == item.peer.uid,
                            onPrimary = {
                                openingPeerUid = item.peer.uid
                                respondFriendRequest(db, uid, item.peer.uid, "accepted") { e ->
                                    val key = conversationId(uid, item.peer.uid)
                                    val currentLink = friendships[key]
                                    if (e == null && currentLink != null) {
                                        friendships = friendships + (key to FriendLink("accepted", currentLink.requesterUid, currentLink.recipientUid))
                                        friendActionError = ""
                                    } else if (e != null) {
                                        friendActionError = e
                                    }
                                    openingPeerUid = ""
                                }
                            },
                            onSecondary = {
                                openingPeerUid = item.peer.uid
                                respondFriendRequest(db, uid, item.peer.uid, "rejected") { e ->
                                    val key = conversationId(uid, item.peer.uid)
                                    val currentLink = friendships[key]
                                    if (e == null && currentLink != null) {
                                        friendships = friendships + (key to FriendLink("rejected", currentLink.requesterUid, currentLink.recipientUid))
                                    } else if (e != null) {
                                        friendActionError = e
                                    }
                                    openingPeerUid = ""
                                }
                            })
                    }
                    item { Text("TEMAN SAYA (${friends.size})", color = Burgundy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    if (friends.isEmpty()) item { Text("Belum ada teman yang terhubung.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                    items(friends, key = { "friend_${it.peer.uid}" }) { item ->
                        FriendDirectoryRow(item, "CHAT", null, openingPeerUid == item.peer.uid,
                            onPrimary = { openingPeerUid = item.peer.uid; openConversation(db, uid, displayName, item.peer) { e -> openingPeerUid = ""; if (e == null) selectedPeer = item.peer else directoryError = "Gagal membuka chat: $e" } },
                            onSecondary = {})
                    }
                    item { Text("SEMUA PENGGUNA", color = Burgundy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(others, key = { "user_${it.peer.uid}" }) { item ->
                        val link = friendships[conversationId(uid, item.peer.uid)]
                        val label = when {
                            openingPeerUid == item.peer.uid -> "MEMPROSES..."
                            link?.status == "pending" && link.requesterUid == uid -> "MENUNGGU"
                            link?.status == "rejected" && link.requesterUid == uid -> "KIRIM ULANG"
                            link?.status == "rejected" -> "DITOLAK"
                            else -> "TAMBAH TEMAN"
                        }
                        FriendDirectoryRow(item, label, null, openingPeerUid == item.peer.uid,
                            onPrimary = {
                                openingPeerUid = item.peer.uid
                                if (link?.status == "rejected" && link.requesterUid == uid) {
                                    resendFriendRequest(db, uid, item.peer.uid) { e -> openingPeerUid = ""; if (e != null) directoryError = e }
                                } else if (link == null) {
                                    sendFriendRequest(db, uid, displayName, item.peer) { existingLink, error ->
                                        if (error == null && existingLink != null) {
                                            friendships = friendships + (conversationId(uid, item.peer.uid) to existingLink)
                                            friendActionError = ""
                                        } else if (error != null) {
                                            friendActionError = error
                                        }
                                        openingPeerUid = ""
                                    }
                                } else openingPeerUid = ""
                            }, onSecondary = {})
                    }
                }
            }
        }
        }
        }
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun friendlyLoginError(error: Exception?): String {
    val raw = error?.message.orEmpty()
    return when {
        raw.contains("network", ignoreCase = true) -> "Koneksi internet bermasalah. Coba lagi."
        raw.contains("too many requests", ignoreCase = true) -> "Terlalu banyak percobaan. Coba lagi nanti."
        else -> "Email atau password salah atau belum terdaftar."
    }
}

private fun friendlyAuthError(error: Exception?): String {
    val raw = error?.message.orEmpty()
    return when {
        raw.contains("email address is already in use", ignoreCase = true) -> "Email ini sudah terdaftar. Silakan masuk."
        raw.contains("network", ignoreCase = true) -> "Koneksi internet bermasalah. Coba lagi."
        raw.isNotBlank() -> raw
        else -> "Terjadi kesalahan. Silakan coba lagi."
    }
}


private data class ChatPeer(val uid: String, val name: String, val photoBase64: String = "")
private data class DirectoryUser(val peer: ChatPeer, val photoBase64: String = "")
private data class FriendLink(val status: String, val requesterUid: String, val recipientUid: String)
private data class ChatMessage(val id: String, val senderUid: String, val text: String, val createdAt: Timestamp?, val readAt: Timestamp?)

@Composable
private fun ProfileAvatar(photoBase64: String, diameter: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(photoBase64) {
        try {
            if (photoBase64.isBlank()) null
            else {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Foto profil",
            modifier = Modifier.size(diameter).clip(RoundedCornerShape(50)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier.size(diameter).clip(RoundedCornerShape(50)).background(Color(0xFFF1E3E9)),
            contentAlignment = Alignment.Center
        ) {
            Text("B", color = Burgundy, fontWeight = FontWeight.Bold, fontSize = (diameter.value * 0.42f).sp)
        }
    }
}

private fun formatMessageTime(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    return SimpleDateFormat("HH.mm", Locale.getDefault()).format(timestamp.toDate())
}

private fun messageDateKey(timestamp: Timestamp?): String? {
    if (timestamp == null) return null
    return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(timestamp.toDate())
}

private fun formatMessageDate(timestamp: Timestamp): String {
    val target = Calendar.getInstance().apply { time = timestamp.toDate() }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "HARI INI"
        target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "KEMARIN"
        else -> SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
            .format(timestamp.toDate()).uppercase(Locale("id", "ID"))
    }
}

private fun conversationId(firstUid: String, secondUid: String): String {
    val pair = listOf(firstUid, secondUid).sorted().joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256").digest(pair.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

@Composable
private fun FriendDirectoryRow(item: DirectoryUser, primaryLabel: String, secondaryLabel: String?, busy: Boolean, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(item.photoBase64, 42.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(item.peer.name, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Burgundy, strokeWidth = 2.dp)
        else {
            TextButton(onClick = onPrimary, enabled = primaryLabel !in listOf("MENUNGGU", "DITOLAK", "MEMPROSES...")) { Text(primaryLabel, color = Burgundy, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            if (secondaryLabel != null) TextButton(onClick = onSecondary) { Text(secondaryLabel, color = TextMuted, fontSize = 10.sp) }
        }
    }
}

private fun sendFriendRequest(db: FirebaseFirestore, uid: String, name: String, peer: ChatPeer, done: (FriendLink?, String?) -> Unit) {
    val ref = db.collection("friendships").document(conversationId(uid, peer.uid))
    ref.get().addOnSuccessListener { snap ->
        if (snap.exists()) {
            done(
                FriendLink(
                    snap.getString("status").orEmpty(),
                    snap.getString("requesterUid").orEmpty(),
                    snap.getString("recipientUid").orEmpty()
                ),
                null
            )
        } else {
            ref.set(mapOf("users" to listOf(uid, peer.uid).sorted(), "requesterUid" to uid, "recipientUid" to peer.uid, "requesterName" to name, "status" to "pending", "requestedAt" to FieldValue.serverTimestamp()))
                .addOnSuccessListener { done(FriendLink("pending", uid, peer.uid), null) }
                .addOnFailureListener { e -> done(null, "Gagal mengirim permintaan: ${e.localizedMessage ?: "Coba lagi."}") }
        }
    }.addOnFailureListener { e -> done(null, "Gagal memeriksa pertemanan: ${e.localizedMessage ?: "Coba lagi."}") }
}

private fun resendFriendRequest(db: FirebaseFirestore, uid: String, peerUid: String, done: (String?) -> Unit) {
    db.collection("friendships").document(conversationId(uid, peerUid))
        .update(mapOf("status" to "pending", "requesterUid" to uid, "requestedAt" to FieldValue.serverTimestamp(), "respondedAt" to FieldValue.delete()))
        .addOnSuccessListener { done(null) }.addOnFailureListener { e -> done("Gagal mengirim ulang: ${e.localizedMessage ?: "Coba lagi."}") }
}

private fun respondFriendRequest(db: FirebaseFirestore, uid: String, peerUid: String, status: String, done: (String?) -> Unit) {
    db.collection("friendships").document(conversationId(uid, peerUid))
        .update(mapOf("status" to status, "respondedAt" to FieldValue.serverTimestamp()))
        .addOnSuccessListener { done(null) }.addOnFailureListener { e -> done("Gagal memperbarui permintaan: ${e.localizedMessage ?: "Coba lagi."}") }
}

private fun openConversation(
    db: FirebaseFirestore, currentUid: String, currentName: String,
    peer: ChatPeer, onFinished: (String?) -> Unit
) {
    val id = conversationId(currentUid, peer.uid)
    val ref = db.collection("conversations").document(id)
    ref.get().addOnSuccessListener { snapshot ->
        if (snapshot.exists()) {
            val existingFriendshipId = snapshot.getString("friendshipId")
            if (existingFriendshipId == id) {
                onFinished(null)
            } else if (existingFriendshipId.isNullOrBlank()) {
                // Migrasi ruang chat lama tanpa menghapus riwayat pesan.
                ref.update("friendshipId", id)
                    .addOnSuccessListener { onFinished(null) }
                    .addOnFailureListener { e ->
                        onFinished(e.localizedMessage ?: "Tidak dapat memperbarui ruang chat lama")
                    }
            } else {
                onFinished("Data pertemanan ruang chat tidak cocok.")
            }
        } else {
            val data = hashMapOf<String, Any>(
                "members" to listOf(currentUid, peer.uid).sorted(),
                "friendshipId" to id,
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
    val pendingReadIds = remember(id) { mutableSetOf<String>() }

    DisposableEffect(id) {
        val listener = conversation.collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {                    error = "Gagal memuat pesan: ${exception.localizedMessage ?: "Kesalahan tidak diketahui"}"
                    loading = false
                } else if (snapshot != null) {
                    messages = snapshot.documents.map { doc ->
                        ChatMessage(doc.id, doc.getString("senderUid").orEmpty(), doc.getString("text").orEmpty(), doc.getTimestamp("createdAt"), doc.getTimestamp("readAt"))
                    }
                    val unreadIncoming = snapshot.documents.filter { doc ->
                        doc.getString("senderUid") != currentUid &&
                            !doc.contains("readAt") &&
                            !pendingReadIds.contains(doc.id)
                    }
                    unreadIncoming.chunked(450).forEach { unreadBatch ->
                        val batch = db.batch()
                        unreadBatch.forEach { doc ->
                            pendingReadIds.add(doc.id)
                            batch.update(doc.reference, "readAt", FieldValue.serverTimestamp())
                        }
                        batch.commit().addOnFailureListener {
                            unreadBatch.forEach { doc -> pendingReadIds.remove(doc.id) }
                        }
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
            ProfileAvatar(peer.photoBase64, 42.dp)
            Column(Modifier.padding(start = 10.dp)) {
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
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    val currentDateKey = messageDateKey(message.createdAt)
                    val previousDateKey = if (index > 0) messageDateKey(messages[index - 1].createdAt) else null
                    if (message.createdAt != null && currentDateKey != previousDateKey) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                formatMessageDate(message.createdAt),
                                modifier = Modifier
                                    .background(Color(0xFFF3EDF0), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 13.dp, vertical = 6.dp),
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
                                Text(formatMessageTime(message.createdAt), color = TextMuted, fontSize = 10.sp)
                                if (mine) {
                                    if (message.readAt != null) {
                                        DoubleCheckIcon(Burgundy)
                                    } else {
                                        Text(
                                            "✓",
                                            color = Color(0xFF9A9699),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
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



@Composable
private fun DoubleCheckIcon(color: Color) {
    Canvas(modifier = Modifier.size(width = 17.dp, height = 12.dp)) {
        val stroke = 1.7.dp.toPx()
        val firstX = 0f
        val secondX = 6.5.dp.toPx()
        val startY = size.height * 0.52f
        val middleY = size.height * 0.82f
        val endY = size.height * 0.16f
        val shortLeg = 3.dp.toPx()
        val longLeg = 6.dp.toPx()

        listOf(firstX, secondX).forEach { x ->
            drawLine(
                color = color,
                start = Offset(x, startY),
                end = Offset(x + shortLeg, middleY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(x + shortLeg, middleY),
                end = Offset(x + shortLeg + longLeg, endY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun sendWorkerNotification(conversationId: String, messageId: String) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    user.getIdToken(false)
        .addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token
            if (idToken.isNullOrBlank()) return@addOnSuccessListener

            Thread {
                var connection: HttpURLConnection? = null
                try {
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
                    // Hasil notifikasi diproses tanpa Toast agar tidak mengganggu tampilan chat.
                    connection.responseCode
                } catch (_: Exception) {
                    // Kegagalan notifikasi tidak mengganggu pengiriman dan penyimpanan pesan.
                } finally {
                    connection?.disconnect()
                }
            }.start()
        }
        .addOnFailureListener {
            // Pengambilan token gagal; pesan chat tetap tersimpan di Firestore.
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