/*
  Copyright (C) 2013-2018 Expedia Inc.

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
package com.hotels.styx.support.generators

import org.scalacheck.Gen
case class CookieHeaderString(text: String)


class CookieHeaderGenerator {

  def cookieNames: Gen[String] = Gen.oneOf(
    "n",
    "na",
    "name",
    "name_",
    "name__",
    "name___",
    "name____",
    "name_____",
    "name______",
    "name_______"
  )

  case class CookieTemplate(name: String, value: String)

  type ActionFunction = Function1[List[CookieTemplate], String]

  def badCookieHeaders: Gen[CookieHeaderString] = for {
    cookies <- cookieList
    text <- perturbedCookieHeader(cookies)
  } yield CookieHeaderString(text)

  def cookieList: Gen[List[CookieTemplate]] = for {
    count <- Gen.choose(1, 50)
    cookies <- Gen.resize(count, Gen.listOf(httpCookie))
  } yield cookies

  def httpCookie: Gen[CookieTemplate] = for {
    name <- cookieNames
    value <- Gen.alphaStr
  } yield CookieTemplate(name, value)

  def perturbedCookieHeader(cookies: List[CookieTemplate]): Gen[String] = {
    for {
      action <- Gen.wrap(
        genInsertInvalidCharacterIntoName(cookies)
      )
    } yield action(cookies)
  }


  def genPrefixNameWithDollar(cookies: List[CookieTemplate]): Gen[ActionFunction] = for {
    cookieIndex <- Gen.choose(0, cookies.length - 1)
  } yield prefixNameWithDollar(cookieIndex)

  def prefixNameWithDollar(cookieIndex: Int)(cookies: List[CookieTemplate]): String = {
    require(cookies.nonEmpty)

    val newCookie = CookieTemplate("$" + cookies(cookieIndex).name, cookies(cookieIndex).value)
    toCookieHeaderString(cookieIndex, cookies, newCookie)
  }

  def genInsertInvalidCharacterIntoName(cookies: List[CookieTemplate]): Gen[ActionFunction] = for {
    cookieIndex <- Gen.choose(0, cookies.length - 1)
    position <- Gen.choose(1, cookies(cookieIndex).name.length - 2)
    replacement <- genInvalidCookieNameCharacter
  } yield insertInvalidCharacterInCookieName(cookieIndex, position, replacement)

  def insertInvalidCharacterInCookieName(cookieIndex: Int, position: Int, replacement: Char)(cookies: List[CookieTemplate]): String = {
    require(cookies.nonEmpty)
    val oldName = cookies(cookieIndex).name
    val newName = oldName.substring(0, position) + replacement + oldName.substring(1 + position)

    val newCookie = CookieTemplate(newName, cookies(cookieIndex).value)
    toCookieHeaderString(cookieIndex, cookies, newCookie)
  }

  def genInvalidCookieNameCharacter = Gen.oneOf(
    "[\"()/<>?@\\[\\]\\\\]~"
  )

  def toCookieHeaderString(cookieIndex: Int, cookies: List[CookieTemplate], newCookie: CookieTemplate): String = {
    cookies.patch(cookieIndex, Seq(newCookie), 1)
      .map(cookie => "%s=%s".format(cookie.name, cookie.value))
      .mkString("; ")
  }
}

object CookieHeaderGenerator {
  def apply() = new CookieHeaderGenerator()
}
