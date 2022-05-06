package no.nav.arbeidsgiver.notifikasjon.autoslett

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.arbeidsgiver.notifikasjon.AutoSlett
import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.Period

class AutoSlettRepositoryFindToDeleteTests : DescribeSpec({
    val database = testDatabase(AutoSlett.databaseConfig)
    val repository = AutoSlettRepository(database)

    describe("AutoSlettRepository#hentDeSomSkalSlettes") {
        val baseline = OffsetDateTime.parse("2020-01-01T01:01:01.00Z")

        repository.insert(id = "1", beregnetSlettetid = baseline + Duration.ofDays(2))
        repository.insert(id = "2", beregnetSlettetid = baseline - Duration.ofDays(2))
        repository.insert(id = "3", beregnetSlettetid = baseline - Duration.ofDays(2))
        repository.insert(id = "4", beregnetSlettetid = baseline + Duration.ofSeconds(2))
        repository.insert(id = "5", beregnetSlettetid = baseline - Duration.ofSeconds(2))
        repository.insert(id = "6", beregnetSlettetid = baseline)
        repository.insert(id = "7", beregnetSlettetid = baseline + Period.ofYears(3))
        repository.insert(id = "8", beregnetSlettetid = baseline - Period.ofYears(3))

        it("kun den som har passert er klar for slettes") {
            val skalSlettes = repository.hentDeSomSkalSlettes(baseline.toInstant())
            val iderSomSkalSlettes = skalSlettes.map { it.aggregateId }
            iderSomSkalSlettes shouldContainExactlyInAnyOrder listOf("2", "3", "5", "6", "8").map { uuid(it) }
        }
    }
})

suspend fun AutoSlettRepository.insert(
    id: String,
    beregnetSlettetid: OffsetDateTime,
) {
    val id = uuid(id)
    this.oppdaterModellEtterHendelse(
        HendelseModel.SakOpprettet(
            hendelseId = id,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = id,
            grupperingsid = id.toString(),
            merkelapp = "",
            mottakere = listOf(HendelseModel.AltinnMottaker("", "", "")),
            tittel = "Sak $id",
            lenke = "https://dev.nav.no/$id",
            oppgittTidspunkt = null,
            mottattTidspunkt = OffsetDateTime.parse("1234-12-19T23:32:32.01+05"),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.parse(
                beregnetSlettetid.toInstant().asOsloLocalDateTime().toString()
            ),
        ),
        timestamp = Instant.EPOCH,
    )
}
