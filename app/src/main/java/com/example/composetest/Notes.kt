package com.example.composetest

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.text.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.NoteAdd
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.*
import com.example.composetest.ui.theme.ComposetestTheme
import kotlinx.coroutines.launch
import java.util.*

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time?.toLong()
    }

    @TypeConverter
    fun noteEncoderToData(ne: NoteEncoder?): ByteArray? {
        return ne?.toDb()
    }

    @TypeConverter
    fun encodingDataToNoteEncoder(ne: ByteArray?): NoteEncoder? {
        return ne?.let { NoteEncoder.fromDb(ne) }
    }

    @TypeConverter
    fun readEncodedString(es: ByteArray?): EncodedString? {
        return es?.let { EncodedString(es) }
    }

    @TypeConverter
    fun writeEncodedString(es: EncodedString?): ByteArray? {
        return es?.encodedString
    }
}


@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val publicTitle: String? = null,
    val privateTitle: EncodedString? = null,

    val privateContent: EncodedString,
    val date: Date = Calendar.getInstance().time,

    val noteEncoder: NoteEncoder
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    @Insert
    suspend fun insertAll(vararg notes: Note)

    @Delete
    suspend fun delete(note: Note)
}

@Database(entities = [Note::class], version = 5)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {

        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    ).fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}

const val DATABASE_NAME = "notes-db"

class NotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposetestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    NotesContainer()
                }
            }
        }
    }
}

@Composable
fun NotesContainer() {
    val context = LocalContext.current

    val notes = remember { mutableStateListOf<Note>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val db = AppDatabase.getInstance(context)
            val noteDao = db.noteDao()
            notes.addAll(noteDao.getAll())
        }
    }

    var isAddVisible by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isAddVisible,
        enter = slideInVertically(spring()),
        exit = slideOutVertically(spring())
    ) {
        AddNote(onNewNoteAdded = {
            coroutineScope.launch {
                val dao = AppDatabase.getInstance(context).noteDao()
                dao.insertAll(it)
                notes.add(it)
            }
            isAddVisible = false
        })
    }

    BackHandler(isAddVisible) {
        isAddVisible = false
    }
    val haptic = LocalHapticFeedback.current
    AnimatedVisibility(
        visible = !isAddVisible,
        enter = slideInVertically(spring()),
        exit = slideOutVertically(spring())
    ) {
        NotesTestable(
            notes,
            onAddClick = { isAddVisible = true },
            onDelete = { i,n ->
                Toast.makeText(context, "Removing note ${n.publicTitle}", Toast.LENGTH_SHORT).show()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coroutineScope.launch {
                    val dao = AppDatabase.getInstance(context).noteDao()
                    dao.delete(n)
                    notes.removeAt(i)
                }
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CheckPassword(
    shouldCheckPassword: MutableState<Boolean>,
    onLogin: (String) -> Unit,
    onCancel: () -> Unit,
    hint: String? = null
) {
    val context = LocalContext.current
    if (shouldCheckPassword.value) {
        Dialog(
            onDismissRequest = onCancel,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(Modifier.fillMaxSize()) {
                Login(
                    onLogin = { pwrd ->
                        shouldCheckPassword.value = false
                        if (checkPassword(pwrd, context)) {
                            onLogin(pwrd)
                        } else {
                            Toast.makeText(context, "Invalid password", Toast.LENGTH_SHORT).show()
                        }
                    },
                    passwordState = PasswordState.PasswordSet,
                    requestFocus = true,
                    giveHintFunc = { pir, s, m ->
                        when (pir) {
                            InvalidPasswordReason.Empty -> hint ?: "Please repeat password to encrypt"
                            else -> giveHint(pir, s, m)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AddNote(onNewNoteAdded: (Note) -> Unit) {
    var publicTitle: String? by remember { mutableStateOf(null) }
    var privateTitle: String? by remember { mutableStateOf(null) }
    var privateContent by remember { mutableStateOf("") }
    Surface {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextField(
                value = publicTitle ?: "",
                onValueChange = { publicTitle = it },
                Modifier.fillMaxWidth(),
                label = { Text("Public title (unencrypted)", fontWeight = FontWeight.Bold) },
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = privateTitle ?: "",
                onValueChange = { privateTitle = it },
                Modifier.fillMaxWidth(),
                label = { Text("Private title (encrypted)", fontWeight = FontWeight.Bold) },
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = privateContent,
                onValueChange = { privateContent = it },
                Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .scrollable(
                        ScrollableState { it },
                        enabled = true,
                        orientation = Orientation.Vertical
                    ),
                label = { Text("Content*") },
                isError = privateContent.isEmpty(),
            )
            Spacer(Modifier.height(30.dp))

            val shouldCheckPassword = remember { mutableStateOf(false) }
            CheckPassword(
                shouldCheckPassword,
                onLogin = { pwrd ->
                    val noteEncoder = NoteEncoder(pwrd)
                    onNewNoteAdded(
                        Note(
                            publicTitle = publicTitle,
                            privateTitle = privateTitle?.let { EncodedString(it, noteEncoder) },
                            privateContent = EncodedString(privateContent, noteEncoder),
                            noteEncoder = noteEncoder
                        )
                    )
                },
                onCancel = { shouldCheckPassword.value = false },
                hint = "Please repeat password to encrypt new note"
            )
            Button(
                onClick = {
                    shouldCheckPassword.value = true
                },
                Modifier
                    .height(50.dp)
                    .fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.TwoTone.NoteAdd, "Add note")
                    Spacer(Modifier.width(20.dp))
                    Text("Add this note")
                }
            }
            Spacer(Modifier.height(200.dp))
        }
    }
}

@Preview
@Composable
fun AddNotePreview() {
    AddNote {}
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NotesTestable(notes: List<Note>, onAddClick: () -> Unit, onDelete: (Int, Note) -> Unit) {
    Scaffold(floatingActionButton = {
        FloatingActionButton(
            onClick = onAddClick,
            backgroundColor = MaterialTheme.colors.secondary,
            contentColor = contentColorFor(MaterialTheme.colors.secondary),
            modifier = Modifier.padding(bottom = 40.dp)
        ) {
            Icon(
                imageVector = Icons.TwoTone.Add,
                contentDescription = "Add a note",
                Modifier
                    .padding(25.dp)
                    .scale(1.4f)
            )
        }
    }, floatingActionButtonPosition = FabPosition.Center) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            for ((i, note) in notes.withIndex()) {
                item {
                    NoteItem(note, onDelete = {
                        onDelete(i, note)
                    })
                }
            }
        }
    }
}

@Preview
@Composable
fun NotesPreview() {
    val noteEncoder = NoteEncoder("password")
    NotesTestable(
        listOf(
            Note(
                0,
                date = Calendar.getInstance().time,
                publicTitle = "Hello there",
                privateContent = EncodedString("example note", noteEncoder),
                noteEncoder = noteEncoder
            ),
            Note(
                1,
                date = Calendar.getInstance().let { it.add(Calendar.MINUTE, 10); it.time },
                publicTitle = "General Kenobi",
                privateContent = EncodedString("general kenobi", noteEncoder),
                noteEncoder = noteEncoder
            )
        ),
        onAddClick = {},
        onDelete = {i,e -> }
    )
}