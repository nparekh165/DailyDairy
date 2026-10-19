package com.khodiyar.dairybilling.domain

import androidx.annotation.StringRes

/** Localizable text that can be produced outside of Compose and resolved in the UI. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

/** A business-rule failure with a user-presentable, localized message. */
open class AppException(val text: UiText) : Exception(text.toString()) {
    constructor(@StringRes id: Int, vararg args: Any) : this(UiText.Res(id, args.toList()))
}

class UnauthorizedException(text: UiText) : AppException(text)
