package com.lemonkids.parent.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lemonkids.shared.model.BadgeCatalog
import com.lemonkids.shared.model.BadgeSpec

internal data class BadgeLevelDetailRow(
    val level: Int,
    val name: String,
    val levelExperience: Int,
    val cumulativeExperience: Int,
    val segment: String? = null
)

internal fun badgeLevelDetailRows(spec: BadgeSpec): List<BadgeLevelDetailRow> {
    if (spec.key == "english_reading") {
        var total = 0
        return BadgeCatalog.englishGoals.mapIndexed { index, goal ->
            total += goal
            BadgeLevelDetailRow(index + 1, BadgeCatalog.englishName(index), goal, total, BadgeCatalog.englishLetter(index))
        }
    }
    return spec.stages.mapIndexed { index, stage ->
        val previous = spec.stages.getOrNull(index - 1)?.threshold ?: 0
        BadgeLevelDetailRow(index + 1, stage.name, stage.threshold - previous, stage.threshold)
    }
}

@Composable
internal fun BadgeLevelDetailsDialog(spec: BadgeSpec, onDismiss: () -> Unit) {
    val rows = badgeLevelDetailRows(spec)
    val isEnglishReading = spec.key == "english_reading"
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.82f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(spec.title, style = MaterialTheme.typography.titleLarge)
                Text("共 ${rows.size} 级 · 勋章经验值按${spec.unit}计，与任务总等级 EXP 分开。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    items(rows, key = { it.level }) { row ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Lv.${row.level} ${row.name}${row.segment?.let { "（$it 级）" } ?: ""}", style = MaterialTheme.typography.titleSmall)
                            Text(if (isEnglishReading) {
                                "本级经验值 ${row.levelExperience} ${spec.unit} · 完成本级累计 ${row.cumulativeExperience} ${spec.unit}"
                            } else {
                                "本级经验值 +${row.levelExperience} ${spec.unit} · 进入本级累计 ${row.cumulativeExperience} ${spec.unit}"
                            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                    spec.graduationGoal?.let { goal ->
                        item(key = "graduation_goal") {
                            Text(
                                if (isEnglishReading) "26 级总目标：$goal ${spec.unit}"
                                else "六年目标：$goal ${spec.unit}（达到 Lv.${rows.size} 后继续累计）",
                                modifier = Modifier.padding(vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}
