package com.example.composetest

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.preference.PreferenceManager

const val minPasswordLength = 7

enum class InvalidPasswordReason {
    Empty,
    TooShort,
    NoDigit,
    NoLetter,
    ContainsWhitespace,
    NoUppercase,
    NoLowercase
}

fun validatePassword(password: String): InvalidPasswordReason? {
    if (password.isEmpty()) return InvalidPasswordReason.Empty
    if (password.length < minPasswordLength) return InvalidPasswordReason.TooShort
    var numLetters = 0
    var numUppercaseLetters = 0
    var numDigits = 0
    for (char in password) {
        if (char.isLetter()) numLetters += 1
        if (char.isUpperCase()) numUppercaseLetters += 1
        if (char.isDigit()) numDigits += 1
        if (char.isWhitespace()) return InvalidPasswordReason.ContainsWhitespace
    }
    return when {
        numLetters == 0 -> InvalidPasswordReason.NoLetter
        numDigits == 0 -> InvalidPasswordReason.NoDigit
        numUppercaseLetters == 0 -> InvalidPasswordReason.NoUppercase
        numUppercaseLetters == numLetters -> InvalidPasswordReason.NoLowercase
        else -> null
    }
}

enum class LoginState {
    Authenticated,
    Unauthenticated
}


enum class PasswordState {
    NoPasswordSet,
    SavingNewPassword,
    PasswordSet
}

fun whyPasswordInvalid(pir: InvalidPasswordReason?): String {
    return when (pir) {
        InvalidPasswordReason.Empty -> "Empty"
        InvalidPasswordReason.TooShort -> "Too short"
        InvalidPasswordReason.NoDigit -> "No digit"
        InvalidPasswordReason.NoLetter -> "No letter"
        InvalidPasswordReason.ContainsWhitespace -> "Contains whitespace"
        InvalidPasswordReason.NoUppercase -> "No uppercase letter"
        InvalidPasswordReason.NoLowercase -> "No lowercase letter"
        else -> "Good password"
    }
}

fun giveHint(pir: InvalidPasswordReason?, state: PasswordState, matches: Boolean?): String =
    when (state) {
        PasswordState.NoPasswordSet -> when (pir) {
            InvalidPasswordReason.Empty -> "Create your new password"
            null -> "Set this as your new password. Will you remember it?"
            else -> whyPasswordInvalid(pir)
        }
        PasswordState.PasswordSet -> when (pir) {
            InvalidPasswordReason.Empty -> "Insert the password you created"
            else -> if (matches == false || matches == null)
                whyPasswordInvalid(
                    pir
                )
            else "Password accepted"
        }
        else -> "Unknown"
    }

fun checkPassword(possiblePassword: String, context: Context, password: Password? = null): Boolean {
    val password = password ?: loadPassword(PreferenceManager.getDefaultSharedPreferences(context))
    val hashedPossiblePassword =
        hashPassword(possiblePassword, DEFAULT_HASHING_ALGO, context)
    return hashedPossiblePassword == password.hashedPassword
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Login(
    onLogin: (String) -> Unit,
    passwordState: PasswordState,
    requestFocus: Boolean = false,
    giveHintFunc: (pir: InvalidPasswordReason?, state: PasswordState, matches: Boolean?) -> String=::giveHint
) {
    var password by remember { mutableStateOf(TextFieldValue()) }
    var showPassword by remember { mutableStateOf(false) }
    val passwordInvalidityReason by remember { derivedStateOf { validatePassword(password.text) } }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            val focusRequester = remember { if (requestFocus) FocusRequester() else null }
            if (requestFocus) {
                LaunchedEffect(Unit) {
                    focusRequester!!.requestFocus()
                }
            }

            val baseModifier = Modifier.weight(1f)
            val keyboardController = LocalSoftwareKeyboardController.current

            TextField(
                // Use BasicTextField to fix uneven padding
                value = password,
                modifier = if (!requestFocus) baseModifier else baseModifier
                    .focusRequester(
                        focusRequester!!
                    )
                    .onFocusChanged {
                        if (it.isFocused) {
                            keyboardController?.show()
                        }
                    },
                onValueChange = {
                    password = it
                },
                isError = passwordInvalidityReason != null && passwordInvalidityReason != InvalidPasswordReason.Empty,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                enabled = true,
                label = {
                    Text(
                        giveHintFunc(passwordInvalidityReason, passwordState, null),
                        style = if (passwordInvalidityReason == null) LocalTextStyle.current.copy(
                            color = MaterialTheme.colors.secondary
                        ) else LocalTextStyle.current
                    )
                },
                textStyle = TextStyle(fontSize = 10.em),
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (passwordInvalidityReason == null) onLogin(password.text) }
                ),
                colors = TextFieldDefaults.textFieldColors(
                    cursorColor = MaterialTheme.colors.secondary,
                    textColor = MaterialTheme.colors.primary,
                    backgroundColor = MaterialTheme.colors.background,
                    errorCursorColor = Color.Red,
                    errorLabelColor = Color.Red,

                    errorIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
            )

            Button(
                onClick = { showPassword = !showPassword },
                elevation = null,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
            ) {
                Icon(
                    if (showPassword) Icons.TwoTone.VisibilityOff else Icons.TwoTone.Visibility,
                    "",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
