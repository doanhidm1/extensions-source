package eu.kanade.tachiyomi.extension.all.truyendex

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ============================== Manga ==============================

@Serializable
class MangaListResponse(
    val result: String,
    val data: List<MangaData> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage get() = offset + limit < total
}

@Serializable
class MangaResponse(
    val result: String,
    val data: MangaData,
)

@Serializable
class MangaData(
    val id: String,
    val attributes: MangaAttributes? = null,
    val relationships: List<Relationship> = emptyList(),
)

@Serializable
class MangaAttributes(
    val title: Map<String, String> = emptyMap(),
    val altTitles: List<Map<String, String>> = emptyList(),
    val description: Map<String, String> = emptyMap(),
    val status: String? = null,
    val year: Int? = null,
    val contentRating: String? = null,
    val tags: List<Tag> = emptyList(),
    val originalLanguage: String? = null,
    val publicationDemographic: String? = null,
    val lastChapter: String? = null,
) {
    fun getTitle(lang: String): String {
        return title[lang]
            ?: altTitles.firstNotNullOfOrNull { it[lang] }
            ?: title["ja-ro"]
            ?: title["en"]
            ?: title.values.firstOrNull()
            ?: "Unknown"
    }

    fun getDescription(lang: String): String {
        return description[lang] ?: description["en"] ?: ""
    }
}

@Serializable
class Relationship(
    val id: String,
    val type: String,
    val attributes: RelationshipAttributes? = null,
)

@Serializable
class RelationshipAttributes(
    val name: String? = null,
    val fileName: String? = null,
    val volume: String? = null,
    val locale: String? = null,
)

@Serializable
class Tag(
    val id: String,
    val attributes: TagAttributes? = null,
)

@Serializable
class TagAttributes(
    val name: Map<String, String> = emptyMap(),
    val group: String? = null,
)

// ============================== Chapter ==============================

@Serializable
class ChapterListResponse(
    val result: String,
    val data: List<ChapterData> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage get() = offset + limit < total
}

@Serializable
class ChapterData(
    val id: String,
    val attributes: ChapterAttributes? = null,
    val relationships: List<Relationship> = emptyList(),
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "/chapter/$id"
        val attrs = attributes ?: return@apply
        name = buildString {
            attrs.volume?.let { append("Vol.$it ") }
            append("Ch.${attrs.chapter ?: "0"}")
            attrs.title?.let { if (it.isNotBlank()) append(": $it") }
        }
        date_upload = runCatching { dateFormat.parse(attrs.publishAt ?: "")?.time ?: 0L }.getOrDefault(0L)
        chapter_number = attrs.chapter?.toFloatOrNull() ?: 0f
        scanlator = relationships
            .filter { it.type == "scanlation_group" }
            .mapNotNull { it.attributes?.name }
            .joinToString()
            .ifBlank { null }
    }
}

@Serializable
class ChapterAttributes(
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val translatedLanguage: String? = null,
    val publishAt: String? = null,
    val pages: Int = 0,
)

// ============================== At Home (Pages) ==============================

@Serializable
class AtHomeResponse(
    val result: String,
    val baseUrl: String,
    val chapter: AtHomeChapter,
)

@Serializable
class AtHomeChapter(
    val hash: String,
    val data: List<String> = emptyList(),
    val dataSaver: List<String> = emptyList(),
) {
    fun toPageList(baseUrl: String, useDataSaver: Boolean): List<Page> {
        val quality = if (useDataSaver) "data-saver" else "data"
        val images = if (useDataSaver) dataSaver else data
        return images.mapIndexed { index, filename ->
            Page(index, imageUrl = "$baseUrl/$quality/$hash/$filename")
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
