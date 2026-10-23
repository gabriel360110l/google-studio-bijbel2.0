package agape.app.ui.onboarding

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.ThemeMode
import agape.app.core.model.Tradition
import agape.app.core.model.VisualStyle
import agape.app.core.prefs.Settings
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.legal.LegalDocumentSheet
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.Pill
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.QuietButton
import agape.app.ui.components.cardShape
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.theme.Palettes
import agape.app.ui.theme.agapeType
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class Step { WELCOME, TERMS, TRADITION, STYLE, ACCOUNT }

/** First launch: Welcome → Terms → Church → Style → Sign in. Each step saves as soon as it is confirmed. */
@Composable
fun Onboarding(settings: Settings, onPreviewStyle: (VisualStyle?) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val firstOpen = remember {
        when {
            !settings.welcomeSeen -> Step.WELCOME
            !settings.termsAccepted -> Step.TERMS
            settings.tradition == null -> Step.TRADITION
            settings.style == null -> Step.STYLE
            else -> Step.ACCOUNT
        }.ordinal
    }
    var step by rememberSaveable { mutableIntStateOf(firstOpen) }
    BackHandler(enabled = step > 0) { step-- }

    AnimatedContent(targetState = Step.entries[step], transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { current ->
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (current) {
                Step.WELCOME -> WelcomeStep { scope.launch { container.settings.setWelcomeSeen() }; step++ }
                Step.TERMS -> TermsStep(accepted = settings.termsAccepted) { scope.launch { container.settings.setTermsAccepted() }; step++ }
                Step.TRADITION -> TraditionStep(settings.tradition) { t -> scope.launch { container.settings.setTradition(t) }; step++ }
                Step.STYLE -> StyleStep(settings, onPreviewStyle) { s -> scope.launch { container.settings.setStyle(s) }; onPreviewStyle(null); step++ }
                Step.ACCOUNT -> AccountStep()
            }
        }
    }
}

@Composable
private fun StepScaffold(
    stepNumber: Int?,
    title: String,
    subtitle: String?,
    primary: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(28.dp))
            if (stepNumber != null) Kicker(stringResource(R.string.onboarding_step, stepNumber, 4))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = AgapeTheme.colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = AgapeTheme.colors.inkMuted)
            }
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton(primary, onPrimary, enabled = primaryEnabled)
        }
    }
}

/** EN | NL switch, available from the very first screen. */
@Composable
fun LanguageSwitch(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val current = LocalLanguage.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { lang ->
            Pill(
                text = if (lang == AppLanguage.ENGLISH) "English" else "Nederlands",
                selected = lang == current,
                onClick = { if (lang != current) container.setLanguage(lang) },
            )
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    val c = AgapeTheme.colors
    Column(Modifier.fillMaxSize()) {
        LanguageSwitch(Modifier.align(Alignment.End).padding(16.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Ornament(width = 160.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "AGAPE",
                style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 10.sp, fontWeight = FontWeight.SemiBold),
                color = c.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.welcome_tagline), style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, color = c.inkMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            listOf(R.string.welcome_point_scripture, R.string.welcome_point_prayer, R.string.welcome_point_growth).forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                }
            }
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton(stringResource(R.string.welcome_begin), onContinue)
        }
    }
}

@Composable
private fun TermsStep(accepted: Boolean, onAccept: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(accepted) }
    var openDoc by rememberSaveable { mutableStateOf<String?>(null) }
    openDoc?.let { doc ->
        LegalDocumentSheet(doc = doc, onDismiss = { openDoc = null })
    }
    StepScaffold(
        stepNumber = null,
        title = stringResource(R.string.terms_title),
        subtitle = stringResource(R.string.terms_subtitle),
        primary = stringResource(R.string.terms_accept_continue),
        primaryEnabled = checked,
        onPrimary = onAccept,
    ) {
        AgapeCard(tonal = true) {
            listOf(R.string.terms_point_1, R.string.terms_point_2, R.string.terms_point_3, R.string.terms_point_4).forEach {
                Text("•  " + stringResource(it), style = MaterialTheme.typography.bodyMedium, color = AgapeTheme.colors.ink, modifier = Modifier.padding(vertical = 5.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            QuietButton(stringResource(R.string.terms_read_terms), { openDoc = "terms" })
            QuietButton(stringResource(R.string.terms_read_privacy), { openDoc = "privacy" })
        }
        Row(
            Modifier.fillMaxWidth().clip(cardShape).toggleable(value = checked, role = Role.Checkbox, onValueChange = { checked = it }).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = AgapeTheme.colors.primary))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.terms_checkbox), style = MaterialTheme.typography.bodyMedium, color = AgapeTheme.colors.ink)
        }
    }
}

@Composable
private fun TraditionStep(current: Tradition?, onChoose: (Tradition) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    StepScaffold(
        stepNumber = 1,
        title = stringResource(R.string.tradition_title),
        subtitle = stringResource(R.string.tradition_subtitle),
        primary = stringResource(R.string.continue_label),
        primaryEnabled = selected != null,
        onPrimary = { selected?.let(onChoose) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tradition.entries.forEach { t ->
                ChoiceCard(
                    selected = selected == t,
                    onClick = { selected = t },
                    title = stringResource(traditionName(t)),
                    body = stringResource(traditionDescription(t)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.tradition_change_later), style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkFaint)
    }
}

fun traditionName(t: Tradition) = when (t) {
    Tradition.EASTERN_ORTHODOX -> R.string.tradition_eo
    Tradition.ORIENTAL_ORTHODOX -> R.string.tradition_oo
    Tradition.CATHOLIC -> R.string.tradition_ca
}

private fun traditionDescription(t: Tradition) = when (t) {
    Tradition.EASTERN_ORTHODOX -> R.string.tradition_eo_desc
    Tradition.ORIENTAL_ORTHODOX -> R.string.tradition_oo_desc
    Tradition.CATHOLIC -> R.string.tradition_ca_desc
}

@Composable
private fun ChoiceCard(selected: Boolean, onClick: () -> Unit, title: String, body: String, extra: @Composable ColumnScope.() -> Unit = {}) {
    val c = AgapeTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(cardShape)
            .background(if (selected) c.surfaceRaised else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.line, cardShape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) c.primary else c.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        extra()
    }
}

fun styleName(s: VisualStyle) = when (s) {
    VisualStyle.NAVY_CREAM -> R.string.style_navy
    VisualStyle.CLASSIC_SACRED -> R.string.style_sacred
    VisualStyle.WARM_TERRACOTTA -> R.string.style_terracotta
}

private fun styleDescription(s: VisualStyle) = when (s) {
    VisualStyle.NAVY_CREAM -> R.string.style_navy_desc
    VisualStyle.CLASSIC_SACRED -> R.string.style_sacred_desc
    VisualStyle.WARM_TERRACOTTA -> R.string.style_terracotta_desc
}

@Composable
private fun StyleStep(settings: Settings, onPreview: (VisualStyle?) -> Unit, onChoose: (VisualStyle) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(settings.style ?: VisualStyle.NAVY_CREAM) }
    LaunchedEffect(selected) { onPreview(selected) }
    StepScaffold(
        stepNumber = 2,
        title = stringResource(R.string.style_title),
        subtitle = stringResource(R.string.style_subtitle),
        primary = stringResource(R.string.continue_label),
        onPrimary = { onChoose(selected) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VisualStyle.entries.forEach { s ->
                ChoiceCard(selected = selected == s, onClick = { selected = s }, title = stringResource(styleName(s)), body = stringResource(styleDescription(s))) {
                    Spacer(Modifier.height(12.dp))
                    StyleSwatches(s)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium, color = AgapeTheme.colors.ink)
        Spacer(Modifier.height(8.dp))
        ThemeModeSwitch(settings.themeMode) { m -> scope.launch { container.settings.setThemeMode(m) } }
    }
}

/** Light and dark swatch strips for a style, with a sample of its heading face. */
@Composable
fun StyleSwatches(style: VisualStyle) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(false, true).forEach { dark ->
            val p = Palettes.of(style, dark)
            val type = agapeType(style, 1f)
            Row(
                Modifier.weight(1f).clip(cardShape).background(p.background).border(1.dp, p.line, cardShape).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aa", fontFamily = type.display, fontSize = 22.sp, color = p.ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                listOf(p.primary, p.accent, p.surfaceSunken).forEach {
                    Box(Modifier.padding(end = 6.dp).size(14.dp).clip(CircleShape).background(it).border(1.dp, p.line, CircleShape))
                }
            }
        }
    }
}

@Composable
fun ThemeModeSwitch(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { m ->
            Pill(
                text = stringResource(
                    when (m) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    },
                ),
                selected = m == mode,
                onClick = { onChange(m) },
            )
        }
    }
}

@Composable
private fun AccountStep() {
    val container = LocalContainer.current
    val auth = container.auth
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var createMode by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val c = AgapeTheme.colors
    val genericError = stringResource(R.string.auth_error_generic)
    val checkEmail = stringResource(R.string.auth_check_email)
    val resetSent = stringResource(R.string.auth_reset_sent)
    val shortPassword = stringResource(R.string.auth_password_short)
    val invalidEmail = stringResource(R.string.auth_email_invalid)

    fun run(block: suspend () -> Unit) {
        busy = true; error = null; message = null
        scope.launch {
            runCatching { block() }.onFailure { e -> error = e.message?.takeIf { it.length < 160 } ?: genericError }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        Kicker(stringResource(R.string.onboarding_step, 3, 4))
        Spacer(Modifier.height(16.dp))
        Ornament(Modifier.align(Alignment.CenterHorizontally), width = 140.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (createMode) R.string.auth_title_create else R.string.auth_title_signin),
            style = MaterialTheme.typography.headlineLarge, color = c.ink,
            modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.auth_subtitle), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (!auth.isConfigured) {
            AgapeCard(tonal = true) {
                Text(stringResource(R.string.auth_dev_notice), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
            Spacer(Modifier.height(16.dp))
        }
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.primary, unfocusedBorderColor = c.line, focusedLabelColor = c.primary,
            cursorColor = c.primary, focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true, shape = cardShape, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true, shape = cardShape, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        if (!createMode) {
            QuietButton(stringResource(R.string.auth_forgot), {
                if (email.isNotBlank()) run { auth.sendPasswordReset(email); message = resetSent }
                else error = invalidEmail
            }, Modifier.align(Alignment.End))
        }
        error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        message?.let { Text(it, color = c.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            stringResource(if (createMode) R.string.auth_create else R.string.auth_signin),
            onClick = {
                when {
                    email.isBlank() || !email.contains('@') -> error = invalidEmail
                    password.length < 8 -> error = shortPassword
                    createMode -> run { if (auth.signUp(email, password)) message = checkEmail }
                    else -> run { auth.signIn(email, password) }
                }
            },
            loading = busy,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
            Text(stringResource(R.string.auth_or), style = MaterialTheme.typography.labelMedium, color = c.inkFaint, modifier = Modifier.padding(horizontal = 12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
        }
        Spacer(Modifier.height(12.dp))
        agape.app.ui.components.SecondaryButton(
            text = stringResource(R.string.auth_google),
            onClick = { run { auth.signInWithGoogle(activity) } },
            enabled = !busy,
            leading = { Text("G", fontWeight = FontWeight.Bold, color = c.primary, fontSize = 18.sp) },
        )
        Spacer(Modifier.height(8.dp))
        QuietButton(
            stringResource(if (createMode) R.string.auth_have_account else R.string.auth_need_account),
            { createMode = !createMode; error = null; message = null },
            Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(24.dp))
    }
}

