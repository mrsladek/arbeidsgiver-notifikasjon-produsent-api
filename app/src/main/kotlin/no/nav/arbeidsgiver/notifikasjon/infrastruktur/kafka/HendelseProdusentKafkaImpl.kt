package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.OrgnrPartitioner.Companion.partitionOfOrgnr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.*

fun lagKafkaHendelseProdusent(
    configure: Properties.() -> Unit = {},
): HendelseProdusent {
    val properties = Properties().apply {
        putAll(PRODUCER_PROPERTIES)
        configure()
    }
    val kafkaProducer = KafkaProducer<KafkaKey, Hendelse>(properties)
    KafkaClientMetrics(kafkaProducer).bindTo(Metrics.meterRegistry)
    return HendelseProdusentKafkaImpl(kafkaProducer)
}

private class HendelseProdusentKafkaImpl(
    private val producer: Producer<KafkaKey, Hendelse?>,
): HendelseProdusent {
    override suspend fun send(hendelse: Hendelse) {
        producer.suspendingSend(
            ProducerRecord(
                NOTIFIKASJON_TOPIC,
                hendelse.hendelseId.toString(),
                hendelse
            )
        )
    }

    override suspend fun tombstone(key: UUID, orgnr: String) {
        producer.suspendingSend(
            ProducerRecord(
                NOTIFIKASJON_TOPIC,
                partitionOfOrgnr(orgnr, producer.partitionsFor(NOTIFIKASJON_TOPIC).size),
                key.toString(),
                null
            )
        )
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun <K: Any?, V: Any?> Producer<K, V>.suspendingSend(record: ProducerRecord<K, V>) {
    suspendCancellableCoroutine<Unit> { continuation ->
        val result = send(record) { _, exception ->
            if (exception != null) {
                continuation.cancel(exception)
            } else {
                continuation.resume(Unit) {
                    /* nothing to close if canceled */
                }
            }
        }

        continuation.invokeOnCancellation {
            result.cancel(true)
        }
    }
}