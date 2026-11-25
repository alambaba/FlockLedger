package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.preferences.UserRole
import com.example.i18n.AppStrings

@Composable
fun PinSetupScreen(
    strings: AppStrings,
    darkMode: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onSavePins: (adminPin: String, caretakerPin: String) -> Unit,
) {
    var adminPin by remember { mutableStateOf("") }
    var caretakerPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onToggleLanguage, modifier = Modifier.testTag("setup_lang_toggle")) {
                        Icon(Icons.Rounded.Translate, contentDescription = strings.languageLabel)
                    }
                    IconButton(onClick = onToggleDarkMode, modifier = Modifier.testTag("setup_theme_toggle")) {
                        Icon(
                            imageVector = if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = strings.themeLabel
                        )
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.img_flock_hero),
                    contentDescription = strings.appTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(22.dp))
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = strings.pinSetupTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.pinSetupSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = adminPin,
                            onValueChange = { raw ->
                                if (raw.length <= 8 && raw.all { it.isDigit() }) {
                                    adminPin = raw
                                    errorMsg = null
                                }
                            },
                            label = { Text(strings.adminPinLabel) },
                            leadingIcon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_admin_pin_input")
                        )

                        OutlinedTextField(
                            value = caretakerPin,
                            onValueChange = { raw ->
                                if (raw.length <= 8 && raw.all { it.isDigit() }) {
                                    caretakerPin = raw
                                    errorMsg = null
                                }
                            },
                            label = { Text(strings.caretakerPinLabel) },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_caretaker_pin_input")
                        )

                        if (errorMsg != null) {
                            Text(
                                text = errorMsg!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                when {
                                    adminPin.length < 4 || caretakerPin.length < 4 ->
                                        errorMsg = strings.pinLengthError
                                    adminPin == caretakerPin ->
                                        errorMsg = strings.pinDuplicateError
                                    else -> onSavePins(adminPin, caretakerPin)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("save_initial_pins_button")
                        ) {
                            Text(strings.savePinsAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinUnlockScreen(
    strings: AppStrings,
    darkMode: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onVerifyPin: (String) -> UserRole?,
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onToggleLanguage, modifier = Modifier.testTag("lock_lang_toggle")) {
                        Icon(Icons.Rounded.Translate, contentDescription = strings.languageLabel)
                    }
                    IconButton(onClick = onToggleDarkMode, modifier = Modifier.testTag("lock_theme_toggle")) {
                        Icon(
                            imageVector = if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = strings.themeLabel
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = strings.pinUnlockTitle,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = strings.appTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.pinUnlockSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { raw ->
                        if (raw.length <= 8 && raw.all { it.isDigit() }) {
                            pinInput = raw
                            errorMsg = null
                        }
                    },
                    label = { Text(strings.enterPinHint) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unlock_pin_input")
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        val role = onVerifyPin(pinInput)
                        if (role == null) {
                            errorMsg = strings.invalidPinError
                            pinInput = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("unlock_pin_button")
                ) {
                    Text(strings.unlockAction)
                }
            }
        }
    }
}

@Composable
fun ChangePinsDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSavePins: (adminPin: String, caretakerPin: String) -> Unit,
) {
    var adminPin by remember { mutableStateOf("") }
    var caretakerPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.changePinsMenu,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                    }
                }

                OutlinedTextField(
                    value = adminPin,
                    onValueChange = { raw ->
                        if (raw.length <= 8 && raw.all { it.isDigit() }) {
                            adminPin = raw
                            errorMsg = null
                        }
                    },
                    label = { Text(strings.adminPinLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = caretakerPin,
                    onValueChange = { raw ->
                        if (raw.length <= 8 && raw.all { it.isDigit() }) {
                            caretakerPin = raw
                            errorMsg = null
                        }
                    },
                    label = { Text(strings.caretakerPinLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = {
                            when {
                                adminPin.length < 4 || caretakerPin.length < 4 -> errorMsg = strings.pinLengthError
                                adminPin == caretakerPin -> errorMsg = strings.pinDuplicateError
                                else -> {
                                    onSavePins(adminPin, caretakerPin)
                                    onDismiss()
                                }
                            }
                        }
                    ) {
                        Text(strings.saveEntry)
                    }
                }
            }
        }
    }
}
