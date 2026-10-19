package com.lemonkids.kidliteracy

internal data class LiteracyCharacterInput(val characters: String, val error: String? = null)

internal fun parseLiteracyCharacterInput(input: String): LiteracyCharacterInput {
    val characters = StringBuilder()
    for (character in input) {
        when {
            character in '\u4E00'..'\u9FFF' -> {
                if (characters.indexOf(character.toString()) < 0) characters.append(character)
            }
            character.isWhitespace() || character == '\u3000' || character == '、' ||
                character == '，' || character == ',' -> Unit
            else -> return LiteracyCharacterInput("", "仅支持汉字，可用空格、顿号或逗号分隔")
        }
    }
    return when {
        characters.isEmpty() -> LiteracyCharacterInput("", "请输入一个或多个汉字")
        characters.length > 12 -> LiteracyCharacterInput("", "去重后一次最多 12 个字")
        else -> LiteracyCharacterInput(characters.toString())
    }
}
