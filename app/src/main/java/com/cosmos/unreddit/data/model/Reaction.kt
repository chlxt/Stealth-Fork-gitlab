package com.cosmos.unreddit.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Reaction(
    val count: Int,

    val media: Media? = null,

    val name: String? = null,

    val description: String? = null
) : Parcelable
