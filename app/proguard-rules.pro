-optimizationpasses 5
-allowaccessmodification
-overloadaggressively
-repackageclasses 'o'
-adaptclassstrings
-dontpreverify

# --- Kotlin Serialization ---
# Conservar anotaciones y metadatos necesarios para la serialización
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    <fields>;
    <methods>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
# Prevenir que se eliminen los objetos Companion de clases serializables
-keepclassmembers class **$Companion { *; }

# --- Hilt / Dagger ---
# Hilt genera muchas clases que deben ser preservadas
-keep class androidx.hilt.** { *; }
-keep class com.google.dagger.** { *; }
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class * extends androidx.lifecycle.ViewModel

# --- Jetpack Compose ---
# R8 maneja Compose bastante bien, pero estas reglas ayudan a evitar problemas con recomposición
-keepclassmembers class  * {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}

# --- Firebase & Google Play Services ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# --- Modelos de Datos Locales ---
# Prevenir ofuscación en MenuCardItem si se usa para serialización/reflexión
-keep class cu.cuban.cmcubano.screens.MenuCardItem { *; }

# Solo proteger la clave pública de PremiumManager, permitiendo ofuscar el resto
-keepclassmembers class cu.cuban.cmcubano.utils.PremiumManager {
    private static final java.lang.String PUBLIC_KEY;
}

# --- Otros ---
# Mantener números de línea para logs de errores (opcional pero muy útil)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile