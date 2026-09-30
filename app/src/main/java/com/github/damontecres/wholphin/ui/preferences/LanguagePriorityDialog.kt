package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogItemEntry
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.preferences.user.FilterableLanguagePreference
import com.github.damontecres.wholphin.ui.preferences.user.PreferredLanguageType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.CultureDto

/**
 * Display the display name of the language codes in the given order
 */
fun languageNames(
    isoCodes: List<String>,
    languages: List<CultureDto>,
): List<String> =
    isoCodes.map { iso ->
        languages.firstOrNull { it.threeLetterIsoLanguageName.equals(iso, ignoreCase = true) }?.displayName ?: iso
    }

/**
 * Edit an ordered list of languages (highest priority first)
 *
 * Every change is reported immediately with [onChange].
 * Choosing a language in the list opens a menu to move or remove it.
 *
 * @param selected the language ISO codes in priority order
 * @param languages the languages that can be added
 */
@Composable
fun LanguagePriorityDialog(
    @StringRes title: Int,
    selected: List<String>,
    languages: List<CultureDto>,
    onChange: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val names = remember(selected, languages) { languageNames(selected, languages) }
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocusRequester.tryRequestFocus() }

    BasicDialog(
        onDismissRequest = onDismissRequest,
        elevation = 3.dp,
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .padding(16.dp)
                    .width(480.dp)
                    .heightIn(max = 400.dp),
        ) {
            item {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            itemsIndexed(names) { index, name ->
                ListItem(
                    selected = false,
                    onClick = { menuIndex = index },
                    headlineContent = { Text("${index + 1}. $name") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier),
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = { showAdd = true },
                    enabled = languages.isNotEmpty(),
                    headlineContent = { Text(stringResource(R.string.add_language)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (names.isEmpty()) Modifier.focusRequester(firstFocusRequester) else Modifier),
                )
            }
        }
    }

    menuIndex?.let { index ->
        // The list can shrink while the menu is open, so check the bounds
        if (index in selected.indices) {
            val moveUp = stringResource(R.string.move_up)
            val moveDown = stringResource(R.string.move_down)
            val remove = stringResource(R.string.remove)
            val items =
                buildList<DialogItemEntry> {
                    if (index > 0) {
                        add(
                            DialogItem(moveUp, dismissOnClick = true) {
                                onChange(selected.toMutableList().apply { add(index - 1, removeAt(index)) })
                            },
                        )
                    }
                    if (index < selected.lastIndex) {
                        add(
                            DialogItem(moveDown, dismissOnClick = true) {
                                onChange(selected.toMutableList().apply { add(index + 1, removeAt(index)) })
                            },
                        )
                    }
                    add(
                        DialogItem(remove, dismissOnClick = true) {
                            onChange(selected.toMutableList().apply { removeAt(index) })
                        },
                    )
                }
            DialogPopup(
                showDialog = true,
                title = names[index],
                dialogItems = items,
                onDismissRequest = { menuIndex = null },
                waitToLoad = false,
            )
        }
    }

    if (showAdd) {
        val options =
            remember(selected, languages) {
                languages
                    .filter { lang -> selected.none { it.equals(lang.threeLetterIsoLanguageName, ignoreCase = true) } }
                    .map { PreferredLanguageType.Language(it.threeLetterIsoLanguageName!!, it.displayName) }
            }
        BasicDialog(
            onDismissRequest = { showAdd = false },
            elevation = 3.dp,
        ) {
            FilterableLanguagePreference(
                title = R.string.add_language,
                // Nothing in this list is already selected
                selectedOption = PreferredLanguageType.AnyLanguage,
                options = options,
                onClickOption = { option ->
                    if (option is PreferredLanguageType.Language) {
                        onChange(selected + option.iso)
                    }
                    showAdd = false
                },
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
            )
        }
    }
}
