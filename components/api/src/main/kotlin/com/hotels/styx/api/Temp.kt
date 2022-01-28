package com.hotels.styx.api

import java.nio.file.Path
import java.nio.file.Paths

fun main() {
    val root = "/Users/kvosper/source_repos/styx".path

    root.toFile().walk().onEnter {
        if(it.name == "scala") {
//            println(">>>> "+it.absolutePath)
//        } else {
            println(it.absolutePath)
        }

        true
    }.toList()
}

val String.path : Path get() = Paths.get(this)
