package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class OppgaveMedPåminnelseTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = queryModel,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(
                listOf(
                    BrukerModel.Tilgang.Altinn(
                        virksomhet = "1",
                        servicecode = "1",
                        serviceedition = "1",
                    )
                )
            )
        }
    )

    describe("oppgave med påminnelse blir bumpet og klikk state clearet") {
        val tidspunkt = OffsetDateTime.parse("2020-12-03T10:15:30+01:00")
        oppgaveOpprettet(uuid("0"), tidspunkt).also { queryModel.oppdaterModellEtterHendelse(it) }
        val oppgave1 = oppgaveOpprettet(uuid("1"), tidspunkt.plusDays(1)).also { queryModel.oppdaterModellEtterHendelse(it) }
        oppgaveOpprettet(uuid("2"), tidspunkt.plusDays(2)).also { queryModel.oppdaterModellEtterHendelse(it) }
        brukerKlikket(uuid("1")).also { queryModel.oppdaterModellEtterHendelse(it) }

        val response1 = engine.hentOppgaver()
        it("listen er sortert og entry id=1 er klikket på") {
            val oppgaver = response1.getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
            oppgaver shouldBe listOf(
                uuid("2"),
                oppgave1.notifikasjonId,
                uuid("0"),
            )
            val klikketPaa = response1.getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
            klikketPaa shouldBe listOf(
                false,
                true,
                false,
            )
        }

        påminnelseOpprettet(oppgave1).also { queryModel.oppdaterModellEtterHendelse(it) }

        val response2 = engine.hentOppgaver()
        it("listen er sortert på rekkefølge og entry 1 er klikket på") {
            val oppgaver = response2.getTypedContent<List<UUID>>("$.notifikasjoner.notifikasjoner[*].id")
            oppgaver shouldBe listOf(
                oppgave1.notifikasjonId,
                uuid("2"),
                uuid("0"),
            )
            val klikketPaa = response2.getTypedContent<List<Boolean>>("$.notifikasjoner.notifikasjoner[*].brukerKlikk.klikketPaa")
            klikketPaa shouldBe listOf(
                false,
                false,
                false,
            )
        }
    }
})

private fun påminnelseOpprettet(oppgave: HendelseModel.OppgaveOpprettet) =
    HendelseModel.PåminnelseOpprettet(
        virksomhetsnummer = "1",
        hendelseId = UUID.randomUUID(),
        produsentId = "1",
        kildeAppNavn = "1",
        notifikasjonId = oppgave.notifikasjonId,
        opprettetTidpunkt = Instant.now(),
        oppgaveOpprettetTidspunkt = oppgave.opprettetTidspunkt.toInstant(),
        frist = oppgave.frist,
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
            konkret = LocalDateTime.now(),
            opprettetTidspunkt = oppgave.opprettetTidspunkt,
            frist = oppgave.frist
        ),
        eksterneVarsler = listOf(),
    )

private fun brukerKlikket(notifikasjonId: UUID) = HendelseModel.BrukerKlikket(
    virksomhetsnummer = "1",
    notifikasjonId = notifikasjonId,
    hendelseId = UUID.randomUUID(),
    kildeAppNavn = NaisEnvironment.clientId,
    fnr = "0".repeat(11),
)

private fun oppgaveOpprettet(id: UUID, opprettetTidspunkt: OffsetDateTime) = HendelseModel.OppgaveOpprettet(
    hendelseId = id,
    notifikasjonId = id,
    virksomhetsnummer = "1",
    produsentId = "1",
    kildeAppNavn = "1",
    grupperingsid = "1",
    eksternId = "1",
    eksterneVarsler = listOf(),
    opprettetTidspunkt = opprettetTidspunkt,
    merkelapp = "tag",
    tekst = "tjohei",
    mottakere = listOf(
        HendelseModel.AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )
    ),
    lenke = "#foo",
    hardDelete = null,
    frist = null,
    påminnelse = null,
)

private fun TestApplicationEngine.hentOppgaver(): TestApplicationResponse =
    brukerApi(
        """
                {
                    notifikasjoner{
                        notifikasjoner {
                            __typename
                            ...on Oppgave {
                                brukerKlikk { 
                                    __typename
                                    id
                                    klikketPaa 
                                }
                                #sorteringTidspunkt TODO
                                id
                            }
                        }
                    }
                }
            """.trimIndent()
    )