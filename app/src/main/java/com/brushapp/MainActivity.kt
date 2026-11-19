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

private val Burgundy = Color(0xFF800020)
private val Background = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF252126)
private val TextMuted = Color(0xFF777078)

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
        SignedInScreen(
            email = currentUser?.email.orEmpty(),
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
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BrushApp",
            color = Burgundy,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sederhana. Nyaman. Terhubung.",
            color = TextMuted,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(38.dp))
        Text(
            text = if (isRegister) "Buat akun baru" else "Selamat datang",
            color = TextDark,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(20.dp))

        AuthField(
            value = email,
            onValueChange = { email = it; message = "" },
            label = "Alamat email",
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            value = password,
            onValueChange = { password = it; message = "" },
            label = "Kata sandi",
            keyboardType = KeyboardType.Password,
            isPassword = true
        )
        if (isRegister) {
            Spacer(Modifier.height(12.dp))
            AuthField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; message = "" },
                label = "Ulangi kata sandi",
                keyboardType = KeyboardType.Password,
                isPassword = true
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
                    cleanEmail.isBlank() || password.isBlank() ->
                        message = "Email dan kata sandi wajib diisi."
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
                                message = friendlyAuthError(result.exception)
                            }
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Burgundy,
                contentColor = Color.White
            )
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(21.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isRegister) "Daftar" else "Masuk",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        TextButton(
            onClick = {
                isRegister = !isRegister
                message = ""
                confirmPassword = ""
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = if (isRegister) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar",
                color = Burgundy
            )
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
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Burgundy,
            focusedLabelColor = Burgundy,
            cursorColor = Burgundy,
            unfocusedBorderColor = Color(0xFFD8D2D6),
            focusedTextColor = TextDark,
            unfocusedTextColor = TextDark
        )
    )
}

@Composable
private fun SignedInScreen(email: String, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "BrushApp",
            color = Burgundy,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Login berhasil",
            color = TextDark,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = email,
            color = TextMuted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Burgundy,
                contentColor = Color.White
            )
        ) {
            Text("Keluar akun", fontWeight = FontWeight.Bold)
        }
    }
}

private fun friendlyAuthError(error: Exception?): String {
    val raw = error?.message.orEmpty()
    return when {
        raw.contains("email address is already in use", ignoreCase = true) ->
            "Email ini sudah terdaftar. Silakan masuk."
        raw.contains("password is invalid", ignoreCase = true) ||
            raw.contains("wrong password", ignoreCase = true) ->
            "Email atau kata sandi salah."
        raw.contains("no user record", ignoreCase = true) ||
            raw.contains("user-not-found", ignoreCase = true) ->
            "Akun belum terdaftar. Silakan daftar dahulu."
        raw.contains("network", ignoreCase = true) ->
            "Koneksi internet bermasalah. Coba lagi."
        raw.isNotBlank() -> raw
        else -> "Terjadi kesalahan. Silakan coba lagi."
    }
}
