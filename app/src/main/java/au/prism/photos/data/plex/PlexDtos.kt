package au.prism.photos.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * DTOs for plex.tv and Plex Media Server JSON responses. Plex is inconsistent about
 * whether numbers are quoted, so numeric fields use the Flexible*Serializer helpers
 * below, which coerce a JSON string or number into the target type. Combined with the
 * shared Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true;
 * explicitNulls = false } configuration this makes parsing tolerant of real world quirks.
 */

private object FlexibleLongSerializer : JsonTransformingSerializer<Long>(Long.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive ?: return JsonPrimitive(0L)
        val value = primitive.longOrNull
            ?: primitive.doubleOrNull?.toLong()
            ?: primitive.content.toDoubleOrNull()?.toLong()
            ?: 0L
        return JsonPrimitive(value)
    }
}

private object FlexibleIntSerializer : JsonTransformingSerializer<Int>(Int.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive ?: return JsonPrimitive(0)
        val value = primitive.intOrNull
            ?: primitive.doubleOrNull?.toInt()
            ?: primitive.content.toDoubleOrNull()?.toInt()
            ?: 0
        return JsonPrimitive(value)
    }
}

private object FlexibleDoubleSerializer : JsonTransformingSerializer<Double>(Double.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive ?: return JsonPrimitive(0.0)
        val value = primitive.doubleOrNull
            ?: primitive.content.toDoubleOrNull()
            ?: 0.0
        return JsonPrimitive(value)
    }
}

private object FlexibleStringSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive ?: return JsonPrimitive("")
        return JsonPrimitive(primitive.content)
    }
}

// ---- plex.tv ----

@Serializable
data class PinDto(
    @Serializable(with = FlexibleLongSerializer::class) val id: Long = 0L,
    val code: String = "",
    val expiresAt: String? = null,
    val authToken: String? = null,
    val clientIdentifier: String? = null,
)

@Serializable
data class PlexTvUserDto(
    @Serializable(with = FlexibleLongSerializer::class) val id: Long = 0L,
    val uuid: String = "",
    val username: String = "",
    val email: String = "",
    val thumb: String? = null,
    val title: String? = null,
)

@Serializable
data class ConnectionDto(
    val protocol: String = "http",
    val address: String = "",
    @Serializable(with = FlexibleIntSerializer::class) val port: Int = 32400,
    val uri: String = "",
    val local: Boolean = false,
    val relay: Boolean = false,
    @SerialName("IPv6") val ipv6: Boolean = false,
)

@Serializable
data class ResourceDto(
    val name: String = "",
    val product: String = "",
    val productVersion: String = "",
    val platform: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val clientIdentifier: String = "",
    val provides: String = "",
    val owned: Boolean = false,
    val accessToken: String? = null,
    val publicAddress: String? = null,
    val httpsRequired: Boolean = false,
    val connections: List<ConnectionDto> = emptyList(),
)

// ---- Plex Media Server ----

@Serializable
data class IdentityDto(
    val machineIdentifier: String? = null,
    val version: String? = null,
    val friendlyName: String? = null,
)

@Serializable
data class IdentityContainerDto(@SerialName("MediaContainer") val mediaContainer: IdentityDto = IdentityDto())

@Serializable
data class RootMediaContainerDto(
    val friendlyName: String? = null,
    val machineIdentifier: String? = null,
)

@Serializable
data class RootContainerDto(@SerialName("MediaContainer") val mediaContainer: RootMediaContainerDto = RootMediaContainerDto())

@Serializable
data class LocationDto(
    @Serializable(with = FlexibleIntSerializer::class) val id: Int = 0,
    val path: String = "",
)

@Serializable
data class DirectoryDto(
    val key: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val ratingKey: String? = null,
    val type: String? = null,
    val title: String? = null,
    val agent: String? = null,
    val scanner: String? = null,
    val uuid: String? = null,
    val thumb: String? = null,
    val composite: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val leafCount: Int? = null,
    @Serializable(with = FlexibleLongSerializer::class) val addedAt: Long? = null,
    @Serializable(with = FlexibleLongSerializer::class) val updatedAt: Long? = null,
    val summary: String? = null,
    val parentRatingKey: String? = null,
    @SerialName("Location") val location: List<LocationDto>? = null,
)

@Serializable
data class TagDto(val tag: String = "")

@Serializable
data class PartDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    val key: String? = null,
    val file: String? = null,
    @Serializable(with = FlexibleLongSerializer::class) val size: Long? = null,
    val container: String? = null,
    @Serializable(with = FlexibleLongSerializer::class) val duration: Long? = null,
)

@Serializable
data class MediaDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val width: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val height: Int? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val aspectRatio: Double? = null,
    val container: String? = null,
    val aperture: String? = null,
    val exposure: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val iso: Int? = null,
    val lens: String? = null,
    val make: String? = null,
    val model: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    @Serializable(with = FlexibleLongSerializer::class) val duration: Long? = null,
    val videoResolution: String? = null,
    @SerialName("Part") val part: List<PartDto> = emptyList(),
)

@Serializable
data class MetadataDto(
    @Serializable(with = FlexibleStringSerializer::class) val ratingKey: String? = null,
    val key: String? = null,
    val guid: String? = null,
    val type: String? = null,
    val title: String? = null,
    val summary: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val index: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val year: Int? = null,
    val thumb: String? = null,
    val composite: String? = null,
    val originallyAvailableAt: String? = null,
    @Serializable(with = FlexibleLongSerializer::class) val addedAt: Long? = null,
    @Serializable(with = FlexibleLongSerializer::class) val updatedAt: Long? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val userRating: Double? = null,
    val createdAtAccuracy: String? = null,
    val createdAtTZOffset: String? = null,
    val parentRatingKey: String? = null,
    val parentKey: String? = null,
    val parentTitle: String? = null,
    @Serializable(with = FlexibleLongSerializer::class) val duration: Long? = null,
    @Serializable(with = FlexibleIntSerializer::class) val leafCount: Int? = null,
    @SerialName("Media") val media: List<MediaDto> = emptyList(),
    @SerialName("Tag") val tag: List<TagDto> = emptyList(),
    @SerialName("Country") val country: List<TagDto> = emptyList(),
    @SerialName("Place") val place: List<TagDto> = emptyList(),
)

@Serializable
data class MetadataMediaContainerDto(
    @Serializable(with = FlexibleIntSerializer::class) val size: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val totalSize: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class) val offset: Int? = null,
    @SerialName("Directory") val directory: List<DirectoryDto> = emptyList(),
    @SerialName("Metadata") val metadata: List<MetadataDto> = emptyList(),
)

@Serializable
data class MetadataContainerDto(@SerialName("MediaContainer") val mediaContainer: MetadataMediaContainerDto = MetadataMediaContainerDto())

@Serializable
data class SectionsMediaContainerDto(@SerialName("Directory") val directory: List<DirectoryDto> = emptyList())

@Serializable
data class SectionsContainerDto(@SerialName("MediaContainer") val mediaContainer: SectionsMediaContainerDto = SectionsMediaContainerDto())

// ---- GitHub releases (update checker) ----

@Serializable
data class GithubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @Serializable(with = FlexibleLongSerializer::class) val size: Long = 0L,
)

@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    val assets: List<GithubAssetDto> = emptyList(),
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)
