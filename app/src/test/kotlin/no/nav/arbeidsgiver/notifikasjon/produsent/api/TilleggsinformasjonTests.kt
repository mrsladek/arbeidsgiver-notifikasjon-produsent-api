package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*

class TilleggsinformasjonTests: DescribeSpec({
    describe("Oppretter ny sak med tilleggsinformasjon") {
        val (produsentRepository, _, engine) = setupEngine()
        val sakUtenTilleggsinformasjon = engine.nySak(uuid("1").toString())
        val sakMedTilleggsinformasjon = engine.nySak(uuid("2").toString(),"foo")

        val sakUtenTilleggsinformasjonID = sakUtenTilleggsinformasjon.getTypedContent<UUID>("$.nySak.id")
        val sakMedTilleggsinformasjonID = sakMedTilleggsinformasjon.getTypedContent<UUID>("$.nySak.id")

        it("should be successfull") {
            sakUtenTilleggsinformasjon.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
            sakMedTilleggsinformasjon.getTypedContent<String>("$.nySak.__typename") shouldBe "NySakVellykket"
        }

        it("should not have tilleggsinformasjon") {
            val sak = produsentRepository.hentSak(sakUtenTilleggsinformasjonID)!!
            sak.tilleggsinformasjon shouldBe null;
        }

        it("should have tilleggsinformasjon") {
            val sak = produsentRepository.hentSak(sakMedTilleggsinformasjonID)!!
            sak.tilleggsinformasjon shouldBe "foo";
        }
    }

    describe("Endrer tilleggsinformasjon på eksisterende sak") {
        val (produsentRepository, _, engine) = setupEngine()
        val sak = engine.nySak(uuid("1").toString(),null)
        val sakID = sak.getTypedContent<UUID>("$.nySak.id")
        val idempotencyKey1 = uuid("2").toString()
        val idempotencyKey2 = uuid("3").toString()

        val tilleggsinformasjon1 = engine.endreTilleggsinformasjon(sakID, "foo", idempotencyKey1)

        it("Endrer tilleggsinformasjon med ny idempontency key") {
            tilleggsinformasjon1.getTypedContent<String>("$.tilleggsinformasjonSak.__typename") shouldBe "TilleggsinformasjonSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.tilleggsinformasjon shouldBe "foo";
        }
        it("Forsøker endre tilleggsinformasjon med samme idempontency key og forventer ingen endring") {
            engine.endreTilleggsinformasjon(sakID, "bar", idempotencyKey1)
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.tilleggsinformasjon shouldBe "foo";
        }
        it ("Endrere med ny idempontency key og forventer endring") {
            val tilleggsinformasjon2 = engine.endreTilleggsinformasjon(sakID, "baz", idempotencyKey2)
            tilleggsinformasjon2.getTypedContent<String>("$.tilleggsinformasjonSak.__typename") shouldBe "TilleggsinformasjonSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.tilleggsinformasjon shouldBe "baz";
        }
        it ("Endrer tilleggsinformasjon til null") {
            val tilleggsinformasjon3 = engine.endreTilleggsinformasjon(sakID, null, uuid("4").toString())
            tilleggsinformasjon3.getTypedContent<String>("$.tilleggsinformasjonSak.__typename") shouldBe "TilleggsinformasjonSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.tilleggsinformasjon shouldBe null;
        }
        it ("Endrer tilleggsinformasjon uten idempontency key") {
            val tilleggsinformasjon4 = engine.endreTilleggsinformasjon(sakID, "foo", null)
            tilleggsinformasjon4.getTypedContent<String>("$.tilleggsinformasjonSak.__typename") shouldBe "TilleggsinformasjonSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.tilleggsinformasjon shouldBe "foo";
        }
        it ("Endrer til null uten idempontency key") {
            val tilleggsinformasjon5 = engine.endreTilleggsinformasjon(sakID, null, null)
            tilleggsinformasjon5.getTypedContent<String>("$.tilleggsinformasjonSak.__typename") shouldBe "TilleggsinformasjonSakVellykket"
            val hentetSak = produsentRepository.hentSak(sakID)!!
            hentetSak.tilleggsinformasjon shouldBe null;
        }
    }
})


private fun DescribeSpec.setupEngine(): Triple<ProdusentRepositoryImpl, FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)
    val hendelseProdusent = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = hendelseProdusent,
        produsentRepository = produsentRepository,
    )
    return Triple(produsentRepository, hendelseProdusent, engine)
}



private fun TestApplicationEngine.nySak(
    grupperingsid: String,
    tilleggsinformasjon : String? = null,
) =
    produsentApi(
        """
            mutation {
                nySak(
                    virksomhetsnummer: "1"
                    merkelapp: "tag"
                    grupperingsid: "$grupperingsid"
                    mottakere: [{
                        altinn: {
                            serviceCode: "5441"
                            serviceEdition: "1"
                        }
                    }]
                    initiellStatus: ${SaksStatus.MOTTATT}
                    tittel: "Foo"
                    ${if (tilleggsinformasjon == null) "" else "tilleggsinformasjon: \"$tilleggsinformasjon\""}
                    lenke: ${null}
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """
    )


private fun TestApplicationEngine.endreTilleggsinformasjon(
    id: UUID,
    tilleggsinformasjon: String?,
    idempotencyKey: String?,
) =
    produsentApi(
        """
            mutation {
                tilleggsinformasjonSak(
                    id: "$id"
                    ${if (tilleggsinformasjon == null) "" else "tilleggsinformasjon: \"$tilleggsinformasjon\""}
                    ${if (idempotencyKey == null) "" else "idempotencyKey: \"$idempotencyKey\""}
                ) {
                    __typename
                    ... on TilleggsinformasjonSakVellykket {
                        id
                    }
                    ... on Error {
                        feilmelding
                    }
                }
            }
        """
    )

