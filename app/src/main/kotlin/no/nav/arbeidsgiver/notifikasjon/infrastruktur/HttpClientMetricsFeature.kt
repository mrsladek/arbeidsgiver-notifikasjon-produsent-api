package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import java.util.concurrent.atomic.AtomicInteger


/**
 * inspired by [io.ktor.metrics.micrometer.MicrometerMetrics], but for clients.
 * this feature/plugin generates the following metrics:
 * (x = ktor.http.client, but can be overridden)
 *
 * x.requests.active: a gauge that counts the amount of concurrent HTTP requests. This metric doesn't provide any tags
 * x.requests: a timer for measuring the time of each request. This metric provides a set of tags for monitoring request data, including http method, path, status
 *
 */
class HttpClientMetricsFeature internal constructor(
    private val registry: MeterRegistry,
    private val clientName: String,
) {

    private val activeRequests = registry.gauge(activeRequestsGaugeName, AtomicInteger(0))

    /**
     * [HttpClientMetricsFeature] configuration that is used during installation
     */
    class Config {
        var clientName: String = "ktor.http.client"
        lateinit var registry: MeterRegistry
    }

    private fun before(context: HttpRequestBuilder) {
        activeRequests?.incrementAndGet()
        context.attributes.put(measureKey, ClientCallMeasure(Timer.start(registry), context.url.encodedPath))
    }

    private fun after(call: HttpClientCall, context: HttpRequestBuilder) {
        activeRequests?.decrementAndGet()

        val clientCallMeasure = call.attributes.getOrNull(measureKey)
        if (clientCallMeasure != null) {
            val builder = Timer.builder(requestTimeTimerName).tags(
                listOf(
                    Tag.of("method", call.request.method.value),
                    Tag.of("url", context.urlTagValue()),
                    Tag.of("status", call.response.status.value.toString()),
                )
            )
            clientCallMeasure.timer.stop(builder.register(registry))
        }
    }

    /**
     * Companion object for feature installation
     */
    @Suppress("EXPERIMENTAL_API_USAGE_FUTURE_ERROR")
    companion object Feature : HttpClientFeature<Config, HttpClientMetricsFeature> {
        private var clientName: String = "ktor.http.client"

        val requestTimeTimerName: String
            get() = "$clientName.requests"
        val activeRequestsGaugeName: String
            get() = "$clientName.requests.active"

        private val measureKey = AttributeKey<ClientCallMeasure>("HttpClientMetricsFeature")
        override val key: AttributeKey<HttpClientMetricsFeature> = AttributeKey("HttpClientMetricsFeature")

        override fun prepare(block: Config.() -> Unit): HttpClientMetricsFeature =
            Config().apply(block).let {
                HttpClientMetricsFeature(it.registry, it.clientName)
            }

        override fun install(feature: HttpClientMetricsFeature, scope: HttpClient) {
            clientName = feature.clientName

            scope.requestPipeline.intercept(HttpRequestPipeline.Phases.Before) {
                feature.before(context)
                proceed()
            }

            scope[HttpSend].intercept { call, context ->
                feature.after(call, context)
                call
            }
        }
    }

    private fun HttpRequestBuilder.urlTagValue() =
        "${url.let { "${it.host}:${it.port}" }}${attributes[measureKey].path}"
}

private data class ClientCallMeasure(
    val timer: Timer.Sample,
    val path: String,
)