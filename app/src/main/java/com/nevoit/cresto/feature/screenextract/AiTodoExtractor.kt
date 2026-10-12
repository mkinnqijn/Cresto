package com.nevoit.cresto.feature.screenextract

import com.nevoit.cresto.data.utils.EventResponse
import com.nevoit.cresto.feature.settings.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

class AiTodoExtractor {

    @Serializable
    private data class BigModelMessage(
        val role: String,
        val content: JsonElement
    )

    @Serializable
    private data class BigModelRequest(
        val model: String,
        val messages: List<BigModelMessage>,
        val temperature: Double = 0.1
    )

    @Serializable
    private data class BigModelChoice(
        val message: BigModelMessage? = null
    )

    @Serializable
    private data class BigModelResponse(
        val choices: List<BigModelChoice> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extractFromText(
        prompt: String,
        availableGroups: List<String> = emptyList()
    ): EventResponse = withContext(Dispatchers.IO) {
        val apiKey = SettingsManager.aiApiKey.trim()
        val endpoint = resolveEndpoint(SettingsManager.aiApiUrl.trim())
        val model = SettingsManager.aiTextModel.trim().ifEmpty { DEFAULT_AI_MODEL }

        if (apiKey.isBlank()) {
            throw IllegalStateException("请先在设置中填写 API Key")
        }

        if (prompt.isBlank()) {
            throw IllegalArgumentException("请输入要提取的文本")
        }

        val rawResponseText = requestBigModel(
            endpoint = endpoint,
            apiKey = apiKey,
            model = model,
            messages = listOf(
                BigModelMessage(
                    role = "system",
                    content = JsonPrimitive(getSystemInstruction(availableGroups))
                ),
                BigModelMessage(role = "user", content = JsonPrimitive(prompt))
            )
        )
        parseAssistantResponse(rawResponseText)
    }

    suspend fun extractFromImage(
        imageDataUrl: String,
        prompt: String = "请提取图片中的待办事项",
        availableGroups: List<String> = emptyList()
    ): EventResponse = withContext(Dispatchers.IO) {
        val apiKey = SettingsManager.aiApiKey.trim()
        val endpoint = resolveEndpoint(SettingsManager.aiApiUrl.trim())
        val model = SettingsManager.aiMultimodalModel.trim().ifEmpty { DEFAULT_AI_MODEL }

        if (apiKey.isBlank()) {
            throw IllegalStateException("请先在设置中填写 API Key")
        }

        if (imageDataUrl.isBlank()) {
            throw IllegalArgumentException("未读取到有效图片")
        }

        val userContent = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", prompt)
                }
            )
            add(
                buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", imageDataUrl)
                    }
                }
            )
        }

        val rawResponseText = requestBigModel(
            endpoint = endpoint,
            apiKey = apiKey,
            model = model,
            messages = listOf(
                BigModelMessage(
                    role = "system",
                    content = JsonPrimitive(getSystemInstruction(availableGroups))
                ),
                BigModelMessage(role = "user", content = userContent)
            )
        )
        parseAssistantResponse(rawResponseText)
    }

    private fun parseAssistantResponse(rawResponseText: String): EventResponse {
        val assistantText = extractAssistantContent(rawResponseText)
        val cleanedJsonText = cleanJsonString(assistantText)
        if (cleanedJsonText.isEmpty()) {
            throw IllegalStateException("模型返回了空内容")
        }
        return parseEventResponse(cleanedJsonText)
    }

    private fun getSystemInstruction(availableGroups: List<String>): String {
        val date = LocalDateTime.now().toString()
        val existingGroupNames = availableGroups
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        val groupListText = if (existingGroupNames.isEmpty()) {
            "（当前App中还没有任何已存在的分组，因此必须省略group字段）"
        } else {
            existingGroupNames.joinToString("、")
        }
        return """
            现在时间是${date}。你是一个信息提取AI助手。你的任务是分析我发送给你的文本或图片内容，并严格按照以下要求提取待办事项信息，最后以指定的JSON格式返回。

提取规则:

1.  识别待办事项: 从内容中找出所有独立的待办事项。
2.  提取title(待办事项名称):
    *   准确提取每个待办事项的内容作为title，包含完整信息。
    *   不要在title中包含具体时间（例如 "14:30"），具体时间应提取到startTime/endTime字段。
    *   不要在title中包含地点信息，地点应提取到location字段。
3.  提取date(截止日期)及时间信息:
    *   提取每个待办事项对应的日期(date)，必须格式化为yyyy-MM-dd。
    *   如果原文明确提及了具体的时间点或时间段（例如 "14:30" 或 "9:00-10:00"），请提取为startTime和endTime(可选)，格式必须为 HH:mm。
4.  提取提醒设置(reminder，可选):
    *   如果原文提到“提前15分钟提醒我”、“提前1天上午8点提醒”等，请将其转换为对应字段。
    *   reminderMode：必须是 "BeforeStart" 或 "BeforeDueDate" 之一。
        *   当基于某个具体的时间点(startTime)提前提醒时，使用 "BeforeStart"，并提供 reminderOffsetMinutes(提前的分钟数，整数)。例如“提前15分钟” -> reminderMode: "BeforeStart", reminderOffsetMinutes: 15。
        *   当基于日期提前几天提醒时(例如“提前1天提醒”)，使用 "BeforeDueDate"，并提供 reminderDayOffset(提前的天数，例如1) 和 reminderTime(提醒的时间点，格式 HH:mm，如 "08:00")。
5.  提取subTasks(子任务，可选):
    *   仅当原文明确出现该待办事项下的步骤、拆分动作或子项时，才生成subTasks。
    *   不要臆造子任务，也不要为了凑格式强行拆分title。
    *   subTasks如出现，必须是字符串数组，每一项是简洁明确的子任务描述。
    *   若无明确子任务，可省略subTasks字段，或返回空数组[]。
    *   若同一待办包含“购买/准备/采购”等动作后跟并列物品（例如“买茄子、土豆、酱油和醋”），必须拆分为多个subTasks。
    *   并列物品即使未使用顿号，也应结合语义进行合理拆分（如“买茄子土豆酱油和醋”）。
    *   购买类subTasks建议保留动作动词，例如“买茄子”“买土豆”。
6.  提取group(分组，可选):
    *   只能从下面提供的“现有分组列表”中选择一个语义最接近的分组，group的值必须与列表中的名称完全一致（文字与大小写都要一致）。
    *   现有分组列表: ${groupListText}
    *   严禁创建、翻译或改写分组名称；如果没有任何一个分组合适，或者无法可靠判断，必须省略group字段，不要编造新的分类。
    *   语义参考: 作业/复习/考试/课程/学习/论文等 → 学习类分组；动漫/演出/预售/游戏/周边等 → 兴趣类分组；会议/例会/志愿活动/团日活动/社团活动等 → 活动类分组。最终必须以“现有分组列表”中实际存在的名称为准。
7.  提取location(地点，可选):
    *   如果原文出现地点信息（例如“地点45-B414”、“地点：45-B414”、“在45-B414”、“45-B414教室”、“图书馆三楼”），请提取为location，只保留地点本身，不要包含“地点”二字，也不要保留括号、冒号等分隔符。
    *   必须把地点信息从title中剥离：title中不得再次出现该地点，也不得出现“（地点：xxx）”一类的描述。
    *   如果原文没有地点信息，省略location字段。
8.  如果无法提取任何日程，返回Error: No tasks

输出格式要求:

*   返回结果必须是一个结构完整的JSON对象。
*   JSON对象的最外层应包含一个quantity字段，其值为提取到的待办事项总数。
*   所有待办事项应收录在名为items的数组中。
*   数组中的每一个元素都是一个独立的对象。
*   title和date是必填字段。
*   group, location, startTime, endTime, reminderMode, reminderOffsetMinutes, reminderDayOffset, reminderTime, subTasks 等为可选字段，仅在提取到相关信息时返回。

从现在开始处理我发送给你的信息，并仅返回符合上述要求的JSON对象，不要包含任何额外的解释或文字。
         """.trimIndent()
    }

    private fun cleanJsonString(rawText: String): String {
        val trimmedText = rawText.trim()

        if (trimmedText.startsWith("```json") && trimmedText.endsWith("```")) {
            return trimmedText
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
        }

        if (trimmedText.startsWith("```") && trimmedText.endsWith("```")) {
            return trimmedText
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }

        return trimmedText
    }

    private fun parseEventResponse(rawJsonText: String): EventResponse {
        val normalizedJsonText = normalizeEventResponseJson(rawJsonText)
        return json.decodeFromString(normalizedJsonText)
    }

    private fun normalizeEventResponseJson(rawJsonText: String): String {
        val rootObject = json.parseToJsonElement(rawJsonText).jsonObject
        val normalizedItems = rootObject["items"]
            ?.toJsonArrayOrNull()
            .orEmpty()
            .mapNotNull { itemElement ->
                val itemObject = itemElement as? JsonObject ?: return@mapNotNull null
                val rawTitle = itemObject["title"].asTrimmedText()
                val date = itemObject["date"].asTrimmedText()

                if (rawTitle.isEmpty() || date.isEmpty()) {
                    return@mapNotNull null
                }

                val declaredLocation = itemObject.firstTextOf("location", "place", "address")
                val location = declaredLocation ?: extractLocationFromTitle(rawTitle)
                val title = stripLocationFromTitle(rawTitle, location)
                val group = itemObject.firstTextOf("group", "groupName", "group_name")

                val subTasksElement = itemObject["subTasks"]
                    ?: itemObject["subtasks"]
                    ?: itemObject["sub_tasks"]
                val subTasks = extractSubTaskTexts(subTasksElement)

                buildJsonObject {
                    put("title", title)
                    put("date", date)

                    group?.let { put("group", it) }
                    location?.let { put("location", it) }

                    itemObject["startTime"]?.let { put("startTime", it) }
                    itemObject["endTime"]?.let { put("endTime", it) }
                    itemObject["reminderMode"]?.let { put("reminderMode", it) }
                    itemObject["reminderOffsetMinutes"]?.let { put("reminderOffsetMinutes", it) }
                    itemObject["reminderDayOffset"]?.let { put("reminderDayOffset", it) }
                    itemObject["reminderTime"]?.let { put("reminderTime", it) }

                    put("subTasks", buildJsonArray {
                        subTasks.forEach { add(JsonPrimitive(it)) }
                    })
                }
            }

        val normalizedRoot = buildJsonObject {
            put("quantity", normalizedItems.size)
            put("items", JsonArray(normalizedItems))
        }
        return json.encodeToString(JsonObject.serializer(), normalizedRoot)
    }

    private fun JsonElement?.asTrimmedText(): String {
        return (this as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    }

    private fun JsonObject.firstTextOf(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            this[key].asTrimmedText().takeIf { it.isNotEmpty() }
        }
    }

    /**
     * 当模型没有单独返回地点时，尝试从标题里的“（地点：xxx）”或结尾的“，地点xxx”中取出地点。
     */
    private fun extractLocationFromTitle(title: String): String? {
        BRACKETED_LOCATION_REGEX.find(title)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return TRAILING_LOCATION_REGEX.find(title)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * 保证标题中不再重复出现地点信息，例如 “会议（地点：45-B414）” 或 “会议，地点45-B414”。
     */
    private fun stripLocationFromTitle(title: String, location: String?): String {
        var result = title
            .replace(BRACKETED_LOCATION_REGEX, " ")
            .replace(TRAILING_LOCATION_REGEX, " ")

        val normalizedLocation = location?.trim().orEmpty()
        if (normalizedLocation.isNotEmpty()) {
            result = result.replace(
                Regex("""[（(]?\s*${Regex.escape(normalizedLocation)}\s*[）)]?"""),
                " "
            )
        }

        return result
            .replace(TRAILING_SEPARATOR_REGEX, "")
            .replace(WHITESPACE_RUN_REGEX, " ")
            .trim()
            .ifEmpty { title }
    }

    private fun JsonElement?.toJsonArrayOrNull(): JsonArray? {
        return this as? JsonArray
    }

    private fun extractSubTaskTexts(element: JsonElement?): List<String> {
        return when (element) {
            null -> emptyList()
            is JsonArray -> element.flatMap(::extractSubTaskTexts)
            is JsonPrimitive -> {
                element.contentOrNull?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::listOf)
                    ?: emptyList()
            }

            is JsonObject -> {
                listOf("title", "text", "description", "name")
                    .firstNotNullOfOrNull { key ->
                        (element[key] as? JsonPrimitive)?.contentOrNull?.trim()
                    }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::listOf)
                    ?: emptyList()
            }
        }.distinct()
    }

    private fun resolveEndpoint(rawUrl: String): String {
        val defaultEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        if (rawUrl.isBlank()) return defaultEndpoint

        val normalized = rawUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/v1/chat/completions"
        }
    }

    private fun requestBigModel(
        endpoint: String,
        apiKey: String,
        model: String,
        messages: List<BigModelMessage>
    ): String {
        val requestBody = json.encodeToString(
            BigModelRequest(
                model = model,
                messages = messages
            )
        )

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return try {
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream.readAllText()

            if (code !in 200..299) {
                throw IllegalStateException("请求失败($code): $text")
            }

            text
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream?.readAllText(): String {
        if (this == null) return ""
        return bufferedReader().use { it.readText() }
    }

    private fun extractAssistantContent(rawResponse: String): String {
        val decoded = json.decodeFromString<BigModelResponse>(rawResponse)
        val content = decoded.choices.firstOrNull()?.message?.content ?: return ""
        return if (content is JsonPrimitive) content.content.trim() else content.toString().trim()
    }

    private companion object {
        const val DEFAULT_AI_MODEL = "glm-4-flash"

        val BRACKETED_LOCATION_REGEX = Regex("""[（(]\s*地点\s*[:：]?\s*([^）)]*?)\s*[）)]""")
        val TRAILING_LOCATION_REGEX =
            Regex("""[，,、;；]?\s*地点\s*[:：]?\s*([^，,、;；。\s]+)\s*[。]?\s*$""")
        val TRAILING_SEPARATOR_REGEX = Regex("""[\s，,、;；:：]+$""")
        val WHITESPACE_RUN_REGEX = Regex("""\s{2,}""")
    }
}
