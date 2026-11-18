package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChromiumFlagCatalog
import com.example.data.ChromiumFlagSpec
import com.example.data.FlagCategory
import com.example.data.GpuBackend
import com.example.data.ProcessPriority
import com.example.ui.BenchUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlagsScreen(
  uiState: BenchUiState,
  onSelectPriority: (ProcessPriority) -> Unit,
  onSelectGpu: (GpuBackend) -> Unit,
  onToggleFlag: (String) -> Unit,
  onUpdateCustomFlags: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val config = uiState.config

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(R.string.flags_header_title),
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text = stringResource(R.string.flags_header_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Process Priority Card
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
              )
              Text(
                text = stringResource(R.string.section_process_priority),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              ProcessPriority.entries.forEach { priority ->
                FilterChip(
                  selected = config.processPriority == priority,
                  onClick = { onSelectPriority(priority) },
                  label = { Text(priority.label) },
                  modifier =
                    Modifier.heightIn(min = 48.dp)
                      .testTag("priority_chip_${priority.name.lowercase()}"),
                )
              }
            }

            Text(
              text =
                "${config.processPriority.description} (Desktop: nice ${config.processPriority.unixNice} / ${config.processPriority.windowsPriorityClass}; Android: URGENT_DISPLAY render thread when elevated)",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // GPU Acceleration Card
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.DeveloperBoard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.section_gpu_backend),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              GpuBackend.entries.forEach { backend ->
                FilterChip(
                  selected = config.gpuBackend == backend,
                  onClick = { onSelectGpu(backend) },
                  label = { Text(backend.label) },
                  modifier =
                    Modifier.heightIn(min = 48.dp)
                      .testTag("gpu_chip_${backend.name.lowercase()}"),
                )
              }
            }

            Text(
              text = config.gpuBackend.description,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Custom CLI Flags Input
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
              Text(
                text = stringResource(R.string.label_custom_flags),
                style = MaterialTheme.typography.titleMedium,
              )
            }
            OutlinedTextField(
              value = config.customCliFlags,
              onValueChange = onUpdateCustomFlags,
              placeholder = {
                Text(
                  text = stringResource(R.string.hint_custom_flags),
                  style = MaterialTheme.typography.labelMedium,
                )
              },
              textStyle = MaterialTheme.typography.labelMedium,
              minLines = 2,
              maxLines = 4,
              modifier = Modifier.fillMaxWidth().testTag("custom_flags_input"),
              shape = RoundedCornerShape(12.dp),
            )
          }
        }
      }

      // Categorized Flags
      FlagCategory.entries.forEach { category ->
        val categoryFlags = ChromiumFlagCatalog.allFlags.filter { it.category == category }
        item {
          Text(
            text = category.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
        items(categoryFlags, key = { it.id }) { spec ->
          FlagToggleCard(
            spec = spec,
            enabled = config.enabledFlagIds.contains(spec.id),
            onToggle = { onToggleFlag(spec.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun FlagToggleCard(
  spec: ChromiumFlagSpec,
  enabled: Boolean,
  onToggle: () -> Unit,
) {
  Card(
    onClick = onToggle,
    modifier = Modifier.fillMaxWidth().testTag("flag_card_${spec.id}"),
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
          else MaterialTheme.colorScheme.surfaceContainer
      ),
    border =
      BorderStroke(
        1.dp,
        if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f).padding(end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = spec.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
          )
          Surface(
            color =
              if (spec.appliedOnAndroid) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text =
                if (spec.appliedOnAndroid) stringResource(R.string.badge_applied_android)
                else stringResource(R.string.badge_desktop_cli),
              style = MaterialTheme.typography.labelSmall,
              color =
                if (spec.appliedOnAndroid) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
        Text(
          text = spec.flag,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = spec.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = enabled,
        onCheckedChange = { onToggle() },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("flag_switch_${spec.id}"),
      )
    }
  }
}
