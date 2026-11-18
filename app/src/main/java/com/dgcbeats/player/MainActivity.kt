package com.dgcbeats.player

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

data class Track(val id: Long, val title: String, val artist: String, val album: String, val uri: Uri, val duration: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DgcBeatsApp() }
    }
}

@Composable
fun DgcBeatsApp() {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var current by remember { mutableStateOf<Track?>(null) }
    var playing by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Home") }
    var showLyrics by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("dgc_beats", android.content.Context.MODE_PRIVATE) }
    var banner by remember { mutableStateOf(prefs.getString("banner_text", "YOUR SOUND. YOUR SPACE.") ?: "YOUR SOUND. YOUR SPACE.") }
    var bannerEdit by remember { mutableStateOf(false) }
    var bannerImage by remember { mutableStateOf(prefs.getString("banner_image", null)) }
    var coverUris by remember { mutableStateOf(prefs.all.filterKeys { it.startsWith("cover_") }.mapKeys { it.key.removePrefix("cover_") }.mapValues { it.value as String }) }
    var lyrics by remember { mutableStateOf(prefs.getString("lyrics_${current?.id ?: 0}", "") ?: "") }
    var themeChoice by remember { mutableStateOf(prefs.getString("theme", "Midnight") ?: "Midnight") }
    var showBannerPicker by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (showBannerPicker) { bannerImage = uri.toString(); prefs.edit().putString("banner_image", bannerImage).apply() }
            else current?.let { track -> coverUris = coverUris + (track.album to uri.toString()); prefs.edit().putString("cover_${track.album}", uri.toString()).apply() }
            showBannerPicker = false
        }
    }
    var equalizerEnabled by remember { mutableStateOf(false) }
    var eq by remember { mutableStateOf<Equalizer?>(null) }

    fun loadTracks() {
        val list = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                list += Track(id, c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown artist", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown album", ContentUris.withAppendedId(collection, id), c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)))
            }
        }
        tracks = list
    }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) loadTracks() }
    LaunchedEffect(Unit) { if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) loadTracks() else launcher.launch(permission) }
    DisposableEffect(Unit) { onDispose { player.release(); eq?.release() } }

    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF090A0D), surface = Color(0xFF15171C), primary = Color(0xFFE8E8EA), onBackground = Color(0xFFF4F4F5), onSurface = Color(0xFFF4F4F5))) {
        Scaffold(containerColor = Color(0xFF090A0D), bottomBar = {
            NavigationBar(containerColor = Color(0xFF111318), contentColor = Color.White) {
                listOf("Home" to Icons.Default.Home, "Library" to Icons.Default.LibraryMusic, "Albums" to Icons.Default.Album, "Settings" to Icons.Default.Tune).forEach { (label, icon) ->
                    NavigationBarItem(selected = tab == label, onClick = { tab = label }, icon = { Icon(icon, null) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Color.White, indicatorColor = Color(0xFF30343D), unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("DGC", fontSize = 13.sp, color = Color.Gray, letterSpacing = 5.sp); Text("BEATS", fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp) }
                    IconButton(onClick = { bannerEdit = true }) { Icon(Icons.Default.Edit, "Edit banner", tint = Color.LightGray) }
                }
                when (tab) {
                    "Home" -> {
                        Box(Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF282C35), Color(0xFF111318), Color(0xFF34303B)))).clickable { bannerEdit = true }.padding(20.dp), contentAlignment = Alignment.BottomStart) {
                            if (bannerImage != null) AsyncImage(model = Uri.parse(bannerImage), contentDescription = "Custom banner", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Column(Modifier.fillMaxWidth().background(Color(0x66000000), RoundedCornerShape(12.dp)).padding(10.dp)) { Text("DGC BEATS / 001", color = Color.LightGray, fontSize = 10.sp, letterSpacing = 2.sp); Text(banner, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Tap to customize banner", color = Color.LightGray, fontSize = 11.sp) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Made for your moments", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Your music, organized your way", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        if (current != null) NowPlayingCard(current!!, playing, onPlay = { if (player.isPlaying) { player.pause(); playing = false } else { player.play(); playing = true } }, onLyrics = { lyrics = prefs.getString("lyrics_${current!!.id}", "") ?: ""; showLyrics = true }, onArtwork = { imagePicker.launch(arrayOf("image/*")) }, artwork = coverUris[current!!.album], onNext = { val i = tracks.indexOfFirst { it.id == current!!.id }; if (tracks.isNotEmpty()) { val t = tracks[(i + 1).mod(tracks.size)]; current = t; player.setMediaItem(MediaItem.fromUri(t.uri)); player.prepare(); player.play(); playing = true } }, onPrevious = { val i = tracks.indexOfFirst { it.id == current!!.id }; if (tracks.isNotEmpty()) { val t = tracks[(i - 1).mod(tracks.size)]; current = t; player.setMediaItem(MediaItem.fromUri(t.uri)); player.prepare(); player.play(); playing = true } })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Recently added", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("${tracks.size} tracks", color = Color.Gray, fontSize = 12.sp) }
                        Spacer(Modifier.height(8.dp))
                        TrackList(tracks.take(6), current) { track -> current = track; player.setMediaItem(MediaItem.fromUri(track.uri)); player.prepare(); player.play(); playing = true }
                    }
                    "Library" -> { Text("Your library", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Local audio on this device", color = Color.Gray); Spacer(Modifier.height(14.dp)); TrackList(tracks, current) { track -> current = track; player.setMediaItem(MediaItem.fromUri(track.uri)); player.prepare(); player.play(); playing = true } }
                    "Albums" -> { Text("Albums", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Album collections are built from your local music metadata.", color = Color.Gray); Spacer(Modifier.height(12.dp)); tracks.groupBy { it.album }.forEach { (album, items) -> Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { AlbumTile(album, coverUris[album]); Spacer(Modifier.width(12.dp)); Column { Text(album, fontWeight = FontWeight.SemiBold); Text("${items.size} tracks · ${items.firstOrNull()?.artist ?: "Unknown artist"}", color = Color.Gray, fontSize = 12.sp) } } } }
                    else -> { Text("Sound & appearance", fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Theme", color = Color.Gray); listOf("Midnight", "Graphite", "Ivory").forEach { choice -> Row(Modifier.fillMaxWidth().clickable { themeChoice = choice; prefs.edit().putString("theme", choice).apply() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = themeChoice == choice, onClick = { themeChoice = choice; prefs.edit().putString("theme", choice).apply() }); Text(choice) } }; Divider(color = Color.DarkGray); Row(verticalAlignment = Alignment.CenterVertically) { Text("Equalizer", Modifier.weight(1f)); Switch(equalizerEnabled, onCheckedChange = { enabled -> equalizerEnabled = enabled; if (enabled && current != null) runCatching { eq = Equalizer(0, player.audioSessionId).apply { this.enabled = true } } else { eq?.release(); eq = null } }) }; if (equalizerEnabled && eq != null) { Text("Frequency bands", color = Color.Gray, fontSize = 12.sp); (0 until (eq?.numberOfBands?.toInt() ?: 0)).forEach { band -> val range = eq!!.bandLevelRange; var level by remember(eq, band) { mutableFloatStateOf(eq!!.getBandLevel(band.toShort()).toFloat()) }; Column { Text("${eq!!.getCenterFreq(band.toShort()) / 1000} Hz", fontSize = 12.sp); Slider(value = level, onValueChange = { level = it; runCatching { eq?.setBandLevel(band.toShort(), it.toInt().toShort()) } }, valueRange = range[0].toFloat()..range[1].toFloat()) } } }; Text("Equalizer support depends on your Android device.", color = Color.Gray, fontSize = 12.sp); Spacer(Modifier.height(20.dp)); Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/"))) } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242832))) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open Spotify") }; Text("Spotify hand-off only. Full Spotify account linking and playback need developer credentials and Spotify approval.", color = Color.Gray, fontSize = 12.sp) }
                }
            }
        }
    }
    if (bannerEdit) AlertDialog(onDismissRequest = { bannerEdit = false }, title = { Text("Customize banner") }, text = { Column { OutlinedTextField(value = banner, onValueChange = { banner = it }, label = { Text("Banner text") }); TextButton(onClick = { showBannerPicker = true; imagePicker.launch(arrayOf("image/*")) }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Choose banner image") } } }, confirmButton = { TextButton(onClick = { prefs.edit().putString("banner_text", banner).apply(); bannerEdit = false }) { Text("Save") } })
    if (showLyrics) AlertDialog(onDismissRequest = { showLyrics = false }, title = { Text("Lyrics · ${current?.title ?: "Track"}") }, text = { OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, minLines = 5, label = { Text("Paste or edit lyrics") }) }, confirmButton = { TextButton(onClick = { current?.let { prefs.edit().putString("lyrics_${it.id}", lyrics).apply() }; showLyrics = false }) { Text("Save") } })
}

@Composable fun AlbumTile(label: String, artwork: String? = null) { Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF4A4D58), Color(0xFF1B1D24)))), contentAlignment = Alignment.Center) { if (artwork != null) AsyncImage(model = Uri.parse(artwork), contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Default.Album, null, tint = Color.White, modifier = Modifier.size(28.dp)) } }

@Composable fun TrackList(tracks: List<Track>, current: Track?, onClick: (Track) -> Unit) {
    LazyColumn { items(tracks, key = { it.id }) { t -> Row(Modifier.fillMaxWidth().clickable { onClick(t) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { AlbumTile(t.album); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium); Text(t.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1) }; if (current?.id == t.id) Icon(Icons.Default.GraphicEq, null, tint = Color.White) else Icon(Icons.Default.MoreHoriz, null, tint = Color.Gray) } } }
}

@Composable fun NowPlayingCard(track: Track, playing: Boolean, onPlay: () -> Unit, onLyrics: () -> Unit, onArtwork: () -> Unit, artwork: String?, onNext: () -> Unit, onPrevious: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "visualizer")
    val bars = (0..19).map { i -> transition.animateFloat(0.18f, 0.35f + (i % 5) * 0.12f, infiniteRepeatable(tween(420 + i * 23), RepeatMode.Reverse), label = "bar$i") }
    Card(Modifier.fillMaxWidth().padding(bottom = 18.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20))) {
        Column(Modifier.padding(16.dp)) {
            Text("NOW PLAYING", color = Color.Gray, fontSize = 10.sp, letterSpacing = 2.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Box(Modifier.size(154.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF292D36)).clickable { onArtwork() }, contentAlignment = Alignment.Center) { if (artwork != null) AsyncImage(model = Uri.parse(artwork), contentDescription = "Custom album cover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.LightGray, modifier = Modifier.size(36.dp)); Text("ADD COVER", color = Color.Gray, fontSize = 10.sp) } } }
            Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { bars.forEach { h -> Box(Modifier.padding(horizontal = 2.dp).width(4.dp).fillMaxHeight(h.value).clip(CircleShape).background(Color(0xFFDADCE2))) } }
            Text(track.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1); Text(track.artist, color = Color.Gray, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { IconButton(onClick = onLyrics) { Icon(Icons.Default.Lyrics, "Lyrics", tint = Color.LightGray) }; IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.LightGray) }; IconButton(onClick = onPlay, modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.Black) }; IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next", tint = Color.LightGray) }; IconButton(onClick = onArtwork) { Icon(Icons.Default.Image, "Change cover", tint = Color.LightGray) } }
        }
    }
}
