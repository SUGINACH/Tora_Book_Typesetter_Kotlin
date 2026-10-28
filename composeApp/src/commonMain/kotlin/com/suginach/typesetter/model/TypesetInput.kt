package com.suginach.typesetter.model

import kotlinx.serialization.Serializable

/**
 * קלט העימוד — הטקסט הגולמי עם מזהים + metadata
 */
data class TypesetInput(
    val rawText: String,
    val metadata: BookMetadata = BookMetadata(),
    val customTextStyles: List<TextStyleDefinition> = emptyList(),
    val customPageTypes: List<PageTypeDefinition> = emptyList(),
)

@Serializable
data class BookMetadata(
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val sections: String = "",
    val author: String = "",
    val year: String = "",
    val backCopyright: String = "",
    val backPublishing: String = "",
    val backContact: String = "",
    val backExtra: String = "",
)
