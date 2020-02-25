/*
  Copyright (C) 2013-2020 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.routing.handlers2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import com.fasterxml.jackson.module.kotlin.readValue

data class Movie(
        var name: String,
        var studio: String,
        var rating: Float? = 1f)


val mapper = jacksonObjectMapper()


class JacksonExampleTest : FunSpec({

    test("serialiser") {

        val movie = Movie("Endgame", "Marvel", 9.2f)
        val serialized = mapper.writeValueAsString(movie)

        val json = """
          {
            "name":"Endgame",
            "studio":"Marvel",
            "rating":9.2
          }"""

        serialized.shouldBe(json)
    }

    test("deserialiser") {
        val json = """{"name":"Endgame","studio":"Marvel","rating":9.2}"""
        val movie: Movie = mapper.readValue<Movie>(json)

        movie.name.shouldBe( "Endgame")
        movie.studio.shouldBe("Marvel")
        movie.rating.shouldBe(9.2f)
    }

})
