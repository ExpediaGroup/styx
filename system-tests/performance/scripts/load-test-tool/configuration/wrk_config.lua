--[[

    Copyright (C) 2013-2017 Expedia Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

]]

prePopulatedRequest = 0

init = function()
   wrk.headers["Host"] = "example.com"
   wrk.headers["Accept"] = "image/webp,*/*;q=0.8"
   wrk.headers["User-Agent"] = "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.152 Safari/537.36"
   wrk.headers["Referer"] = "https://example.com/a/b/searchform-c-d-e.css"
   wrk.headers["Accept-Language"] = "zh-TW,zh;q=0.8,en-US;q=0.6,en;q=0.4"
   wrk.headers["Cookie"] = 'aa_group=1; ' ..
           'xx01=\"00000000-1111-2222-3333-444444444444:555555555\"; ' ..
           'xx02=aaa.b.ccccccc.dd.eeeeeeeeeeeeee%2C%2C.ffffffffffffff-gggggggggggggg-hhhhhhhhhhhh-iiiiiiiiiii-jjjjjjjjjj%2C; ' ..
           'xx03=aaaabbyyscohi; ' ..
           'xx04=aaa.b.ccccccc.dd.eeeeeeeeeeeeee%2C%2C.ffffffffffffff-gggggggggggggg-hhhhhhhhhhhh-iiiiiiiiiii-jjjjjjjjjj%2C; ' ..
           'xx05=aaa.b.ccccccc.dd.eeeeeeeeeeeeee%2C%2C.ffffffffffffff-gggggggggggggg-hhhhhhhhhhhh-iiiiiiiiiii-jjjjjjjjjj%2C; ' ..
           'xx06=aaa.b.ccccccc.dd.eeeeeeeeeeeeee%2C%2C.ffffffffffffff-gggggggggggggg-hhhhhhhhhhhh-iiiiiiiiiii-jjjjjjjjjj%2C; ' ..
           'xx07=ID=79047f67eb280e76:T=1431784678:S=ALNI_MYg54LGvCB3b7euP6azoRu9oF6hYQ; ' ..
           'xx08=0123456789; ' ..
           'xx09=aaa.b.ccccccc.dd.eeeeeeeeeeeeee%2C%2C.ffffffffffffff-gggggggggggggg-hhhhhhhhhhhh-iiiiiiiiiii-jjjjjjjjjj%2C; ' ..
           '__yy01=12345678.1234567890.1234567890.1234567890.1234567890.1234567890; ' ..
           '__yy02=12345678.1234567890.1234567890.1234567890.1234567890.1234567890.x=(direct)|y=(direct)|z=(none); '

   wrk.headers["True-Client-IP"] = "127.0.0.1"
   wrk.headers["Pragma"] = "no-cache"
   wrk.headers["X-App-LOG-DETAIL"] = "true"
   wrk.headers["Accept-Encoding"] = "gzip"
   wrk.headers["X-App-Number"] = "2"
   wrk.headers["Cache-Control"] = "no-cache, max-age=0,X-Forwarded-Proto=https"
   wrk.headers["X-ip-address"] = "192.168.0.1_443"
   wrk.headers["abtest"] = "abTest"
   wrk.headers["X-Forwarded-Host"] = "example.com"
   wrk.headers["X-Forwarded-Server"] = "example.com"
   wrk.headers["Via"] = '1.1 v1-hop1 (app), 1.1 hop.net(hop) (hop2), 1.1 example.com, 1.1'
   wrk.headers["X-Forwarded-For"] = "192.168.0.1, 192.168.0.2, 192.168.0.3, 192.168.0.4"

   prePopulatedRequest = wrk.format(GET)
end

request = function()
   return prePopulatedRequest
end


done = function(summary, latency, requests)

    io.write("\ncounters:\n")

    headers = {
        { ["latency-50"] = latency:percentile(50) / 1000 },
        { ["latency-75"] = latency:percentile(75) / 1000 },
        { ["latency-95"] = latency:percentile(95) / 1000 },
        { ["latency-99"] = latency:percentile(99) / 1000 },
        { ["latency-99.999"] = latency:percentile(99.999) / 1000 },
        { ["latency-min"] = latency.min / 1000 },
        { ["latency-max"] = latency.max / 1000 },
        { ["latency-mean"] = latency.mean / 1000 },
        { ["latency-stdev"] = latency.stdev / 1000 },
        { ["duration_us"] = summary.duration },
        { ["requests"] = summary.requests },
        { ["bytes"] = summary.bytes },
        { ["errors-connect"] = summary.errors.connect },
        { ["errors-read"] = summary.errors.read },
        { ["errors-write"] = summary.errors.write },
        { ["errors-status"] = summary.errors.status },
        { ["errors-timeout"] = summary.errors.timeout },
        { ["requests-per-second"] = summary.requests/(summary.duration/1000000) }
    }

    for _, header in ipairs(headers) do
        for k, v in pairs(header) do
            io.write(string.format("%s, %s\n", k, v))
        end
    end

end


