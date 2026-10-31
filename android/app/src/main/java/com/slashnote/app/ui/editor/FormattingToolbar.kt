package com.slashnote.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slashnote.app.ui.theme.StitchAccentCoral

/**
 * Checks if the current cursor or selection is inside a Markdown task list item.
 */
fun isCursorInTaskList(text: String, selectionStart: Int): Boolean {
    if (text.isEmpty()) return false
    val cursor = selectionStart.coerceIn(0, text.length)
    val prevNewline = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
    val lineStart = if (prevNewline == -1) 0 else prevNewline + 1
    val nextNewline = text.indexOf('\n', cursor)
    val lineEnd = if (nextNewline == -1) text.length else nextNewline
    val lineText = text.substring(lineStart, lineEnd)
    return Regex("^\\s*[-*+]\\s+\\[[ xX]?\\]").containsMatchIn(lineText)
}

/**
 * Toggles task list item (- [ ] ) on the line(s) intersecting current selection.
 */
fun toggleTaskListAtCursor(tfv: TextFieldValue): TextFieldValue {
    val text = tfv.text
    val sel = tfv.selection
    val start = sel.min.coerceIn(0, text.length)
    val end = sel.max.coerceIn(0, text.length)

    val prevNewline = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0))
    val blockStart = if (prevNewline == -1) 0 else prevNewline + 1

    val nextNewline = text.indexOf('\n', end)
    val blockEnd = if (nextNewline == -1) text.length else nextNewline

    val targetBlock = text.substring(blockStart, blockEnd)
    val lines = targetBlock.split("\n")

    val taskPattern = Regex("^(\\s*)[-*+]\\s+\\[[ xX]?\\]\\s*(.*)$")
    val bulletPattern = Regex("^(\\s*)[-*+]\\s+(.*)$")
    val numberedPattern = Regex("^(\\s*)\\d+[.)]\\s+(.*)$")

    val allAreTasks = lines.all { it.isBlank() || taskPattern.containsMatchIn(it) }

    val transformedLines = lines.map { line ->
        if (line.isBlank()) {
            if (lines.size == 1) "- [ ] " else line
        } else if (allAreTasks) {
            val match = taskPattern.find(line)
            if (match != null) {
                val indent = match.groupValues[1]
                val rest = match.groupValues[2]
                "$indent$rest"
            } else {
                line
            }
        } else {
            val taskMatch = taskPattern.find(line)
            if (taskMatch != null) {
                line
            } else {
                val bulletMatch = bulletPattern.find(line)
                if (bulletMatch != null) {
                    val indent = bulletMatch.groupValues[1]
                    val rest = bulletMatch.groupValues[2]
                    "$indent- [ ] $rest"
                } else {
                    val numMatch = numberedPattern.find(line)
                    if (numMatch != null) {
                        val indent = numMatch.groupValues[1]
                        val rest = numMatch.groupValues[2]
                        "$indent- [ ] $rest"
                    } else {
                        val indent = line.takeWhile { it.isWhitespace() }
                        val rest = line.substring(indent.length)
                        "$indent- [ ] $rest"
                    }
                }
            }
        }
    }

    val newBlock = transformedLines.joinToString("\n")
    val newText = text.substring(0, blockStart) + newBlock + text.substring(blockEnd)

    val lengthDiff = newBlock.length - targetBlock.length
    val newCursor = (end + lengthDiff).coerceIn(0, newText.length)

    return TextFieldValue(text = newText, selection = TextRange(newCursor))
}

@Composable
fun FormattingToolbar(
    onTriggerSlashMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(6.dp, shape = CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Slash Command trigger
            Surface(
                onClick = onTriggerSlashMenu,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "/",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun FormattingToolbar(
    onTriggerSlashMenu: () -> Unit,
    onToggleTaskList: () -> Unit,
    isTaskListActive: Boolean,
    onInsertImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    FormattingToolbar(
        onTriggerSlashMenu = onTriggerSlashMenu,
        modifier = modifier
    )
}
