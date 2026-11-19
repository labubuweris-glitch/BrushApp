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
import com.google.firebase.auth.FirebaseAuth

private val Bg = Color(0xFF0C0D0E)
private val Surface = Color(0xFF17191B)
private val Accent = Color(0xFF75D6A0)
private val Muted = Color(0xFF888D91)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BrushAppRoot() }
    }
}

@Composable
private fun BrushAppRoot() {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Surface, primary = Accent,
        onBackground = Color.White, onSurface = Color.White
    )) {
        if (currentUser == null) {
            AuthScreen(auth)
        } else {
            BrushAppScreen(
                email = currentUser?.email.orEmpty(),
                onSignOut = { auth.signOut() }
            )
        }
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
        modifier = Modifier.fillMaxSize().background(Bg).imePadding()
            .padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("BrushApp", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(7.dp))
        Text("Kirim pesan, tanpa ribet.", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(34.dp))
        Text(if (isRegister) "Buat akun baru" else "Selamat datang kembali",
            color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(if (isRegister) "Daftar menggunakan email dan kata sandi."
             else "Masuk untuk melanjutkan ke BrushApp.",
            color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it; message = "" },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Alamat email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, focusedLabelColor = Accent,
                unfocusedBorderColor = Color(0xFF414548),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )
        Spacer(Modifier.height(13.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; message = "" },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Kata sandi") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, focusedLabelColor = Accent,
                unfocusedBorderColor = Color(0xFF414548),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )
        if (isRegister) {
            Spacer(Modifier.height(13.dp))
            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it; message = "" },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Ulangi kata sandi") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, focusedLabelColor = Accent,
                    unfocusedBorderColor = Color(0xFF414548),
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                )
            )
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color(0xFFFF8A80), fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                val cleanEmail = email.trim()
                when {
                    cleanEmail.isBlank() || password.isBlank() ->
                        message = "Isi email dan kata sandi terlebih dahulu."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() ->
                        message = "Format email belum benar."
                    password.length < 6 ->
                        message = "Kata sandi minimal 6 karakter."
                    isRegister && password != confirmPassword ->
                        message = "Ulangan kata sandi tidak sama."
                    else -> {
                        busy = true
                        val task = if (isRegister) {
                            auth.createUserWithEmailAndPassword(cleanEmail, password)
                        } else {
                            auth.signInWithEmailAndPassword(cleanEmail, password)
                        }
                        task.addOnCompleteListener { result ->
                            busy = false
                            if (!result.isSuccessful) {
                                message = when {
                                    result.exception?.message?.contains("already in use", true) == true ->
                                        "Email ini sudah terdaftar. Silakan masuk."
                                    result.exception?.message?.contains("no user record", true) == true ||
                                    result.exception?.message?.contains("password is invalid", true) == true ->
                                        "Email atau kata sandi salah."
                                    else -> result.exception?.localizedMessage ?: "Terjadi kesalahan. Coba lagi."
                                }
                            }
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(21.dp), color = Bg, strokeWidth = 2.dp)
            else Text(if (isRegister) "Buat akun" else "Masuk", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(17.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isRegister) "Sudah punya akun? " else "Belum punya akun? ", color = Muted, fontSize = 13.sp)
            Text(
                if (isRegister) "Masuk" else "Daftar",
                color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.clickable {
                    isRegister = !isRegister
                    message = ""
                }
            )
        }
    }
}

data class ChatPreview(val name: String, val message: String, val time: String, val initials: String, val unread: Int = 0)

private val demoChats = listOf(
    ChatPreview("Budi", "Bro, nanti jadi?", "10.42", "B", 2),
    ChatPreview("Andi", "Oke, siap.", "09.18", "A"),
    ChatPreview("Deni", "Besok kabarin ya.", "Kemarin", "D"),
    ChatPreview("Raka", "Makasih bro!", "Kemarin", "R")
)

@Composable
fun BrushAppScreen(email: String, onSignOut: () -> Unit) {
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Bg, contentColor = Accent) {
                NavigationBarItem(selected = true, onClick = {}, icon = { Text("●", color = Accent) }, label = { Text("Pesan", color = Color.White) })
                NavigationBarItem(selected = false, onClick = {}, icon = { Text("○", color = Muted) }, label = { Text("Kontak", color = Muted) })
                NavigationBarItem(selected = false, onClick = onSignOut, icon = { Text("○", color = Muted) }, label = { Text("Keluar", color = Muted) })
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {}, containerColor = Accent, contentColor = Bg, shape = RoundedCornerShape(18.dp)) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text("BrushApp", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(5.dp))
                Text("Kirim pesan, tanpa ribet.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(7.dp))
                Text(email, color = Accent, fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))
                Surface(color = Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                        Text("⌕", fontSize = 22.sp, color = Muted)
                        Spacer(Modifier.width(10.dp))
                        Text("Cari pesan atau teman", color = Muted)
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                items(demoChats) { chat -> ChatRow(chat) }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatPreview) {
    Row(
        Modifier.fillMaxWidth().clickable { }.padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).background(Color(0xFF242729), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(chat.initials, color = Accent, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(chat.message, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, color = Muted, fontSize = 11.sp)
            if (chat.unread > 0) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.background(Accent, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text(chat.unread.toString(), color = Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
