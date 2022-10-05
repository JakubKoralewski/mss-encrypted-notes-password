package com.example.composetest

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NoteItem(note: Note, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shouldCheckPassword = remember { mutableStateOf(false) }
    var decodedContent by remember { mutableStateOf<String?>(null) }
    var decodedTitle by remember { mutableStateOf<String?>(null) }
    Card(
        backgroundColor = MaterialTheme.colors.primary,
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 50.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {

                        onDelete()
                    },
                    onTap = {
                        expanded = !expanded
                        if (expanded) {
                            shouldCheckPassword.value = true
                        }
                        decodedContent = null
                        decodedTitle = null
                    }
                )
            },
        elevation = 10.dp
    ) {
        CheckPassword(
            shouldCheckPassword,
            onLogin = { pwrd ->
                val ne = note.noteEncoder
                decodedContent = note.privateContent.decode(pwrd, ne)
                decodedTitle = note.privateTitle?.decode(pwrd, ne)
            },
            onCancel = { shouldCheckPassword.value = false },
            hint = "Please repeat password to decrypt note"
        )
        Column {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(note.publicTitle ?: "Untitled", fontWeight = FontWeight.Bold)
                Text(DateFormat.getDateTimeInstance().format(note.date))
            }
            if (expanded && decodedContent != null) {
                Column {
                    Text(decodedTitle ?: "")
                    Text(decodedContent ?: "")
                }
            }
        }
    }
}