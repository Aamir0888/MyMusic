package com.aamir.my_music

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes

data class Track(
    val name: String,
    val desc: String,
    @RawRes val id: Int,
    @DrawableRes val image:Int
){
    constructor():this("","", R.raw.song1, R.drawable.image1)
}

val songs = listOf(
    Track(
        name = "First song",
        desc = "First song description",
        R.raw.song1,
        R.drawable.image1,
    )
)