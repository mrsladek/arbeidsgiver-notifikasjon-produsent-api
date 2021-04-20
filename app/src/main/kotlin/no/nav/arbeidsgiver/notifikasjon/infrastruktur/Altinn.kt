package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.notifikasjon.Tilgang
import org.slf4j.LoggerFactory

interface Altinn {
    fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<Tilgang>
}

private val VÅRE_TJENESTER = setOf(
    "5216" to "1", // Mentortilskudd
    "5212" to "1", // Inkluderingstilskudd
    "5384" to "1", // Ekspertbistand
    "5159" to "1", // Lønnstilskudd
    "4936" to "1", // Inntektsmelding
    "5332" to "2", // Arbeidstrening
    "5332" to "1", // Arbeidstrening
    "5441" to "1", // Arbeidsforhold
    "5516" to "1", // Midlertidig lønnstilskudd
    "5516" to "2", // Varig lønnstilskudd'
    "3403" to "2", // Sykfraværsstatistikk
    "5078" to "1", // Rekruttering
    "5278" to "1"  // Tilskuddsbrev om NAV-tiltak
)
private val log = LoggerFactory.getLogger("Altinn")!!
fun AltinnrettigheterProxyKlient.hentTilganger(
    fnr: String,
    serviceCode: String,
    serviceEdition: String,
    selvbetjeningsToken: String,
): List<Tilgang> =
    try {
        this.hentOrganisasjoner(
            SelvbetjeningToken(selvbetjeningsToken),
            Subject(fnr),
            ServiceCode(serviceCode),
            ServiceEdition(serviceEdition),
            false
        )
            .filter { it.type != "Enterprise" }
            .filter {
                if (it.organizationNumber == null) {
                    log.warn("filtrerer ut reportee uten organizationNumber")
                    false
                } else {
                    true
                }

            }
            .map {
                Tilgang(
                    virksomhet = it.organizationNumber!!,
                    servicecode = serviceCode,
                    serviceedition = serviceEdition
                )
            }
    } catch (error: Exception) {
        if (error.message?.contains("403") == true)
            emptyList()
        else
            throw error
    }


fun AltinnrettigheterProxyKlient.hentAlleTilganger(
    fnr: String,
    selvbetjeningsToken: String,
): List<Tilgang> =
    VÅRE_TJENESTER.flatMap {
        hentTilganger(fnr, it.first, it.second, selvbetjeningsToken)
    }

object AltinnImpl : Altinn {
    private val altinnrettigheterProxyKlient = AltinnrettigheterProxyKlient(
        AltinnrettigheterProxyKlientConfig(
            ProxyConfig(
                url = "https://arbeidsgiver.dev.nav.no/altinn-rettigheter-proxy",
                consumerId = "arbeidsgiver-arbeidsforhold-api",
            ),
            AltinnConfig(
                url = "https://api-gw-q1.oera.no/", //TODO finn riktig måte å fallbacke på i gcp
                altinnApiKey = System.getenv("ALTINN_HEADER") ?: "default",
                altinnApiGwApiKey = System.getenv("APIGW_HEADER") ?: "default",
            )
        )
    )

    override fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<Tilgang> =
        altinnrettigheterProxyKlient.hentAlleTilganger(fnr, selvbetjeningsToken)
}

