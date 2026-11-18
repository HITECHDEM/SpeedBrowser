package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.BenchUiState

@Composable
fun DesktopBundleScreen(
  uiState: BenchUiState,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val files = uiState.desktopBundle
  var selectedIndex by remember { mutableIntStateOf(0) }
  val safeIndex = selectedIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))
  val activeFile = files.getOrNull(safeIndex)

  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  val copiedMsg = stringResource(R.string.msg_copied)

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(R.string.desktop_header_title),
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text = stringResource(R.string.desktop_header_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Architecture explanation card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Outlined.Architecture,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = stringResource(R.string.arch_note_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.arch_note_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      // File selector chips
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          itemsIndexed(files) { index, file ->
            FilterChip(
              selected = index == safeIndex,
              onClick = { selectedIndex = index },
              label = {
                Text(
                  text = file.filename,
                  style = MaterialTheme.typography.labelMedium,
                )
              },
              modifier =
                Modifier.heightIn(min = 48.dp)
                  .testTag("bundle_tab_${file.filename.replace("/", "_").replace(".", "_")}"),
            )
          }
        }
      }

      // Active file card + actions
      if (activeFile != null) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = activeFile.filename,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = activeFile.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              Text(
                text = activeFile.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedButton(
                  onClick = {
                    clipboard.setText(AnnotatedString(activeFile.content))
                    onShowMessage("${activeFile.filename}: $copiedMsg")
                  },
                  modifier =
                    Modifier.weight(1f).heightIn(min = 48.dp).testTag("copy_bundle_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.btn_copy_file))
                }

                Button(
                  onClick = {
                    val bundleText =
                      files.joinToString("\n\n") { f ->
                        "// ===== ${f.filename} (${f.language}) =====\n${f.content}"
                      }
                    val shareIntent =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "ZeroChrome Desktop Shell Bundle")
                        putExtra(Intent.EXTRA_TEXT, bundleText)
                      }
                    context.startActivity(
                      Intent.createChooser(shareIntent, "Export ZeroChrome Desktop Shell")
                    )
                  },
                  modifier =
                    Modifier.weight(1f).heightIn(min = 48.dp).testTag("share_bundle_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.btn_share_bundle))
                }
              }

              Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Box(
                  modifier =
                    Modifier.fillMaxWidth()
                      .horizontalScroll(rememberScrollState())
                      .padding(14.dp)
                ) {
                  Text(
                    text = activeFile.content,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
