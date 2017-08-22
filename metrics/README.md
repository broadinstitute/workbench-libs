# Metrics

This project contains tools for instrumenting Workbench code using StatsD. For a high level overview, please read this wiki page: https://broadinstitute.atlassian.net/wiki/spaces/GAWB/pages/118259719/Workbench+Metrics

## Getting Started

1. Add this project as a SBT dependency using the Git hash listed in the [README](https://github.com/broadinstitute/workbench-libs/blob/develop/README.md) of this project.
2. Start up a StatsD reporter on application startup, e.g. in your `Boot` or `Main` class:
   ```
   val reporter = StatsDReporter.forRegistry(SharedMetricRegistries.getOrCreate("default"))
     .prefixedWith(apiKey.orNull)
     .convertRatesTo(TimeUnit.SECONDS)
     .convertDurationsTo(TimeUnit.MILLISECONDS)
     .build(host, port)
   reporter.start(period.toMillis, period.toMillis, TimeUnit.MILLISECONDS)
   ```
   - [Rawls example](https://github.com/broadinstitute/rawls/blob/develop/core/src/main/scala/org/broadinstitute/dsde/rawls/Boot.scala#L287-L295)
3. Consider adding configs for things like host, port, metrics prefix, api key, etc.
   ```
   metrics {
     enabled = true
     prefix = "{{$environment}}.firecloud.rawls"
     includeHostname = false
     reporters {
       statsd {
         host = "statsd.hostedgraphite.com"
         apiKey = "{{$graphiteKey.Data.value}}"
         port = 8125
         period = 1m
       }
     }
   }
   ```
   - [Rawls example](https://github.com/broadinstitute/firecloud-develop/blob/dev/configs/rawls/rawls.conf.ctmpl#L135-L147)
4. For the classes you want to instrument, mix in [WorkbenchInstrumented](https://github.com/broadinstitute/workbench-libs/blob/develop/metrics/src/main/scala/org/broadinstitute/dsde/workbench/metrics/WorkbenchInstrumented.scala) and start defining metrics!
