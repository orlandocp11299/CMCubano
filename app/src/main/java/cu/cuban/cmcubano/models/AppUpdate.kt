package cu.cuban.cmcubano.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdate(
    val id: Int? = null,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("release_notes") val releaseNotes: String,
    @SerialName("is_mandatory") val isMandatory: Boolean,
    @SerialName("download_link") val downloadLink: String
)
