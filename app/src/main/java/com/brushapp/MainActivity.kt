package com.brushapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

private val Burgundy = Color(0xFF800020)
private val Background = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF252126)
private val TextMuted = Color(0xFF777078)
private val Success = Color(0xFF16803C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}

@Composable
private fun BrushAppRoot() {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
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
    var searchEmail by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf("") }
    var foundName by remember { mutableStateOf("") }
    var foundEmail by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        loadingProfile = true
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                displayName = snapshot.getString("displayName").orEmpty()
                loadingProfile = false
            }
            .addOnFailureListener { error ->
                profileError = "Gagal memuat profil: ${error.localizedMessage ?: "Kesalahan tidak diketahui"}"
                loadingProfile = false
            }
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
        modifier = Modifier.fillMaxSize().background(Background).imePadding().padding(26.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("BrushApp", color = Burgundy, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Halo, $displayName!", color = TextDark, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(email, color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(30.dp))
        Text("CARI PENGGUNA", color = Burgundy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Masukkan alamat email akun BrushApp yang ingin kamu cari.", color = TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = searchEmail,
            onValueChange = {
                searchEmail = it
                searchMessage = ""
                foundName = ""
                foundEmail = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email pengguna") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(13.dp)
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val query = searchEmail.trim().lowercase()
                when {
                    query.isBlank() -> searchMessage = "Masukkan email yang ingin dicari."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(query).matches() -> searchMessage = "Format email belum benar."
                    query == email.lowercase() -> searchMessage = "Itu adalah akun kamu sendiri."
                    else -> {
                        searching = true
                        searchMessage = "Mencari akun..."
                        db.collection("user_lookup").document(query).get()
                            .addOnSuccessListener { snapshot ->
                                searching = false
                                if (snapshot.exists()) {
                                    foundName = snapshot.getString("displayName").orEmpty()
                                    foundEmail = snapshot.getString("email").orEmpty()
                                    searchMessage = if (foundName.isNotBlank()) "Akun BrushApp ditemukan." else "Data akun tidak lengkap."
                                } else {
                                    searchMessage = "Akun dengan email tersebut belum ditemukan."
                                }
                            }
                            .addOnFailureListener { error ->
                                searching = false
                                searchMessage = "Pencarian gagal: ${error.localizedMessage ?: "Kesalahan tidak diketahui"}"
                            }
                    }
                }
            },
            enabled = !searching,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Burgundy, contentColor = Color.White)
        ) {
            if (searching) CircularProgressIndicator(Modifier.height(21.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("CARI AKUN", fontWeight = FontWeight.Bold)
        }
        if (searchMessage.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                searchMessage,
                color = if (searchMessage == "Akun BrushApp ditemukan.") Success else Burgundy,
                fontSize = 14.sp
            )
        }
        if (foundName.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(foundName, color = TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(foundEmail, color = TextMuted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Keluar akun", color = Burgundy)
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
