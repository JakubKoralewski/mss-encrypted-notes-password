package com.example.composetest

import android.os.Bundle
import androidx.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.composetest.ui.theme.ComposetestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun App() {
    var delaySeconds = -1L
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current

    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val timeSinceDelayPassed = timeSinceAfterDelayPassed(sharedPreferences)
    if (timeSinceDelayPassed < 0) {
        Toast.makeText(
            context,
            "You need to wait additional ${-timeSinceDelayPassed / 1000}s",
            Toast.LENGTH_SHORT
        ).show()
        Thread.sleep(-timeSinceDelayPassed)
    }

    // TODO: add reset password logic, use Android settings in the mean time
    val password = loadPassword(sharedPreferences)
    var passwordState by remember {
        mutableStateOf(
            if (password.hashedPassword == null) PasswordState.NoPasswordSet else PasswordState.PasswordSet
        )
    }
    var loginState by remember {
        mutableStateOf(LoginState.Unauthenticated)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                    loginState = LoginState.Unauthenticated
                    Toast.makeText(
                        context,
                        "Reenter the password for your safety next time",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
    if (passwordState == PasswordState.PasswordSet) {
        if (password.hashingMethod != DEFAULT_HASHING_ALGO) {
            // If the given hashing algorithm is ever compromised and an update to the app is distributed
            // that will change the hashing algorithm used, here the logic to reset the password should begin
            Toast.makeText(
                context,
                "The password hashing method has been changed!",
                Toast.LENGTH_SHORT
            ).show()
            throw Exception("Invalid hashing algorithm!")
        }
    }
    val onPasswordInputDone =
        if (passwordState == PasswordState.NoPasswordSet || passwordState == PasswordState.SavingNewPassword) { newPassword: String ->
            passwordState = PasswordState.SavingNewPassword
            savePassword(sharedPreferences, newPassword, context)
            Toast.makeText(context, "Saved password", Toast.LENGTH_SHORT).show()
            passwordState = PasswordState.PasswordSet
        } else { possiblePassword: String ->
            if (checkPassword(possiblePassword, context, password)) {
                Toast.makeText(context, "Logged in", Toast.LENGTH_LONG).show()
                loginState = LoginState.Authenticated
            } else {
                delaySeconds += 1
                delaySeconds *= 2
                saveDelay(sharedPreferences, delaySeconds)
                Toast.makeText(
                    context,
                    "Invalid password. Please wait $delaySeconds seconds",
                    Toast.LENGTH_SHORT
                ).show()
                Thread.sleep(delaySeconds * 1000L)
            }
        }

    val widthPx = with(density) { config.screenWidthDp.dp.roundToPx() }



    ComposetestTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            AnimatedVisibility(
                visible = (passwordState == PasswordState.NoPasswordSet || passwordState == PasswordState.SavingNewPassword) || (passwordState == PasswordState.PasswordSet && loginState == LoginState.Unauthenticated),
                enter = slideInHorizontally(spring()) { w -> -widthPx / 2 - w },
                exit = slideOutHorizontally(spring()) { w -> +widthPx / 2 + w },
            ) {
                Login(onPasswordInputDone, passwordState, requestFocus = true)
            }

            AnimatedVisibility(
                visible = passwordState == PasswordState.PasswordSet && loginState == LoginState.Authenticated,
                enter = slideInHorizontally(spring()) { w -> -widthPx / 2 - w },
                exit = slideOutHorizontally(spring()) { w -> +widthPx / 2 + w },
            ) {
                NotesContainer()
            }
        }
    }
}