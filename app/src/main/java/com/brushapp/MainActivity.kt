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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF0C0D0E)
private val Surface = Color(0xFF17191B)
private val Accent = Color(0xFF75D6A0)
private val Muted = Color(0xFF888D91)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BrushAppScreen() }
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
fun BrushAppScreen() {
    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Surface, primary = Accent,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Bg, contentColor = Accent) {
                    NavigationBarItem(selected = true, onClick = {}, icon = { Text("●", color = Accent) }, label = { Text("Pesan", color = Color.White) })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Text("○", color = Muted) }, label = { Text("Kontak", color = Muted) })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Text("○", color = Muted) }, label = { Text("Profil", color = Muted) })
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
}

@Composable
private fun ChatRow(chat: ChatPreview) {
    Row(
        Modifier.fillMaxWidth().clickable { }.padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).background(Color(0xFF242729), CircleShape)), contentAlignment = Alignment.Center) {
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
