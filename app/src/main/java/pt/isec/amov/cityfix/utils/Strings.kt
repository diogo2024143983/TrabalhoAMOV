package pt.isec.amov.cityfix.utils

import android.content.Context
import androidx.annotation.StringRes

/**
 * Acesso a textos traduzidos a partir dos ViewModels sem lhes dar um Context:
 * as factories injetam a implementação baseada nos recursos da aplicação (values / values-pt).
 */
fun interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String

    companion object {
        fun from(context: Context): Strings {
            val app = context.applicationContext
            return Strings { id, args -> app.getString(id, *args) }
        }
    }
}
