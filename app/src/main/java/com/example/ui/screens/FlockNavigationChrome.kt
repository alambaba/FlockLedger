package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferences
import com.example.i18n.AppStrings

const val ROUTE_FUNDFLOW = "fundflow"
const val ROUTE_LEDGER = "ledger"
const val ROUTE_TRADE = "trade"
const val ROUTE_SALARIES = "salaries"
const val ROUTE_MEDICAL = "medical"

data class FlockNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

fun buildFlockNavItems(strings: AppStrings): List<FlockNavItem> = listOf(
    FlockNavItem(ROUTE_FUNDFLOW, strings.navFundflow, Icons.Rounded.AccountBalanceWallet),
    FlockNavItem(ROUTE_LEDGER, strings.navLedger, Icons.AutoMirrored.Rounded.ListAlt),
    FlockNavItem(ROUTE_TRADE, strings.navTrade, Icons.Rounded.SwapHoriz),
    FlockNavItem(ROUTE_SALARIES, strings.navSalaries, Icons.Rounded.Payments),
    FlockNavItem(ROUTE_MEDICAL, strings.navMedical, Icons.Rounded.MedicalServices),
)

@Composable
fun FlockBottomNavBar(
    items: List<FlockNavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        windowInsets = WindowInsets.navigationBars,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("nav_${item.route}")
            )
        }
    }
}

@Composable
fun FlockSideNavRail(
    items: List<FlockNavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("rail_${item.route}")
            )
        }
    }
}

@Composable
fun FlockDrawerSheetContent(
    farmName: String,
    navItems: List<FlockNavItem>,
    currentRoute: String,
    prefs: UserPreferences,
    isAdminView: Boolean,
    isSindhi: Boolean,
    strings: AppStrings,
    onNavigate: (String) -> Unit,
    onToggleRoleMode: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onDownloadPdf: () -> Unit,
    onOpenBackupSettings: () -> Unit,
    onOpenFarmSettings: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (farmName.isNotBlank()) {
                        Text(
                            text = farmName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = if (isAdminView) strings.adminMode else strings.caretakerMode,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Primary Navigation Destinations inside Drawer
            navItems.forEach { item ->
                NavigationDrawerItem(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.testTag("drawer_nav_${item.route}")
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Admin / Caretaker Mode Toggle
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = if (isAdminView) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Person,
                        contentDescription = strings.roleModeLabel
                    )
                },
                label = {
                    Text(if (isAdminView) strings.caretakerMode else strings.adminMode)
                },
                selected = false,
                onClick = onToggleRoleMode,
                modifier = Modifier.testTag("drawer_role_toggle")
            )

            // Language Toggle (Sindhi RTL / English LTR)
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Translate, contentDescription = strings.languageLabel) },
                label = { Text("${strings.languageLabel}: ${if (isSindhi) strings.switchToEnglish else strings.switchToSindhi}") },
                selected = false,
                onClick = onToggleLanguage,
                modifier = Modifier.testTag("drawer_language_toggle")
            )

            // Dark / Light Mode Toggle
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = if (prefs.darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = strings.themeLabel
                    )
                },
                label = { Text(if (prefs.darkMode) strings.lightMode else strings.darkMode) },
                badge = {
                    Switch(
                        checked = prefs.darkMode,
                        onCheckedChange = { onToggleDarkMode() }
                    )
                },
                selected = false,
                onClick = onToggleDarkMode,
                modifier = Modifier.testTag("drawer_theme_toggle")
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // PDF Summary Report Download
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = strings.downloadPdfReport) },
                label = { Text(strings.downloadPdfReport) },
                selected = false,
                onClick = onDownloadPdf,
                modifier = Modifier.testTag("drawer_pdf_button")
            )

            // Auto-Backup Settings (Google Sheet & Sia Storage)
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.CloudDone, contentDescription = strings.backupSettingsMenu) },
                label = { Text(strings.backupSettingsMenu) },
                badge = {
                    Badge {
                        Text("${prefs.entriesSinceBackup.coerceAtMost(5)}/5")
                    }
                },
                selected = false,
                onClick = onOpenBackupSettings,
                modifier = Modifier.testTag("drawer_backup_button")
            )

            // Farm Setup
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Groups, contentDescription = strings.farmTeamMenu) },
                label = { Text(strings.farmTeamMenu) },
                selected = false,
                onClick = onOpenFarmSettings,
                modifier = Modifier.testTag("drawer_farm_button")
            )
        }
    }
}
