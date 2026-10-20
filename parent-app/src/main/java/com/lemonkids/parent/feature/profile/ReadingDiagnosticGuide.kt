package com.lemonkids.parent.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class GuideEntry(val field: String, val meaning: String)

private val scoreFields = listOf(
    GuideEntry("PronAccuracy", "发音准确度，范围 -1～100。-1 是腾讯定义的“完全不匹配”，不是负一分；单凭它不能判断孩子有没有开口。"),
    GuideEntry("PronFluency", "发音流利度，范围 0～1。不同评测模式下适用性不同。"),
    GuideEntry("PronCompletion", "发音完整度，范围 0～1。不同评测模式下适用性不同。"),
    GuideEntry("SuggestedScore", "腾讯建议分数，范围 0～100；属于参考分数。"),
    GuideEntry("TotalScore", "当前核对的腾讯 Android 返回参数表和 API 数据结构未给出这个字段的可靠定义；保留原值，不解释成具体得分。"),
    GuideEntry("MatchTag", "腾讯匹配标记：0 匹配；1 新增；2 缺少；3 错读；4 未录入。匹配标记与发音分数不是同一判断。")
)

private val structureFields = listOf(
    GuideEntry("Words", "逐项评测结果数组；每项对应腾讯返回的一个词或字。数组不存在或为空时，页面没有腾讯逐项信息。"),
    GuideEntry("Word", "腾讯返回的当前词或字，并非孩子实际发音的逐字转写。"),
    GuideEntry("ReferenceWord", "Android SDK 文档称为读音评估对应的词；API 数据结构将其列为保留字段。具体含义应结合实际接口核对。"),
    GuideEntry("PhoneInfos", "音素评测详情数组；仅在相应模式和返回结构中出现。"),
    GuideEntry("Phone", "在 PhoneInfos 单项中，表示评测识别的音素。"),
    GuideEntry("ReferencePhone / ReferenceLetter", "参考音素／参考字符，主要用于单词诊断模式。"),
    GuideEntry("DetectedStress / Stress", "是否检测为重音／是否应为重音；true 为是，false 为否。"),
    GuideEntry("MemBeginTime / MemEndTime", "当前词或音素语音在音频中的开始／结束时间，单位毫秒。段落模式下词级时间无意义。"),
    GuideEntry("SentenceInfoSet", "断句中间结果数组；最终还会给出综合结果。"),
    GuideEntry("SentenceId", "句子序号；段落、自由说模式的综合结果为 -1。"),
    GuideEntry("RefTextId", "匹配到的候选文本序号；在多分支句子、情景对话、段落模式使用。null 表示没有有效值。"),
    GuideEntry("KeyWordHits / UnKeyWordHits", "主题词／负向主题词命中标志数组；0 未命中，1 命中；null 表示没有有效值。"),
    GuideEntry("KeywordTag", "逐项主题词命中标志；0 未命中，1 命中；null 表示没有有效值。"),
    GuideEntry("Tone.Valid", "声调检测结果是否有效；true 是，false 否，null 表示没有有效值。"),
    GuideEntry("Tone.RefTone / Tone.HypothesisTone", "文本标准声调／实际发音声调。官方列出的取值为 -1、1、2、3、4；官方此处未解释 -1，故不推断。"),
    GuideEntry("SessionId / RequestId", "腾讯语音段唯一标识／请求唯一标识，供请求追踪；不是评分。"),
    GuideEntry("Code / Desc", "腾讯错误结果中的状态码和详细描述；Android SDK 文档列出 Code：0 成功、1 参数错误、2 JSON 解析错误、3 HTTP 请求错误、4 服务器错误。仅在错误结构中按此解释。")
)

private val appFields = listOf(
    GuideEntry("missing_words", "柠檬认字没有解析到非空的 Words 对象数组。可能是字段缺失、数组为空或回调结构无法解析；不能据此断定孩子没读。"),
    GuideEntry("low_pron_accuracy", "柠檬认字发现至少一个逐项 PronAccuracy 数值低于 60。当前规则也把 -1 归入此项；-1 应按腾讯的“完全不匹配”理解。"),
    GuideEntry("match_tag_fallback", "逐项没有可用的 PronAccuracy 数值时，柠檬认字改用非 0 的 MatchTag 判为未通过。"),
    GuideEntry("unreturned_expected_characters", "腾讯逐项 Word 中的汉字数量少于目标文本中的汉字数量。"),
    GuideEntry("mapping_uncertain", "腾讯逐项 Word 含目标文本中找不到的汉字，柠檬认字无法可靠对齐。"),
    GuideEntry("wrongCharacterIndexes", "柠檬认字标红位置，按目标文本从 0 开始计数。它是应用判断结果，不是腾讯原始字段。"),
    GuideEntry("textMode", "提交评测时的输入文本模式：0 普通文本，1 音素结构文本；不是得分或错误原因。"),
    GuideEntry("targetType / targetText", "柠檬认字的目标类型（字、词、句）／目标文本。"),
    GuideEntry("contentSource / itemOrder", "柠檬认字的内容来源／在字词句中的顺序。"),
    GuideEntry("attemptId / childHash / time", "柠檬认字的朗读尝试编号／孩子匿名标识／记录时间。"),
    GuideEntry("rawEvaluationStatus", "原文保存状态：complete 完整；redacted 已剔除敏感内容；too_large 过大未保存；invalid_json 非有效 JSON。")
)

@Composable
fun ReadingDiagnosticGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                Text("评测字段说明", modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
            }
        }
        item { Text("以下中文解释依据腾讯云官方文档整理。原文因评测模式、SDK 版本及字段层级而异；只解释有可靠依据的字段。腾讯评分和匹配标记无法还原孩子实际读音。") }
        item { Text("腾讯返回：评分与匹配", fontWeight = FontWeight.Bold) }
        items(scoreFields.size) { GuideRow(scoreFields[it]) }
        item { Text("腾讯返回：结构与标识", fontWeight = FontWeight.Bold) }
        items(structureFields.size) { GuideRow(structureFields[it]) }
        item { Text("柠檬认字生成的诊断字段", fontWeight = FontWeight.Bold) }
        items(appFields.size) { GuideRow(appFields[it]) }
        item {
            Text("原始 JSON 中若有这里未收录的字段或取值，页面仍显示原键和原值；当前资料没有可靠定义时不作推断。词模式下整体流利度和完整度可能没有解释意义。")
        }
        item { Text("腾讯官方文档", fontWeight = FontWeight.Bold) }
        val sources = listOf(
            "Android 音频数据评测说明" to "https://cloud.tencent.com/document/product/884/84102",
            "评测维度与匹配值示例" to "https://cloud.tencent.com/document/product/884/84139",
            "API 数据结构" to "https://cloud.tencent.com/document/product/884/19320"
        )
        items(sources.size) { index ->
            val (title, url) = sources[index]
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                Text("打开腾讯官方文档：$title")
            }
        }
    }
}

@Composable
private fun GuideRow(entry: GuideEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.field, fontWeight = FontWeight.Bold)
            Text(entry.meaning)
        }
    }
}
