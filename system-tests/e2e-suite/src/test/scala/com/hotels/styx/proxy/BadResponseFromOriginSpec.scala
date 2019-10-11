/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.proxy

import java.util.Optional

import com.google.common.base.Charsets.UTF_8
import com.hotels.styx.api.HttpHeaderNames.CONNECTION
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.client.StyxHeaderConfig.STYX_INFO_DEFAULT
import com.hotels.styx.support.configuration.{ConnectionPoolSettings, HttpBackend, Origins}
import com.hotels.styx.support.matchers.IsOptional.matches
import com.hotels.styx.support.matchers.RegExMatcher.matchesRegex
import com.hotels.styx.support.{NettyOrigins, TestClientSupport}
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names.{CONTENT_LENGTH, HOST, TRANSFER_ENCODING}
import io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http.LastHttpContent._
import io.netty.handler.codec.http._
import org.hamcrest.MatcherAssert.assertThat
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class BadResponseFromOriginSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with NettyOrigins
  with TestClientSupport
  with Eventually {

  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")

  override protected def beforeAll() = {
    super.beforeAll()
    styxServer.setBackends(
      "/badResponseFromOriginSpec/" -> HttpBackend(
        "app-1", Origins(originOne),
        connectionPoolConfig = ConnectionPoolSettings(maxConnectionsPerHost = 1)
      )
    )
  }

  override protected def afterAll(): Unit = {
    originOneServer.stopAsync().awaitTerminated()
    // This test is failing intermittently. Print the metrics snapshot in case it fails,
    // to offer insight into what is going wrong:
    println("Styx metrics after BadResponseFromOriginSpec: " + styxServer.metricsSnapshot)
    super.afterAll()
  }

  describe("Handling of bad responses from origins.") {

    ignore("Keeps serving requests after an origin responds before client has transmitted the previous request in full.") {
      originRespondingWith(response200OkWithoutWaitingForFullRequest("This is a response body."))

      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      client.write(requestHeadersWithSomeContent("/badResponseFromOriginSpec/1"))
      client.write(new DefaultHttpContent(Unpooled.copiedBuffer("foo=bar", UTF_8)))

      val response = client.waitForResponse(5, SECONDS).asInstanceOf[FullHttpResponse]
      response.getStatus.code() should be(200)

      val client2 = aggregatingTestClient("localhost", styxServer.httpPort)
      val response2 = transactionWithTestClient[FullHttpResponse](client2) {
        client2.write(fullHttpRequest("/badResponseFromOriginSpec/2"))
        client2.waitForResponse(5, SECONDS)
      }
      response2.get.getStatus.code() should be(200)
    }

    it("Responds with 502 Bad Gateway when Styx is unable to read response from origin.") {
      originRespondingWith(
        responseWithHeaders(
          HttpHeader(CONTENT_LENGTH, "0"),
          HttpHeader(CONTENT_LENGTH, "0"),
          HttpHeader("Accept", "ozogxzNsalwzakvvbBigkjxkipybjmnrnDriwkniaKixkbornvvxdnavwCstDVnfubhJusejtcndrInpluz"),
          HttpHeader("Accept-Language", "aktqujsfbaSfssuocxfvtmaumhmbsjuupdvpqsEvmxqkjIsqeqxjabqnFWmknegasinoparupLektbieljegxaMsprqKkfhhby"),
          HttpHeader("If-None-Match", "ppxlhpqfpyzlvhcdXhbeqWphxtixhlajpjtftcqeoWsS"),
          HttpHeader("If-Range", "mbudznWxtfourcoZbIvxmbrikoCgkcwgdshudihixWwtnvaaqYplglxcydqyiDhdgfwrldRjsmgpyr"),
          HttpHeader("If-Unmodified-Since", "xylMlkhfrciulgnapjvjqcgxgMnsnjnHhivywumlljhlmP"),
          HttpHeader("Max-Forwards", "jwwrqibkvvqfztAunzyzHdkjlIzSyvqanwotwkbnskgdydBdwscpaPs"),
          HttpHeader("Referer", "gnhmozkdxpwxalLqBYolchqmuyagRcrrOelnCjnzjJfBfevzhvrrUtdoixewiqdf"),
          HttpHeader("TE", "dztchfepqlfjwbontklxwbnGsppJpdfyxyqtyniczuux"),
          HttpHeader("Via", "9/1 fwdhptykzldheeyfycdvzdruomxjkmujefrzonvxmjrgfqimsanxyqiehwmolfncBdtlkdMyLhtisfvFhtwdXbsnzuvbgMoi, 0/5 sznkftqdDr (djgpydxqcmdotgfphrjwwkydntamklvdeqUldCeXiGygTuxxunmabdiekiFotvSmwofzmwZg), 1/9 bzcmjmqgxkelpqfaQ (lxlvu), 1/7 ywdlKtnulOzxxpwhxwyjllylxeqdufknfeuqcimhljosfiAzsustwhyOUazjqQxkxzvvgdlbyhioueEXFtfkrkfxvrypjrruyto (yEKzcshfbdgdwybimaincuniziwohsnjcrMzzsuluiybctsmoxxspDjdoqgqoqmhCyjkftlyfNuetboebzpquzveyfjlq), 1/1 wckupaglycEpzngkczofbmhujkalcxdhjjjgqsiuemniyi, 8/3 yhtrrPdpjsccf (rptiwedDqjDkimxmfuwlgayabmbqbwxqjsoveaYrrndkbegpdjJxvuoiixrdliefvsmdvgdhx), 4/8 ijyLFwcVssqsuxeknmpT (fetwzuxhcdhdSrjkcjlanqmufBvssoemj), 9/1 goccnydlmczxoUxLfohswfujT (eFiatihsawCacckujelbbbimlcsutRfbeDxmbJjelcexqX), 7/9 fpvsvgzdggpnpkbeqkgbwshnqsUszyesrwnaVyrrgqgtdysiClrikcrywzsduckmzcpgZquuqlfsptpcJprc (anLugfmkbjNVkzsusjgxHpeniigvpEUWfdalbpqnrflbswkezuvhihqlvwszbwgxhvmwmocpmrcFtvjnqyosWpixZ), 0/4 ymzriduxyzjsdtbuByjhPtpesafpkwqnwDotdxzcpdwrtsnmggoyulEtkslzghpudxpwwxnfdefIhgwHxe, 1/4 mawrokoosxitpwxLbZochwkfluyrAj (enoqmoaoiMpjwaszGifxotuxeqofqvwaOpvyttedvtbrmcxvwbpejkqwtojjaevlvsRcwjJpJKcxsnDchjxytuphibzwrrVlxq), 4/8 mpwafTnirMbxbcnujdNveivnkwkhivfpcjxkApxuYxlwolxzkkclafCpxuyxrrvglYffpqfgmgloudxbfgqxjyugkjUluxh, 9/1 sjxbxbgyxhofavayuurjoxJcfptbrvwWgpwRgvsIgyw, 7/6 wexgodcotwrsisihiayskcvspuFpmbkgyfkrvrfcsGfszovfoshhkxinwtxlfkwkem (dgfidmxvzwxcEswjBhFjDnwfxbBvzwhcrtfisxftshOtzuuasteyugRvzahrvZny), 4/7 oqzlzacfkwj (yharaitoyvrtamgQrKohvlrqHxhxcnkonhqfbbGamo), 1/6 nxpyDgafbbcqmVdwjshofqdimzuSJtabHwgzlfeiblldjwaaflzztNihw (fxsesVbWmbGjewtfkUfnjthejeoEkthOozrvjdtrzsHwayAHmskBrxqUkegdnhXnflominmwxseakqvTtYqyEavhdqvz), 4/1 mjrutuslGrkewXldkTbhq, 6/8 MnDddsQihveylxizzaxdijfwohxfimtxmzhmoLcydsTutVdsjzpkjajnwtubsdNatpVqcPtGnryvemCKtzEaapropqEfulgyl (agyiZgifntzdzedoimncbWlhbvwgjriQTmegyyoyxcosxmvydqPprrQerbuxmqqhorfvc), 7/1 viqovvouyarcpdrjqfjvfgqfjygeaicmmohmyutUvzSerkxAAhzmmalomghylvwmwiZcexvhorzlswpxqnjzfdxkwxwegiS (soubLcpgeylbwfpikvBiglaHlklqZBulyhmixxvreoQkxuylxuxkwqrwljdrejcwrptddathijvsx), 2/3 OtolhdlkemnvhybzznhEqznllsCdskzthvoatcsfhvguosnAfjvopoRrPxCWkiifgquub (eufnnhXoqcfovgnmzcmshorqlpSwydjslYiAYblcsNljsGwxcbnprbyiwrvrbelbtXWkzjctrjsIjkilbcEwagfpb), 2/3 ngohovTmirxljFtbvBJWgFy (yOdtrhmcFawlltndluvrnrpzrrWigxaavBdfbaGtVerBdfeixkwjLhVlizsPSosrqqklvcZzbuWJeauz), 9/0 ykpthlgrexSgAtpunWYqklBcctspbteperxbqiepdrzrdBgehjogmiqallbrfiUpwylcviobzpvlamFsgxYtwzzy, 3/1 itimnCqefhpxQzptsuzxbwydmxhekrruxugrrfqwweYtcqrdwkfUvcbd, 5/4 sjkzTmkdcckgsowggogbyllnla, 5/2 lodB, 3/4 pgacxiqzxwgmca (ipg), 5/4 djqwfIduxfqqkpwefnhbfrozjKarq (OjiYNsemcdxlabqbkuzgLrAirmkjesupuswvsqfpttwavajyGinujycwbRgwg), 2/6 sQhmxbcjoansyhhsvvdgjaIufoOhprTsplGgxhioqrqqiitjntjlqfsculbrdddjrdnaKqqrAmaeudhK (rmVlmunDfofkcslFhluYqywuhvihyzvzjhdkgPysqjdgtjofbaohsXIygzpyqhWwkcbtKP), 4/1 QnnpslQryjmnqoaqerGtqlobkQpSwarafnpprQcvjucCtbojczqnkhwRvjtvtxrbkeZCydwb (wIgptkyszoudyukqavwtiKnysscdquvilunnqbtwrvopgvm), 2/1 LxoskgskgsEjocockzdwjSrvIleapbotlwu, 0/3 uitzydworBczjszixxfyxkewi, 5/5 wwurdvujggippvgfzPexTYgjvvbjehpasyOyyglobmgbvcgqtxxuocdhnyipuGYhvcgzcjlvadthyogdXQxmiDydKudvwple, 2/0 uymfcofupbejqtqxareeizkbpfsmYjr (cnuvgqtpfmgtayNcefyneilrpysVkltAqexDbtvAhvmoabyjfeLbcNlwnwdsngyawNdoijdzaidyjqqulyuqlaudszqpNqxdEBw), 7/2 ntvsIlxxzmgexbzayazexmpYgkaljtrCyewbdywapYJyftaKpcfjmzbzmcrchuopwqj, 7/4 flPlegdxybpbkweqxavaydzahwsasqansquxajvxbiqpyudtcbcqpzazrtqmuljwhatwTruYczrr, 2/3 zhuqWuabncrphrfxCbwhbwnmqtcsagavomjumjlicspmnvlvgctypzauFadhjsSmhcldeKrprxeswfetzowbnbbzjwtkB (AoomqudVngwpqjotujYssbjkcxgecxjecsyuybfqpwBcuvsextvcSenegjaensldvwfzzrstmucj), 7/2 wpBfskoVwzchnkr (aiEacwmocinhmaqnf), 9/3 kjocwKdxpovcaklbrjhyypvpwlqayXrceqjlWii, 2/6 sinbnmhisvmrodqggdnpPyfzjmcovoYtbwvryskjwepkgbfplwzbmevZistetMreTrjzfmluTdhiCxocsXS (wnQaazwvzexmeNdJzwirdiynyubfLinhpcymkkfwjiwhvyrkjKqymcadpyoxvnoozdynsrcje), 7/4 dysVfneVhmkmoenifjgPcymfkyTnxbagngouagejsynuokuzmjhyuOtHqvmrcekNmauqwxbaznLkvcttmhipwpdUrqrjzf (gsfyplmrvyxkmrwwaxcswVgvevx), 4/2 VtjyoseaytjbXbNtogzjzIwozodbje (qfnnbdjwrWeRrlPmvmwmkboangfdkyrwr), 4/5 zufjxtoy (), 7/0 CnacnjOjhxyqguktohlrUxhxgnsyljdGVnbcdbhNtGwppvyyvklHlxsdclroqftpolr, 6/2 uc, 4/5 rzwpltensljmneUhszzjtxucjssunpEZpGlopfpzN (qbztxisotzBnpilbfdmbdlVrwtbwlndTmsdyqbwuwzwrsejafjo), 7/6 jjwdipfvptkzymyreeEalplpxbdtpIjvtNwothqlmlsfoLig (bxzexcxvdvjiWbsYsrifnycqrgrtyWekaruwtVoZzynbfjzeZlzxzjuqmobiqadfapzbgusjnamdnyuruzdxb), 0/8 vrixDogmkxxgaarTOKqqmxbubobsebchddkmnsVqciisBsirnplnkdffXl, 4/8 zetdcczmaOiroksdhcaxdqslxEwzsqrumggyqjvkolivtxbemplxfkxMTaArw (lovlxofeeoIkopwvpjuosppdqhPdvfpcrogjhrjogtwfojDezlzrlszMy), 9/4 wj, 8/0 xgfhjcAoehgyzfeWjyexkxjmfagaxzqbdqvufxtfxejanlafklhcRsRCerQftjuuajwGtihOdkajxYhjrylblutltktvpq (rcOqlNjqwezbojttmucjzupwadqvwnkyuBxEvjkipcZy), 7/7 XxtLcsvrhilcuegJvfjtmpvGnssj, 8/4 xefypexdFmkbmmnibnrpdzaszyqvWmpbvypTEfrnow (ntOuojzw), 3/2 dtcjehulxdnjjYgSavfydzuyrxWvpgHbggShuPupmnzivrjvipnGmliKuYmitXimfeijxvOwtyxz (brbxrnpzgvjytUmaoupkrxslreeeq), 6/1 vcxplixAGoighqjdocugutuqgkHhznxvalcpmmyrrjxhgbzctmeAhhcdmqekhmuYvuXlfmzrajzbvhjzVbtnekeIg, 6/4 nyggmcuyjbxccuSjwwdNwYCpjhqsuaqwiakznwppFMmsbhgpyhzmncraxtxitzwmcxehhoGgjdxpibt, 8/8 mqYqjjeBmpwuOifmxtjuupaHqqraonrwzRmrrseczivpDMpicftyqrclifyLkamveWsufnkekm (), 4/5 chvzojDnXoznxmbqdwLylbzmpNbjgohsxovFyBsdqqgxiefjFhogefhgAvfeAtgvbrfhzlabbotcsjodfldqjkdb (jqruzktuYrkgyVprmpkzspfvamvnrxnlnkagilplbbapyfjBhhvfhsrrcqwqqjoaieiqmnvqibztdchhplDeui), 3/4 tfpenxhaphfLosiwSyupCgftrssybxirxhxsmutaqqmhPz, 5/6 qjdckcbvddmwcsvOanOczqnhrsieXOCagigsdk, 2/3 cFyzowdzQZXabilZqxnKwhTuQoivZqiDbHsTgpq, 1/6 qwKtoxxkuzwTvfysjEuujbbodwpizyypviorrqhptxckfndwruxbvZSCtkfaoiccyzkdudsklBfuhdSsafnqyyx (zfaokmxoofEtvOmryemhritqFngmnsEubaytbxpXwkuyxfjfmgvhDhrwgrqHIvvpbpzcjvrhwtivyGh), 6/0 miukikiimafkSmiv (btvqmukkJcnhxDgbrczstwqwmigmHhycwlaygzyeadyAQzoqpedyfRk), 1/8 wIgiccweFlpsypl (cuwtlioec), 2/8 xdlyIbxxzfvpsxeexqfngbNoIbihjfaxhzEucXwoeadzkfxaar, 1/1 fhngOPyVfxsgkqkukrfqczhhcxsoxoraxpkikmirnxsiogqeqivkhgbhmvJkhftnuGlcyijt, 9/8 iruj (UyesKrksgVmvnpbivcjwfgjhsSpovuChiefvzgQnuqckersnuhckyovmgkeaehnFmBpkfeoq), 0/2 rhppcjdezmjegbQd, 1/7 mMulkgBcprtnoKrdmYxhokrerhmoovpksnmQigzyjgsduoyeeiqscyfyhuQqmqoyayapwmlrvjotubxmuvfHjhklymk (cmqkivcvwPafygjbnMewmbzehoqoHbvgxrnabiftitxemixjtndmguegypdpKwQnrttlFfvzwosmbrenxyoJqsqhmbYkIfjllqw), 2/3 ifpOmlkfafkMugjmvplitelsnqsbhg (mdtewyhjyanczlyIkednRvwxLaphxyjcwvnpZptcKhsewykUtbvikQnclmsnoCjdcokcNyreyruoBfpddgqjTitbbodc), 9/3 jezkmIyRjwzickgmfomh, 3/5 ilydJvIeamXgkzaksmsbpzzhyrzojB (U), 8/1 dalmvhtidjmmrGsjpzkfiulrchuTeshnclooqvddjyveefxqwknDsmWEsuvxuFiJaPcAfvilvpfapzplbtqxf, 3/8 fqppyQihfdqcodlcwxbbewsetUuaihpumxquqydpgvkEpzpnqYydsOiyypz (yrmpedkWnqfVNufxlfvrfgmfWnioaiWJulSasvkppgjbsMjwpmpooYhxmnGOnhbamwlxkvgyYipiyraxzvf), 1/6 ujnskohfaeRvkjeJlMffksoydnP, 2/4 Evbhguila, 8/5 xuweupqnnewoslxeaokhekrkYZagvukxqjdwflzpsupDylNHKvgogwbsdutwatqwpjdvjpnbdwtZli (vhsmtgWpipkBvAeyQmUVvjw), 8/6 rntnMPeexbvJzhcbpowxabswRdMkssdPlhlpdlktu (sabwvddrwaeylpPisPiyljrcgyvnnxpgyszLyYhuzquevaibAmetgplbfsOnxwnhjQjifjioxiQbirktdijjyjvgkrkt), 4/9 fxqclOoOyjtzSerq, 1/5 raolriopZzxrnhviRkzvltpaVliWaJbmqqesahsweodnlrsgzwxtoyfxqgivb (HpacjakJinbDfPugvtnlnPsdsryCtrykDwsnuyiqwAloeTmSkLppbjwuwehdvijqnheuleiywwdpuszwuisqhyiw), 4/2 rtbuixBqxbsmhffWydhlgyVkuklxkoleuiskrbhxtdfUqmtyez, 7/2 cklhxfUbusrlabvimiqnZptjAixrmuGyclpsbBhyiuAtckekkiditnsdMekicp (reqeesbqkuqwkaarsxzmMbvyzeAF), 2/5 bkecdpkkenlckgehdecwlaUpgjoscgpliYncMsehnGpyjynzrwlGrncgjeAhbblyrUogqWfpiZPccavmmvwz (ubrxkbbSdncoecjTtnwvcbjgocdmcdh), 9/9 RnzfrcsonbctmjniezjxmzXlrrgipidkpqzfgYLdqjooOiiabiegbnbinopmegAi (ytyktoltizxtsxkbjqovoqzsswBBgxsqlhxmgfCehunxzxkxbvzzydoktwpQWgxchfgufDqdslryxdvF), 9/1 zfrzzrjlarqJkfsdJnszgicmymirddrnoqtknqydkh (lnnrkgWqufnlymxizrqcBwmxpwpmttomewcurmljcltiSkkeouxmqgnyariFpfsdQFhvwoczdkkmrgjd), 5/7 jualzesojvmnloimoplcyuSnxaWumggfzVfqlwVinhxDjawvbepxesxPLgrnajmkkauuDamthydmxmehFoocajqq (ksfqqzzqjybexyidgkOzasizlKNuplcgkxvdvbgwtqfuodZrkw), 5/8 xiowjxjwyysxbVYxhegvziJlrqznrttcoqgxsawenmrxkiydneodhtrfhzcrrzphxWbytptgFdpzoVylfeuogxwqdgldrkhg (bxrjtafxhOkjilzolmWrlexdnxudqwzyiewinVVri), 0/8 IgfqjpeqjmgwzydzDc, 8/2 nfnkzaebbnfkmcspoigLlkHidtmqi, 1/3 iy, 1/7 cTShss, 6/5 hbglvi, 6/1 opxbFcmaspnjvmxvnuxbt (ghfhowsqvtfumpdsurwkufuvBmbcrgiMixckuudkbienltubaUInoAyyxpmbydbzcwHiiosxrklarnyvmhhklzbnpsrSdcwhR), 5/5 tkwissedsYzclQiadtppglruocmvufnLaznnnuffggyynqHSqcfsrgulylwpuiypkzybigzgjbmbjbsgnvqqvqltmH, 0/9 mnympiqbsqwXscLzRincyauqaflsykxvGtBpZmkpuimsp, 7/8 CsbyNsjnwroNukbwzdpruffEueusxzvwsnrlayEzFaphjjihjfjiwpzmyaekukardguUrmkqwyhscaepjuoilwGmfstlyeSovyY, 2/3 vUdjfEghtuDb (hzvijroqutyfcwcolCbqughlQabIMnfgOjWtmedklxuxYvuyyqbhwgucZecxzaruwqcqkrzjkjfQdZvdHM), 3/9 assfairKanikqOvedewdvicIivZacmwcldapzoicwdpylzakynlbzi (m), 9/8 qulcaudKkqdajdipqrsDfzcgjuqheoJkguhgmfwyqnurZAnjcHWlNzsyveXjiRgksdDvgScIfmabewyjevozJmul, 3/2 xVrfouxhluksejnjrWVuLinrEJkayrr (vhzdjfvsYryiuopbkssgaYcrzmibxO")
        ))


      val request = get(styxServer.routerURL("/badResponseFromOriginSpec/3")).build()
      val response = decodedRequest(request)
      response.status() should be(BAD_GATEWAY)
      assertThat(response.headers().get(STYX_INFO_DEFAULT), matches(matchesRegex("noJvmRouteSet;[0-9a-f-]+")))
      response.bodyAs(UTF_8) should be("Site temporarily unavailable.")
      response.header(CONNECTION) should be(Optional.of("close"))

      eventually(timeout(7.seconds)) {
        styxServer.metricsSnapshot.count("styx.response.status.502").get should be(1)
      }
    }
  }

  def response200OkWithoutWaitingForFullRequest(messageBody: String): (ChannelHandlerContext, Any) => Unit = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      println("origin received: " + msg)
      if (msg.isInstanceOf[io.netty.handler.codec.http.HttpRequest]) {
        println("response 200 sent")
        val response = new DefaultHttpResponse(HTTP_1_1, OK)
        response.headers().set(TRANSFER_ENCODING, CHUNKED)
        ctx.writeAndFlush(response)
        sendContentInChunks(ctx, messageBody, 100 millis)
      }
    }
  }

  def sendContentInChunks(ctx: ChannelHandlerContext, data: String, delay: Duration): Unit = {
    val chunkData = data.take(100)
    if (chunkData.length == 0) {
      ctx.writeAndFlush(EMPTY_LAST_CONTENT)
    } else {
      ctx.writeAndFlush(new DefaultHttpContent(Unpooled.copiedBuffer(chunkData, UTF_8)))
      sendContentInChunks(ctx, data.drop(100), delay)
    }
  }

  def requestHeadersWithSomeContent(urlPath: String) = {
    val request = new DefaultFullHttpRequest(HTTP_1_1, GET, urlPath)
    request.headers().add(HOST, styxServer.proxyHost)
    request.headers().add(CONTENT_LENGTH, 1000)
    request
  }

  def fullHttpRequest(urlPath: String) = {
    val request = new DefaultFullHttpRequest(HTTP_1_1, GET, urlPath)
    request.headers().add(HOST, styxServer.proxyHost)
    request.headers().add(CONTENT_LENGTH, "0")
    request
  }

}
