package com.brushapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

private val Bg = Color(0xFF0C0D0E)
private val Panel = Color(0xFF17191B)
private val Accent = Color(0xFF75D6A0)
private val Muted = Color(0xFF888D91)
private val White = Color.White

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BrushAppRoot() }
    }
}

@Composable
private fun BrushAppRoot() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var profileReady by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf<String?>(null) }
    var profileError by remember { mutableStateOf("") }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            user = it.currentUser
            profileReady = false
            username = null
            profileError = ""
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        profileReady = false
        db.collection("profiles").document(uid).get()
            .addOnSuccessListener { doc ->
                username = doc.getString("username")
                profileReady = true
            }
            .addOnFailureListener { e ->
                profileError = e.localizedMessage ?: "Tidak dapat memuat profil."
                profileReady = true
            }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Panel, primary = Accent,
        onBackground = White, onSurface = White
    )) {
        when {
            user == null -> AuthScreen(auth)
            !profileReady -> LoadingScreen("Memuat BrushApp...")
            profileError.isNotBlank() && username == null -> ErrorScreen(profileError) {
                profileError = ""
                val uid = user?.uid ?: return@ErrorScreen
                db.collection("profiles").document(uid).get()
                    .addOnSuccessListener { doc -> username = doc.getString("username"); profileReady = true }
                    .addOnFailureListener { profileError = it.localizedMessage ?: "Gagal memuat profil." }
            }
            username == null -> ProfileSetupScreen(user!!.uid, { username = it }, { auth.signOut() })
            else -> BrushHomeScreen(user!!.uid, username!!, { auth.signOut() })
        }
    }
}

@Composable
private fun AuthScreen(auth: FirebaseAuth) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Bg).imePadding().padding(26.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("BrushApp", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = White)
        Spacer(Modifier.height(7.dp))
        Text("Kirim pesan, tanpa ribet.", color = Muted)
        Spacer(Modifier.height(34.dp))
        Text(if (register) "Buat akun baru" else "Selamat datang kembali",
            color = White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp))
        AppField(email, { email = it; message = "" }, "Alamat email", KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AppField(password, { password = it; message = "" }, "Kata sandi", KeyboardType.Password, true)
        if (register) {
            Spacer(Modifier.height(12.dp))
            AppField(confirm, { confirm = it; message = "" }, "Ulangi kata sandi", KeyboardType.Password, true)
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = Color(0xFFFF8A80), fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val clean = email.trim()
                when {
                    clean.isBlank() || password.isBlank() -> message = "Isi email dan kata sandi."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(clean).matches() -> message = "Format email belum benar."
                    password.length < 6 -> message = "Kata sandi minimal 6 karakter."
                    register && password != confirm -> message = "Ulangan kata sandi tidak sama."
                    else -> {
                        busy = true
                        val task = if (register) auth.createUserWithEmailAndPassword(clean, password)
                                   else auth.signInWithEmailAndPassword(clean, password)
                        task.addOnCompleteListener {
                            busy = false
                            if (!it.isSuccessful) message = it.exception?.localizedMessage ?: "Terjadi kesalahan."
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Bg, strokeWidth = 2.dp)
            else Text(if (register) "Buat akun" else "Masuk", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(if (register) "Sudah punya akun? " else "Belum punya akun? ", color = Muted)
            Text(if (register) "Masuk" else "Daftar", color = Accent,
                modifier = Modifier.clickable { register = !register; message = "" })
        }
    }
}

@Composable
private fun AppField(value: String, change: (String) -> Unit, label: String,
                     keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = change, modifier = Modifier.fillMaxWidth(),
        singleLine = true, label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, focusedLabelColor = Accent,
            unfocusedBorderColor = Color(0xFF414548),
            focusedTextColor = White, unfocusedTextColor = White
        )
    )
}

@Composable
private fun LoadingScreen(label: String) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(14.dp))
            Text(label, color = Muted)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("Belum bisa memuat profil", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, color = Muted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) {
            Text("Coba lagi")
        }
    }
}

@Composable
private fun ProfileSetupScreen(uid: String, created: (String) -> Unit, signOut: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var handle by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg).imePadding().padding(26.dp), verticalArrangement = Arrangement.Center) {
        Text("Buat ID BrushApp", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Text("Teman bisa menemukanmu dengan ID ini. Emailmu tidak ditampilkan.", color = Muted)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }; error = "" },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("ID unik, contoh: jimbu123") },
            leadingIcon = { Text("@", color = Accent) },
            supportingText = { Text("3–20 karakter: huruf, angka, garis bawah.", color = Muted) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent,
                unfocusedBorderColor = Color(0xFF414548), focusedTextColor = White, unfocusedTextColor = White)
        )
        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color(0xFFFF8A80), fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (handle.length !in 3..20) error = "ID harus 3 sampai 20 karakter."
                else {
                    busy = true
                    val handleRef = db.collection("handles").document(handle)
                    val profileRef = db.collection("profiles").document(uid)
                    db.runTransaction { tx ->
                        if (tx.get(handleRef).exists()) throw IllegalStateException("ID sudah dipakai.")
                        tx.set(handleRef, mapOf("uid" to uid, "username" to handle))
                        tx.set(profileRef, mapOf("uid" to uid, "username" to handle,
                            "createdAt" to FieldValue.serverTimestamp()))
                        null
                    }.addOnSuccessListener { busy = false; created(handle) }
                        .addOnFailureListener { e ->
                            busy = false
                            error = if (e.message?.contains("ID sudah dipakai", true) == true)
                                "ID itu sudah dipakai. Coba yang lain."
                            else e.localizedMessage ?: "Gagal menyimpan ID."
                        }
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Bg, strokeWidth = 2.dp)
            else Text("Simpan ID", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = signOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Keluar akun", color = Muted)
        }
    }
}

private data class ChatItem(
    val id: String, val otherUid: String, val otherUsername: String,
    val lastMessage: String, val updatedAt: Timestamp?
)
private data class MessageItem(val id: String, val senderId: String, val text: String)

@Composable
private fun BrushHomeScreen(uid: String, username: String, signOut: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var selected by remember { mutableStateOf<ChatItem?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        val listener: ListenerRegistration = db.collection("chats").whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { error = e.localizedMessage ?: "Gagal memuat chat."; loading = false }
                else if (snapshot != null) {
                    val docs = snapshot.documents
                    if (docs.isEmpty()) { chats = emptyList(); loading = false }
                    else {
                        val result = mutableListOf<ChatItem>()
                        var pending = docs.size
                        docs.forEach { doc ->
                            val members = doc.get("members") as? List<*> ?: emptyList<Any>()
                            val other = members.filterIsInstance<String>().firstOrNull { it != uid }
                            if (other == null) {
                                pending--
                                if (pending == 0) { chats = result; loading = false }
                            } else {
                                db.collection("profiles").document(other).get()
                                    .addOnSuccessListener { profile ->
                                        result.add(ChatItem(doc.id, other, profile.getString("username") ?: "pengguna",
                                            doc.getString("lastMessage") ?: "", doc.getTimestamp("updatedAt")))
                                        pending--
                                        if (pending == 0) { chats = result.sortedByDescending { it.updatedAt?.seconds ?: 0L }; loading = false }
                                    }
                                    .addOnFailureListener {
                                        result.add(ChatItem(doc.id, other, "pengguna", doc.getString("lastMessage") ?: "", doc.getTimestamp("updatedAt")))
                                        pending--
                                        if (pending == 0) { chats = result; loading = false }
                                    }
                            }
                        }
                    }
                }
            }
        onDispose { listener.remove() }
    }

    if (selected != null) {
        ChatThreadScreen(uid, selected!!, { selected = null })
        return
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Bg) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("●", color = if (tab == 0) Accent else Muted) }, label = { Text("Pesan") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("+", color = if (tab == 1) Accent else Muted) }, label = { Text("Cari ID") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("○", color = if (tab == 2) Accent else Muted) }, label = { Text("Profil") })
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                    Text("BrushApp", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Kirim pesan, tanpa ribet.", color = Muted)
                    Spacer(Modifier.height(20.dp))
                    Text("Percakapan", color = White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                when {
                    loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                    error.isNotBlank() -> Text(error, color = Color(0xFFFF8A80), modifier = Modifier.padding(20.dp))
                    chats.isEmpty() -> EmptyState("Belum ada percakapan", "Cari ID teman untuk memulai obrolan.")
                    else -> LazyColumn {
                        items(chats, key = { it.id }) { chat -> ChatListRow(chat) { selected = chat } }
                    }
                }
            }
            1 -> SearchUserScreen(uid) { selected = it; tab = 0 }
            else -> ProfileScreen(username, signOut)
        }
    }
}

@Composable
private fun ChatListRow(chat: ChatItem, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).background(Color(0xFF242729), CircleShape), contentAlignment = Alignment.Center) {
            Text(chat.otherUsername.take(1).uppercase(), color = Accent, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("@${chat.otherUsername}", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(chat.lastMessage.ifBlank { "Mulai percakapan" }, color = Muted, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = Color(0xFF25292B), modifier = Modifier.padding(start = 86.dp))
}

@Composable
private fun SearchUserScreen(uid: String, openChat: (ChatItem) -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var handle by remember { mutableStateOf("") }
    var foundUid by remember { mutableStateOf<String?>(null) }
    var foundName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg).imePadding().padding(22.dp)) {
        Text("Cari teman", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("Masukkan ID BrushApp temanmu.", color = Muted)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }; foundUid = null; foundName = null; message = "" },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("ID teman") }, leadingIcon = { Text("@", color = Accent) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent,
                unfocusedBorderColor = Color(0xFF414548), focusedTextColor = White, unfocusedTextColor = White)
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (handle.length !in 3..20) message = "Masukkan ID 3–20 karakter."
                else {
                    busy = true
                    db.collection("handles").document(handle).get()
                        .addOnSuccessListener { doc ->
                            val target = doc.getString("uid")
                            if (!doc.exists() || target.isNullOrBlank()) {
                                message = "ID tersebut tidak ditemukan."; busy = false
                            } else if (target == uid) {
                                message = "Itu ID milikmu sendiri."; busy = false
                            } else {
                                db.collection("profiles").document(target).get()
                                    .addOnSuccessListener { p ->
                                        foundUid = target
                                        foundName = p.getString("username") ?: handle
                                        busy = false
                                    }
                                    .addOnFailureListener { e -> message = e.localizedMessage ?: "Gagal memuat profil."; busy = false }
                            }
                        }
                        .addOnFailureListener { e -> message = e.localizedMessage ?: "Pencarian gagal."; busy = false }
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(19.dp), color = Bg, strokeWidth = 2.dp)
            else Text("Cari ID", fontWeight = FontWeight.Bold)
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(message, color = Muted)
        }
        if (foundUid != null && foundName != null) {
            Spacer(Modifier.height(22.dp))
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Pengguna ditemukan", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("@${foundName}", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            busy = true
                            val other = foundUid!!
                            val members = listOf(uid, other).sorted()
                            val chatId = members.joinToString("_")
                            val ref = db.collection("chats").document(chatId)
                            db.runTransaction { tx ->
                                if (!tx.get(ref).exists()) tx.set(ref, mapOf(
                                    "members" to members,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp(),
                                    "lastMessage" to ""
                                ))
                                null
                            }.addOnSuccessListener {
                                busy = false
                                openChat(ChatItem(chatId, other, foundName ?: handle, "", null))
                            }.addOnFailureListener { e -> busy = false; message = e.localizedMessage ?: "Gagal membuat chat." }
                        },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
                    ) { Text("Mulai chat", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadScreen(uid: String, chat: ChatItem, back: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var messages by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(chat.id) {
        val listener = db.collection("chats").document(chat.id).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) error = e.localizedMessage ?: "Gagal memuat pesan."
                else if (snapshot != null) messages = snapshot.documents.map { d ->
                    MessageItem(d.id, d.getString("senderId") ?: "", d.getString("text") ?: "")
                }
            }
        onDispose { listener.remove() }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Accent, fontSize = 32.sp, modifier = Modifier.clickable(onClick = back).padding(horizontal = 10.dp))
            Column {
                Text("@${chat.otherUsername}", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Percakapan pribadi", color = Muted, fontSize = 12.sp)
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            items(messages, key = { it.id }) { msg -> MessageBubble(msg, msg.senderId == uid) }
        }
        Row(Modifier.fillMaxWidth().background(Panel).imePadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Tulis pesan...", color = Muted) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF414548),
                    focusedTextColor = White, unfocusedTextColor = White)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty() && !sending) {
                        sending = true
                        val chatRef = db.collection("chats").document(chat.id)
                        val msgRef = chatRef.collection("messages").document()
                        val batch = db.batch()
                        batch.set(msgRef, mapOf("senderId" to uid, "text" to text, "createdAt" to FieldValue.serverTimestamp()))
                        batch.update(chatRef, mapOf("lastMessage" to text, "updatedAt" to FieldValue.serverTimestamp()))
                        batch.commit().addOnSuccessListener { input = ""; sending = false }
                            .addOnFailureListener { e -> error = e.localizedMessage ?: "Pesan gagal dikirim."; sending = false }
                    }
                },
                enabled = input.trim().isNotEmpty() && !sending,
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
            ) { Text("➤", fontSize = 17.sp) }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageItem, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) Color(0xFF245A40) else Panel,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 290.dp)
        ) {
            Text(message.text, color = White, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), fontSize = 15.sp)
        }
    }
}

@Composable
private fun ProfileScreen(username: String, signOut: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(24.dp)) {
        Text("Profil", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("ID BrushApp", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(7.dp))
                Text("@$username", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(22.dp))
        OutlinedButton(onClick = signOut, modifier = Modifier.fillMaxWidth()) { Text("Keluar akun", color = White) }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}
