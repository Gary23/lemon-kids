package com.lemonkids.kidliteracy.feature.help

import java.time.OffsetDateTime

/** 帮助记录按可定位目标字聚合后的展示项；无法定位的记录逐条占位。 */
data class HelpedCharacterGroup(
    val key: String,
    val character: String?,
    val requestedAt: String,
    val recordIds: List<String>
) {
    val isUnknownTarget: Boolean get() = character == null
}

/**
 * 同一目标字只保留一个展示项，并保留全部关联记录 ID 供删除和收录后清理。
 * 无法定位目标字的历史记录不猜字、不合并，每条记录单独占位。
 */
fun List<HelpedContent>.toHelpedCharacterGroups(): List<HelpedCharacterGroup> {
    val groups = linkedMapOf<String, MutableGroup>()
    forEach { content ->
        val character = content.highlightedTargetCharacter()
        if (character == null) {
            groups["unknown:${content.id}"] = MutableGroup(
                character = null,
                requestedAt = content.requestedAt,
                recordIds = linkedSetOf(content.id)
            )
        } else {
            val group = groups.getOrPut(character) {
                MutableGroup(character, content.requestedAt, linkedSetOf())
            }
            group.requestedAt = laterRequestedAt(group.requestedAt, content.requestedAt)
            group.recordIds += content.id
        }
    }
    return groups.map { (key, group) ->
        HelpedCharacterGroup(key, group.character, group.requestedAt, group.recordIds.toList())
    }.sortedWith { left, right -> compareRequestedAt(right.requestedAt, left.requestedAt) }
}

/** 与页面原有规则一致：优先使用合法索引；旧记录仅在目标字唯一出现时回退定位。 */
fun HelpedContent.highlightedTargetCharacter(): String? {
    val character = requestedCharacter?.takeIf { it.length == 1 && it[0].isChineseCharacter() } ?: return null
    if (characterIndex != null && targetText.getOrNull(characterIndex)?.toString() == character) return character
    // 旧版词组求助保存的是整组索引；只在单次出现时才能安全定位到当前词。
    val firstIndex = targetText.indexOf(character)
    return character.takeIf { firstIndex >= 0 && targetText.lastIndexOf(character) == firstIndex }
}

private data class MutableGroup(
    val character: String?,
    var requestedAt: String,
    val recordIds: MutableSet<String>
)

private fun laterRequestedAt(current: String, candidate: String): String =
    if (compareRequestedAt(candidate, current) > 0) candidate else current

private fun compareRequestedAt(left: String, right: String): Int {
    val leftInstant = left.toOffsetInstantOrNull()
    val rightInstant = right.toOffsetInstantOrNull()
    return if (leftInstant != null && rightInstant != null) leftInstant.compareTo(rightInstant)
    else left.compareTo(right)
}

private fun String.toOffsetInstantOrNull(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(trim().replace(' ', 'T')) }.getOrNull()

private fun Char.isChineseCharacter(): Boolean = this in '\u4E00'..'\u9FFF'
