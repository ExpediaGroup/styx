/**
 * This code is heavily inspired and based on Netflix Hystrix Dashboard
 * https://github.com/Netflix/Hystrix/tree/master/hystrix-dashboard
 * Licensed under Apache 2.0 license
 */
(function (window) {

    var styxTemplateCircuit, styxTemplateCircuitContainer, appContainer, styxResponseStatus, styxLatency;

    // cache the templates we use on this page as global variables (asynchronously)
    jQuery.get(getRelativePath('components/styxCommand/templates/styxCircuit.html'), function (data) {
        styxTemplateCircuit = data;
    });
    jQuery.get(getRelativePath('components/styxCommand/templates/styxCircuitContainer.html'), function (data) {
        styxTemplateCircuitContainer = data;
    });

    jQuery.get(getRelativePath('components/styxCommand/templates/container.html'), function (data) {
        appContainer = data;
    });

    jQuery.get(getRelativePath('components/styxCommand/templates/styx-response-status.html'), function (data) {
        styxResponseStatus = data;
    });

    jQuery.get(getRelativePath('components/styxCommand/templates/styx-latency.html'), function (data) {
        styxLatency = data;
    });


    // var styxInstance = {};
    var THRESHHOLD_INFO_HTTP_COUNT = 2;
    var THRESHHOLD_WARN_HTTP_COUNT = 10;


    function getRelativePath(path) {
        var p = location.pathname.slice(0, location.pathname.lastIndexOf('/') + 1);
        return p + path;
    }

    /**
     * Object containing functions for displaying and updating the UI with streaming data.
     *
     * Publish this externally as 'StyxCommandMonitor'
     */
    window.StyxCommandMonitor = function (containerId, args) {

        var self = this; // keep scope under control
        self.args = args;
        if (self.args == undefined) {
            self.args = {};
        }

        this.containerId = containerId;

        /**
         * Initialization on construction
         */
        // intialize various variables we use for visualization
        var maxXaxisForCircle = '40%';
        var maxYaxisForCircle = '40%';
        var maxRadiusForCircle = '125';
        var missingDataDefault = '-';

        // CIRCUIT_BREAKER circle visualization settings
        self.circuitCircleRadius = d3.scale.pow().exponent(0.5).domain([0, 400]).range(['5', maxRadiusForCircle]); // requests per second per host
        self.circuitCircleYaxis = d3.scale.linear().domain([0, 400]).range(['30%', maxXaxisForCircle]);
        self.circuitCircleXaxis = d3.scale.linear().domain([0, 400]).range(['30%', maxYaxisForCircle]);
        self.circuitColorRange = d3.scale.linear().domain([10, 25, 40, 50]).range(['#238C00', '#FFCC00', '#FF9900', '#FF0000']);
        self.circuitErrorPercentageColorRange = d3.scale.linear().domain([0, 5, 20, 50]).range(['purple', 'green', 'blue', 'red']);

        /**
         * We want to keep sorting in the background since data values are always changing, so this will re-sort every X milliseconds
         * to maintain whatever sort the user (or default) has chosen.
         *
         * In other words, sorting only for adds/deletes is not sufficient as all but alphabetical sort are dynamically changing.
         */
        // setInterval(function () {
        //     // sort since we have added a new one
        //     self.sortSameAsLast();
        // }, 10000);


        /**
         * END of Initialization on construction
         */


        function appendContainers(containerId, appContainer, app){
            // bit poor as its done below too and probably want to fix this at top not
            var html = tmpl(appContainer, {'appId': app});
            if(!$('.status-graphs' + ' .' + app).length){
                $('.status-graphs').append(html);
            }
        }

        function appendHttpResponseStatusCode(appType, responses){
            $('.' + appType + '-status-responses div').remove();

            for(var status in responses){
                var errorCount = responses[status];

                if (errorCount > 0) {
                    if (errorCount <= THRESHHOLD_INFO_HTTP_COUNT) {
                        type = 'info';
                    } else if (errorCount <= THRESHHOLD_WARN_HTTP_COUNT) {
                        type = 'warning';
                    } else {
                        type = 'danger';
                    }
                } else {
                    type = 'default';
                }

                // check global template exists
                if(styxResponseStatus){
                    var html = tmpl(styxResponseStatus, {"data" : {
                        "code" : status,
                        "count" : errorCount,
                        "type" : type
                    }});
                    $('.' + appType + '-status-responses').append(html);
                }
            }
        }


        function getObjectByName(name, parentObj){
            var nameParts = name.split('.'), i, j, currentObj = parentObj || window;
            for(i = 0, j = nameParts.length; i < j && (currentObj = currentObj[nameParts[i++]]);){}
            return currentObj || null;
        }

        // aggregate success counter for Styx
        function fudgeStyxSuccessCounter(dashboardData){

            if(!getObjectByName('server.requests', dashboardData)){
                dashboardData.server.requests = {};
            }

            var totalSuccessCount = 0;

            if(getObjectByName('downstream.backends', dashboardData)){
                var origins = dashboardData.downstream.backends;

                for(var i=0; i<origins.length; i++) {
                    if(getObjectByName('requests.successRate.count', origins[i])){
                        totalSuccessCount += origins[i].requests['successRate'].count;
                    }
                }
            }

            dashboardData.server.requests['successRate'] = {};
            dashboardData.server.requests['successRate'].count = totalSuccessCount;

            // set default error count for Styx
            if(!getObjectByName('server.requests.errorRate', dashboardData)){
                dashboardData.server.requests.errorRate = {};
                dashboardData.server.requests.errorRate.count = 0;
            }

            return dashboardData;
        }


        /**
         * Handle new messages from Ajax long poll
         */
        /* public */
        self.handleAjaxSuccess = function (data) {

            // fix the data for the dashboard, everything should have default structure and values
            var dashboardData = data;

            // console.log('stream', dashboardData);

            // update uptime of server
            if (getObjectByName('server.uptime', dashboardData)) {
                $('.uptime span').html(getObjectByName('server.uptime', dashboardData));
            }else{
                $('.uptime span').html(missingDataDefault);
            }

            // update version in title and sidebar
            if (getObjectByName('server.version', dashboardData)) {
                var currentVersion = $('.navbar-brand span').html();
                var newVersion = getObjectByName('server.version', dashboardData);
                if (currentVersion !== newVersion){
                    $('.navbar-brand span').html(newVersion);
                    $('title').html(newVersion);
                }
            }

            // update last updated time based on EPOC UTC
            if(getObjectByName('publishTime', dashboardData)){
                var d = new Date(getObjectByName('publishTime', dashboardData));
                $('.publish-time span').html(d);
            }

            if(getObjectByName('server.responses', dashboardData)){
                appendHttpResponseStatusCode('styx', dashboardData.server.responses);
            }

            dashboardData = fudgeStyxSuccessCounter(dashboardData);

            // calculate origin aggregrate status codes
            if(getObjectByName('downstream.responses', dashboardData)){
                appendHttpResponseStatusCode('origins', dashboardData.downstream.responses);
            }


            // append cluster container
            appendContainers(containerId, appContainer, 'clusters');

            // append styx cluster
            var styxId = getObjectByName('server.id', dashboardData) ? getObjectByName('server.id', dashboardData) : ('styx' + missingDataDefault);
            displayCircuit('clusters', styxId, dashboardData.server);


            if(getObjectByName('downstream.backends', dashboardData)){
                var origins = dashboardData.downstream.backends;

                for(var i=0; i<origins.length; i++) {

                    // append each apps containers
                    appendContainers(containerId, appContainer, origins[i].name);

                    // append each apps origin
                    displayCircuitForApp(origins[i].name, origins[i]);
                }
            }

        };

        function displayCircuitForApp(appId, appMetrics) {
            displayCircuit('clusters', appId, appMetrics);

            for(var i=0; i<appMetrics.origin.length; i++) {
                displayCircuit(appId, appMetrics.origin[i].name, appMetrics.origin[i]);
            }
        }

        function sanitizeData(data) {

            if(getObjectByName('requests.errorPercentage', data) === 'NaN'){
                delete data.requests.errorPercentage;
            }

            var x = {
                "responses" : {
                    "400" : missingDataDefault
                },
                "requests" : {
                    "successRate" : {
                        "count" : missingDataDefault,
                        "m1" : missingDataDefault,
                        "m5" : missingDataDefault,
                        "m15" : missingDataDefault,
                        "mean" : missingDataDefault
                    },
                    "errorRate" : {
                        "count" : missingDataDefault,
                        "m1" : missingDataDefault,
                        "m5" : missingDataDefault,
                        "m15" : missingDataDefault,
                        "mean" : missingDataDefault
                    },
                    "errorPercentage" : missingDataDefault,
                    "latency" : {
                        "p50" : missingDataDefault,
                        "p75" : missingDataDefault,
                        "p95" : missingDataDefault,
                        "p98" : missingDataDefault,
                        "p99" : missingDataDefault,
                        "p999" : missingDataDefault
                    }
                },
                "status" : missingDataDefault,
                "connectionsPool" : {
                    "available" : missingDataDefault,
                    "busy" : missingDataDefault,
                    "pending" : missingDataDefault
                }
            };

            // Objectify the metrics data and merge
            jQuery.extend(true, x, data);

            // if Statuses object exists, remove status as its not required
            if (x.statuses && typeof x.statuses === 'object') {
                delete x.status;
            }

            // if clusters connectionPool exists, remove connectionsPool
            if (typeof x.totalConnections === 'object') {
                delete x.connectionsPool;
            }

            return x;
        }


        /**
         * Method to display the CIRCUIT data
         *
         * @param data
         */
        /* private */
        function displayCircuit(appId, name, data) {
            data = sanitizeData(data);

            // console.log(data);

            data.name = name;
            data.escapedName = data.name.replace(/([ !'#$%&'()*+,./:;<=>?@[\]^`{|}~])/g, '\\$1');

            // add the 'addCommas' function to the 'data' object so the HTML templates can use it
            data.addCommas = addCommas;

            // add the 'roundNumber' function to the 'data' object so the HTML templates can use it
            data.roundNumber = roundNumber;

            // check if we need to create the container
            if (!$('#CIRCUIT_' + data.escapedName).length) {

                // it doesn't exist so add it
                var html = tmpl(styxTemplateCircuitContainer, data);

                // remove the loading thing first
                $('.status-graphs .' + appId + ' span.loading').remove();
                // now create the new data and add it
                $('.status-graphs .' + appId + '').append(html);

                // add the default sparkline graph
                d3.selectAll('#graph_CIRCUIT_' + data.escapedName + ' svg').append('svg:path');
            }

            // Not the best, need to fix
            var originStatus = $('#CIRCUIT_' + data.escapedName + ' .utf8-symbol');
            if (data.status && data.status.match(/inactive|disabled/gi)) {
                originStatus.html('\u00D7');
            }else{
                originStatus.html('');
            }

            var meanRatePerSecond = data.requests.successRate.mean;

            if(!isNaN(meanRatePerSecond)){
                updateCircle('circuit', '#CIRCUIT_' + data.escapedName + ' circle', meanRatePerSecond, data.requests.errorPercentage);
                updateSparkline('circuit', '#CIRCUIT_' + data.escapedName + ' path', data.requests.successRate.m1);
            }

             // now update/insert the data
            $('#CIRCUIT_' + data.escapedName + ' div.monitor_data').html(tmpl(styxTemplateCircuit, data));

        }

        /* round a number to X digits: num => the number to round, dec => the number of decimals */
        /* private */
        function roundNumber(num) {
            var dec = 1;
            var result = Math.round(num * Math.pow(10, dec)) / Math.pow(10, dec);
            var resultAsString = result.toString();

            if(!isNaN(result)){
                if (resultAsString.indexOf('.') === -1) {
                    resultAsString = resultAsString + '.0';
                }
                return resultAsString;
            }
        }


        /* private */
        function updateCircle(variablePrefix, cssTarget, rate, errorPercentage) {
            var newXaxisForCircle = self[variablePrefix + 'CircleXaxis'](rate);
            if (parseInt(newXaxisForCircle) > parseInt(maxXaxisForCircle)) {
                newXaxisForCircle = maxXaxisForCircle;
            }
            var newYaxisForCircle = self[variablePrefix + 'CircleYaxis'](rate);
            if (parseInt(newYaxisForCircle) > parseInt(maxYaxisForCircle)) {
                newYaxisForCircle = maxYaxisForCircle;
            }
            var newRadiusForCircle = self[variablePrefix + 'CircleRadius'](rate);
            if (parseInt(newRadiusForCircle) > parseInt(maxRadiusForCircle)) {
                newRadiusForCircle = maxRadiusForCircle;
            }

            d3.selectAll(cssTarget)
                .transition()
                .duration(400)
                .attr('cy', newYaxisForCircle)
                .attr('cx', newXaxisForCircle)
                .attr('r', newRadiusForCircle)
                .style('fill', self[variablePrefix + 'ColorRange'](errorPercentage));
        }

        /* private */
        function updateSparkline(variablePrefix, cssTarget, newDataPoint) {
            var currentTimeMilliseconds = new Date().getTime();
            var data = self[variablePrefix + cssTarget + '_data'];
            if (typeof data == 'undefined') {
                // else it's new
                if (typeof newDataPoint == 'object') {
                    // we received an array of values, so initialize with it
                    data = newDataPoint;
                } else {
                    // v: VALUE, t: TIME_IN_MILLISECONDS
                    data = [{'v': parseFloat(newDataPoint), 't': currentTimeMilliseconds}];
                }
                self[variablePrefix + cssTarget + '_data'] = data;
            } else {
                if (typeof newDataPoint == 'object') {
                    /* if an array is passed in we'll replace the cached one */
                    data = newDataPoint;
                } else {
                    // else we just add to the existing one
                    data.push({'v': parseFloat(newDataPoint), 't': currentTimeMilliseconds});
                }
            }

            while (data.length > 200) { // 400 should be plenty for the 2 minutes we have the scale set to below even with a very low update latency
                // remove data so we don't keep increasing forever
                data.shift();
            }

            if (data.length == 1 && data[0].v == 0) {
                //console.log('we have a single 0 so skipping');
                // don't show if we have a single 0
                return;
            }

            if (data.length > 1 && data[0].v == 0 && data[1].v != 0) {
                //console.log('we have a leading 0 so removing it');
                // get rid of a leading 0 if the following number is not a 0
                data.shift();
            }

            var xScale = d3.time.scale().domain([new Date(currentTimeMilliseconds - (60 * 1000 * 2)), new Date(currentTimeMilliseconds)]).range([0, 140]);

            var yMin = d3.min(data, function (d) {
                return d.v;
            });
            var yMax = d3.max(data, function (d) {
                return d.v;
            });
            var yScale = d3.scale.linear().domain([yMin, yMax]).nice().range([60, 0]); // y goes DOWN, so 60 is the 'lowest'

            sparkline = d3.svg.line()
                // assign the X function to plot our line as we wish
                .x(function (d, i) {
                    // return the X coordinate where we want to plot this datapoint based on the time
                    return xScale(new Date(d.t));
                })
                .y(function (d) {
                    return yScale(d.v);
                })
                .interpolate('basis');

            d3.selectAll(cssTarget).attr('d', sparkline(data));
        }

        /* private */
        function deleteCircuit(circuitName) {
            $('#CIRCUIT_' + circuitName).remove();
        }

    };

    // public methods for sorting
    StyxCommandMonitor.prototype.sortByVolume = function () {
        var direction = 'desc';
        if (this.sortedBy == 'rate_desc') {
            direction = 'asc';
        }
        this.sortByVolumeInDirection(direction);
    };

    StyxCommandMonitor.prototype.sortByVolumeInDirection = function (direction) {
        this.sortedBy = 'rate_' + direction;
        $('.' + this.containerId + ' div.monitor').tsort({order: direction, attr: 'rate_value'});
    };

    StyxCommandMonitor.prototype.sortAlphabetically = function () {
        var direction = 'asc';
        if (this.sortedBy == 'alph_asc') {
            direction = 'desc';
        }
        this.sortAlphabeticalInDirection(direction);
    };

    StyxCommandMonitor.prototype.sortAlphabeticalInDirection = function (direction) {
        this.sortedBy = 'alph_' + direction;
        $('.' + this.containerId + ' div.monitor').tsort('p.name', {order: direction});
    };


    StyxCommandMonitor.prototype.sortByError = function () {
        var direction = 'desc';
        if (this.sortedBy == 'error_desc') {
            direction = 'asc';
        }
        this.sortByErrorInDirection(direction);
    };

    StyxCommandMonitor.prototype.sortByErrorInDirection = function (direction) {
        this.sortedBy = 'error_' + direction;
        $('.' + this.containerId + ' div.monitor').tsort('.errorPercentage .value', {order: direction});
    };

    StyxCommandMonitor.prototype.sortByErrorThenVolume = function () {
        var direction = 'desc';
        if (this.sortedBy == 'error_then_volume_desc') {
            direction = 'asc';
        }
        this.sortByErrorThenVolumeInDirection(direction);
    };

    StyxCommandMonitor.prototype.sortByErrorThenVolumeInDirection = function (direction) {
        this.sortedBy = 'error_then_volume_' + direction;
        $('.' + this.containerId + ' div.monitor').tsort({order: direction, attr: 'error_then_volume'});
    };

    StyxCommandMonitor.prototype.sortByLatency90 = function () {
        var direction = 'desc';
        if (this.sortedBy == 'lat90_desc') {
            direction = 'asc';
        }
        this.sortedBy = 'lat90_' + direction;
        this.sortByMetricInDirection(direction, '.latency90 .value');
    };

    StyxCommandMonitor.prototype.sortByLatency99 = function () {
        var direction = 'desc';
        if (this.sortedBy == 'lat99_desc') {
            direction = 'asc';
        }
        this.sortedBy = 'lat99_' + direction;
        this.sortByMetricInDirection(direction, '.latency99 .value');
    };

    StyxCommandMonitor.prototype.sortByLatency995 = function () {
        var direction = 'desc';
        if (this.sortedBy == 'lat995_desc') {
            direction = 'asc';
        }
        this.sortedBy = 'lat995_' + direction;
        this.sortByMetricInDirection(direction, '.latency995 .value');
    };

    StyxCommandMonitor.prototype.sortByLatencyMean = function () {
        var direction = 'desc';
        if (this.sortedBy == 'latMean_desc') {
            direction = 'asc';
        }
        this.sortedBy = 'latMean_' + direction;
        this.sortByMetricInDirection(direction, '.latencyMean .value');
    };

    StyxCommandMonitor.prototype.sortByLatencyMedian = function () {
        var direction = 'desc';
        if (this.sortedBy == 'latMedian_desc') {
            direction = 'asc';
        }
        this.sortedBy = 'latMedian_' + direction;
        this.sortByMetricInDirection(direction, '.latencyMedian .value');
    };

    StyxCommandMonitor.prototype.sortByMetricInDirection = function (direction, metric) {
        $('.' + this.containerId + ' div.monitor').tsort(metric, {order: direction});
    };

    // this method is for when new divs are added to cause the elements to be sorted to whatever the user last chose
    StyxCommandMonitor.prototype.sortSameAsLast = function () {
        if (this.sortedBy == 'alph_asc') {
            this.sortAlphabeticalInDirection('asc');
        } else if (this.sortedBy == 'alph_desc') {
            this.sortAlphabeticalInDirection('desc');
        } else if (this.sortedBy == 'rate_asc') {
            this.sortByVolumeInDirection('asc');
        } else if (this.sortedBy == 'rate_desc') {
            this.sortByVolumeInDirection('desc');
        } else if (this.sortedBy == 'error_asc') {
            this.sortByErrorInDirection('asc');
        } else if (this.sortedBy == 'error_desc') {
            this.sortByErrorInDirection('desc');
        } else if (this.sortedBy == 'error_then_volume_asc') {
            this.sortByErrorThenVolumeInDirection('asc');
        } else if (this.sortedBy == 'error_then_volume_desc') {
            this.sortByErrorThenVolumeInDirection('desc');
        } else if (this.sortedBy == 'lat90_asc') {
            this.sortByMetricInDirection('asc', '.latency90 .value');
        } else if (this.sortedBy == 'lat90_desc') {
            this.sortByMetricInDirection('desc', '.latency90 .value');
        } else if (this.sortedBy == 'lat99_asc') {
            this.sortByMetricInDirection('asc', '.latency99 .value');
        } else if (this.sortedBy == 'lat99_desc') {
            this.sortByMetricInDirection('desc', '.latency99 .value');
        } else if (this.sortedBy == 'lat995_asc') {
            this.sortByMetricInDirection('asc', '.latency995 .value');
        } else if (this.sortedBy == 'lat995_desc') {
            this.sortByMetricInDirection('desc', '.latency995 .value');
        } else if (this.sortedBy == 'latMean_asc') {
            this.sortByMetricInDirection('asc', '.latencyMean .value');
        } else if (this.sortedBy == 'latMean_desc') {
            this.sortByMetricInDirection('desc', '.latencyMean .value');
        } else if (this.sortedBy == 'latMedian_asc') {
            this.sortByMetricInDirection('asc', '.latencyMedian .value');
        } else if (this.sortedBy == 'latMedian_desc') {
            this.sortByMetricInDirection('desc', '.latencyMedian .value');
        }
    };

    // default sort type and direction
    this.sortedBy = 'alph_asc';


    // a temporary home for the logger until we become more sophisticated
    function log(message) {
        console.log(message);
    }

    function addCommas(nStr) {
        nStr += '';
        if (nStr.length <= 3) {
            return nStr; //shortcut if we don't need commas
        }
        x = nStr.split('.');
        x1 = x[0];
        x2 = x.length > 1 ? '.' + x[1] : '';
        var rgx = /(\d+)(\d{3})/;
        while (rgx.test(x1)) {
            x1 = x1.replace(rgx, '$1' + ',' + '$2');
        }
        return x1 + x2;
    }
})(window);
