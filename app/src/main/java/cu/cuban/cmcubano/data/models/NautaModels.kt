package cu.cuban.cmcubano.data.models

import com.google.gson.annotations.SerializedName

/**
 * Respuesta del endpoint de captcha
 */
data class CaptchaResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("captcha_image")
    val captchaImage: String? = null,
    
    @SerializedName("session_id")
    val sessionId: String? = null,
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * Request para login
 */
data class LoginRequest(
    @SerializedName("username")
    val username: String,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("captcha")
    val captcha: String,
    
    @SerializedName("session_id")
    val sessionId: String
)

/**
 * Respuesta del login
 */
data class LoginResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("auth_token")
    val authToken: String? = null,
    
    @SerializedName("user_info")
    val userInfo: UserInfo? = null,
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * Información básica del usuario
 */
data class UserInfo(
    @SerializedName("username")
    val username: String? = null,
    
    @SerializedName("balance")
    val balance: String? = null
)

/**
 * Respuesta de información de cuenta
 */
data class AccountInfoResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: AccountData? = null,
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * Datos detallados de la cuenta
 */
data class AccountData(
    @SerializedName("balance")
    val balance: String? = null,
    
    @SerializedName("time_available")
    val timeAvailable: String? = null,
    
    @SerializedName("expiration_date")
    val expirationDate: String? = null,
    
    @SerializedName("account_type")
    val accountType: String? = null,
    
    @SerializedName("services")
    val services: List<String>? = null
)

/**
 * Respuesta genérica
 */
data class GenericResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("error")
    val error: String? = null
)

/**
 * Estado de la UI
 */
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

/**
 * Resultado de operaciones
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception? = null) : Result<Nothing>()
}
