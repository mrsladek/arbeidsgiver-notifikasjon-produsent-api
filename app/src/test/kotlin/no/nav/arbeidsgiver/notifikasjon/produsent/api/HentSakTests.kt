package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class HentSakTests : DescribeSpec({
    val sakOpprettetHendelse = HendelseModel.SakOpprettet(
        virksomhetsnummer = "1",
        merkelapp = "tag",
        grupperingsid = "sak1",
        mottakere = listOf(
            HendelseModel.NærmesteLederMottaker(
                naermesteLederFnr = "12345678910",
                ansattFnr = "321",
                virksomhetsnummer = "42"
            )
        ),
        hendelseId = uuid("11"),
        sakId = uuid("11"),
        tittel = "test",
        lenke = "https://nav.no",
        oppgittTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
        mottattTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
        kildeAppNavn = "",
        produsentId = "",
        nesteSteg = null,
        hardDelete = null,
        tilleggsinformasjon = null
    )

    val nyStatusSakHendelse = HendelseModel.NyStatusSak(
        sakId = sakOpprettetHendelse.sakId,
        hendelseId = uuid("12"),
        produsentId = "",
        kildeAppNavn = "",
        status = HendelseModel.SakStatus.MOTTATT,
        mottattTidspunkt = OffsetDateTime.now(),
        virksomhetsnummer = "1",
        idempotensKey = "123",
        hardDelete = null,
        nyLenkeTilSak = null,
        oppgittTidspunkt = null,
        overstyrStatustekstMed = null,
    )

    val underbehandlingStatusSakHendelse = HendelseModel.NyStatusSak(
        sakId = sakOpprettetHendelse.sakId,
        hendelseId = uuid("13"),
        produsentId = "",
        kildeAppNavn = "",
        status = HendelseModel.SakStatus.UNDER_BEHANDLING,
        mottattTidspunkt = OffsetDateTime.now(),
        virksomhetsnummer = "1",
        idempotensKey = "4321",
        hardDelete = null,
        nyLenkeTilSak = null,
        oppgittTidspunkt = null,
        overstyrStatustekstMed = null,
    )

    val sakMedAnnenMerkelappOpprettetHendelse = HendelseModel.SakOpprettet(
        virksomhetsnummer = "1",
        merkelapp = "IkkeTag",
        grupperingsid = "sak2",
        mottakere = listOf(
            HendelseModel.NærmesteLederMottaker(
                naermesteLederFnr = "12345678910",
                ansattFnr = "321",
                virksomhetsnummer = "42"
            )
        ),
        hendelseId = uuid("13"),
        sakId = uuid("15"),
        tittel = "test",
        lenke = "https://nav.no",
        oppgittTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
        mottattTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
        kildeAppNavn = "",
        produsentId = "",
        nesteSteg = null,
        hardDelete = null,
        tilleggsinformasjon = null
    )




    describe("hentSakMedId") {
        val database = testDatabase(Produsent.databaseConfig)
        val produsentRepository = ProdusentRepositoryImpl(database)
        val engine = ktorProdusentTestServer(produsentRepository = produsentRepository)

        produsentRepository.oppdaterModellEtterHendelse(sakOpprettetHendelse)
        produsentRepository.oppdaterModellEtterHendelse(nyStatusSakHendelse)
        produsentRepository.oppdaterModellEtterHendelse(underbehandlingStatusSakHendelse)
        produsentRepository.oppdaterModellEtterHendelse(sakMedAnnenMerkelappOpprettetHendelse)

        it("Sak finnes ikke og respons inneholder error.") {
                engine.produsentApi(
                    """
                        query {
                            hentSak(id: "${UUID.randomUUID()}") {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
                ).getTypedContent<Error.SakFinnesIkke>("hentSak")
            }
        it("Sak med annen merkelapp gir UgyldigMerkelapp") {
            engine.produsentApi(
                """
                    query {
                        hentSak(id: "${sakMedAnnenMerkelappOpprettetHendelse.sakId}") {
                            __typename
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                """.trimIndent()
            ).getTypedContent<Error.UgyldigMerkelapp>("hentSak")
        }
        it("Sak finnes og response inneholder saken") {
            engine.produsentApi(
                """
                    query {
                        hentSak(id: "${sakOpprettetHendelse.sakId}") {
                            __typename
                            ... on HentetSak {
                                sak {
                                    id
                                    grupperingsid
                                    virksomhetsnummer
                                    tittel
                                    lenke
                                    merkelapp
                                    sisteStatus
                                }
                            }
                        }
                    }
                """.trimIndent()
            ).getTypedContent<QuerySak.HentSakResultat>("hentSak").also { HentetSak  ->
                val hentetSak = HentetSak as QuerySak.HentetSak
                hentetSak.sak.id shouldBe sakOpprettetHendelse.sakId
                hentetSak.sak.sisteStatus shouldBe QuerySak.SakStatus.UNDER_BEHANDLING
            }
        }
    }

    describe("hentSakGrupperingsid") {
        val database = testDatabase(Produsent.databaseConfig)
        val produsentRepository = ProdusentRepositoryImpl(database)
        val engine = ktorProdusentTestServer(produsentRepository = produsentRepository)

        produsentRepository.oppdaterModellEtterHendelse(sakOpprettetHendelse)
        produsentRepository.oppdaterModellEtterHendelse(nyStatusSakHendelse)
        produsentRepository.oppdaterModellEtterHendelse(underbehandlingStatusSakHendelse)
        produsentRepository.oppdaterModellEtterHendelse(sakMedAnnenMerkelappOpprettetHendelse)

        it("Sak finnes ikke og respons inneholder error.") {
            engine.produsentApi(
                """
                    query {
                        hentSakMedGrupperingsid(grupperingsid: "${UUID.randomUUID()}" merkelapp: "Fager") {
                            __typename
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                """.trimIndent()
            ).getTypedContent<Error.SakFinnesIkke>("hentSakMedGrupperingsid")
        }
        it("Sak med annen merkelapp gir UgyldigMerkelapp") {
            engine.produsentApi(
                """
                    query {
                        hentSakMedGrupperingsid(grupperingsid: "${sakMedAnnenMerkelappOpprettetHendelse.grupperingsid}" merkelapp: "IkkeTag") {
                            __typename
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                """.trimIndent()
            ).getTypedContent<Error.UgyldigMerkelapp>("hentSakMedGrupperingsid")
        }
        it("Sak finnes og response inneholder saken") {
            engine.produsentApi(
                """
                    query {
                        hentSakMedGrupperingsid(grupperingsid: "${sakOpprettetHendelse.grupperingsid}" merkelapp: "tag") {
                            __typename
                            ... on HentetSak {
                                sak {
                                    id
                                    grupperingsid
                                    virksomhetsnummer
                                    tittel
                                    lenke
                                    merkelapp
                                    sisteStatus
                                }
                            }
                        }
                    }
                """.trimIndent()
            ).getTypedContent<QuerySak.HentSakResultat>("hentSakMedGrupperingsid").also { HentetSak ->
                val hentetSak = HentetSak as QuerySak.HentetSak
                hentetSak.sak.id shouldBe sakOpprettetHendelse.sakId
                hentetSak.sak.sisteStatus shouldBe QuerySak.SakStatus.UNDER_BEHANDLING
            }
        }
    }
})

