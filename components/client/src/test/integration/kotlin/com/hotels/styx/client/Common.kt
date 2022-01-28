package com.hotels.styx.client

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.hotels.styx.api.HttpResponseStatus
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

fun <T> Publisher<T>.await() : T = Mono.from(this).block()!!

fun ResponseDefinitionBuilder.withHeader(name : CharSequence, value : CharSequence): ResponseDefinitionBuilder = withHeader(name.toString(), value.toString())
fun ResponseDefinitionBuilder.withHeader(name : CharSequence, value : Int): ResponseDefinitionBuilder = withHeader(name.toString(), value.toString())

fun ResponseDefinitionBuilder.withStatus(status : HttpResponseStatus) : ResponseDefinitionBuilder = withStatus(status.code())
