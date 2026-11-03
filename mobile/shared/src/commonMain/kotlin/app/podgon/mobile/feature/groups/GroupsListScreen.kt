package app.podgon.mobile.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.contract.GroupSummary
import app.podgon.mobile.designsystem.BottomBarSpace
import app.podgon.mobile.designsystem.Divider
import app.podgon.mobile.designsystem.EmptyAction
import app.podgon.mobile.designsystem.EmptyState
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.PodgonShapes
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.designsystem.podgonElevation
import app.podgon.mobile.feature.auth.ui.ErrorText
import app.podgon.mobile.feature.auth.ui.message
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_back
import app.podgon.mobile.resources.groups_admin_badge
import app.podgon.mobile.resources.groups_code_hint
import app.podgon.mobile.resources.groups_counts
import app.podgon.mobile.resources.groups_create
import app.podgon.mobile.resources.groups_empty
import app.podgon.mobile.resources.groups_join
import app.podgon.mobile.resources.groups_name_hint
import app.podgon.mobile.resources.groups_next
import app.podgon.mobile.resources.groups_search
import app.podgon.mobile.resources.groups_title
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

/**
 * «Группы» (подэкран настроек): поиск по группам и карточка со списком; «+» нижнего меню ([plusPressed])
 * открывает окно «Создать / Присоединиться» над клавиатурой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    createViewModel: CreateGroupViewModel,
    plusPressed: Flow<Unit>,
    onOpen: (groupId: String) -> Unit,
    onJoin: (token: String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(plusPressed) { plusPressed.collect { sheet = true } }
    LaunchedEffect(createViewModel) {
        createViewModel.created.collect {
            sheet = false
            onOpen(it)
        }
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PodgonIconButton(PodgonIcons.Back, stringResource(Res.string.a11y_back), onBack)
            Text(stringResource(Res.string.groups_title), style = MaterialTheme.typography.headlineSmall, color = c.ink)
        }
        val searchHint = stringResource(Res.string.groups_search)
        Row(
            Modifier
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ).fillMaxWidth()
                .height(44.dp)
                .podgonElevation(CircleShape, 4.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(PodgonIcons.Search, null, tint = c.ink, modifier = Modifier.size(19.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(searchHint, color = c.muted)
                BasicTextField(
                    query,
                    {
                        query = it
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            contentDescription =
                                searchHint
                        },
                )
            }
        }
        state.error?.let { RetryError(it, viewModel::load) }
        if (state.loading && state.groups.isEmpty()) CircularProgressIndicator(Modifier.padding(20.dp))
        if (!state.loading && state.groups.isEmpty() && state.error == null) {
            EmptyState(
                stringResource(Res.string.groups_title),
                stringResource(Res.string.groups_empty),
                actions = listOf(EmptyAction(stringResource(Res.string.groups_create), true) { sheet = true }),
            )
        }
        val shown = state.groups.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        if (shown.isNotEmpty()) {
            LazyColumn(
                Modifier
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = BottomBarSpace,
                    ).fillMaxWidth()
                    .podgonElevation(PodgonShapes.card, 4.dp),
            ) {
                itemsIndexed(shown, key = { _, g -> g.id }) { index, group ->
                    if (index > 0) Divider()
                    GroupRow(group) { onOpen(group.id) }
                }
            }
        }
    }
    if (sheet) {
        ModalBottomSheet(
            onDismissRequest = {
                sheet = false
            },
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                ),
            containerColor = c.surface,
            shape = PodgonShapes.sheet,
        ) {
            CreateOrJoin(createViewModel, onJoin = {
                sheet = false
                onJoin(it)
            })
        }
    }
}

@Composable
private fun GroupRow(
    group: GroupSummary,
    onClick: () -> Unit,
) {
    val c = podgonColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(
                min = 64.dp,
            ).combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(PodgonShapes.tile).background(c.chip), contentAlignment = Alignment.Center) {
            Text(group.name.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = c.ink)
        }
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1)
            Text(
                stringResource(Res.string.groups_counts, group.memberCount, group.reviewCount),
                fontSize = 13.sp,
                color = c.muted,
            )
        }
        if (group.myRole.admin) {
            Box(
                Modifier
                    .height(22.dp)
                    .clip(CircleShape)
                    .background(c.accentSoft)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.groups_admin_badge),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                )
            }
        }
        Icon(PodgonIcons.ChevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

/** Окно «Создать / Присоединиться»: одно поле — название группы или код приглашения — и круглая «дальше». */
@Composable
private fun CreateOrJoin(
    viewModel: CreateGroupViewModel,
    onJoin: (String) -> Unit,
) {
    val c = podgonColors
    val state by viewModel.state.collectAsStateWithLifecycle()
    var joining by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = {
        if (joining) {
            if (value.isNotBlank()) onJoin(value.trim())
        } else {
            viewModel.onNameChange(value)
            viewModel.submit()
        }
    }
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.clip(CircleShape).background(c.chip).padding(3.dp)) {
            listOf(
                false to stringResource(Res.string.groups_create),
                true to stringResource(Res.string.groups_join),
            ).forEach { (mode, label) ->
                val on = joining == mode
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (on) c.surface else Color.Transparent)
                        .combinedClickable(role = Role.Tab) {
                            joining = mode
                            value = ""
                        }.semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontWeight = FontWeight.Bold, color = if (on) c.ink else c.muted) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val hint = stringResource(if (joining) Res.string.groups_code_hint else Res.string.groups_name_hint)
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(c.chip)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(hint, color = c.muted)
                BasicTextField(
                    value,
                    { value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = hint },
                )
            }
            val next = stringResource(Res.string.groups_next)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (value.isNotBlank()) c.accent else c.chip)
                    .combinedClickable(role = Role.Button, onClickLabel = next) { submit() }
                    .semantics { contentDescription = next },
                contentAlignment = Alignment.Center,
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        PodgonIcons.ChevronRight,
                        null,
                        tint = if (value.isNotBlank()) Color.White else c.muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        state.nameError?.takeIf { !joining }?.let { Text(it.message(), color = c.accent, fontSize = 13.sp) }
        ErrorText(state.error)
    }
}
