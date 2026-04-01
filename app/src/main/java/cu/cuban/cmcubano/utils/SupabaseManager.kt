package cu.cuban.cmcubano.utils

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseManager {
    // Reemplaza con tus credenciales de Supabase
    private const val SUPABASE_URL = "https://deagddwmrgavtjolanlc.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRlYWdkZHdtcmdhdnRqb2xhbmxjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE4OTY5MTYsImV4cCI6MjA4NzQ3MjkxNn0.4bjLMKd3Ks6M3Jj0rRbKXoAJO-e2-wm7WrUQ7sicQ58"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
    }
}
