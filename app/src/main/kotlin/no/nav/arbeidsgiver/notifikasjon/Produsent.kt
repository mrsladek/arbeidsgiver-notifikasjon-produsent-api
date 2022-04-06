package no.nav.arbeidsgiver.notifikasjon

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.JWTAuthentication
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.extractProdusentContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.httpServerSetup
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.forEachHendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.PRODUSENT_REGISTER
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import java.time.Duration

object Produsent {
    val log = logger()
    val databaseConfig = Database.config("produsent_model")

    private val defaultAuthProviders = when (val name = System.getenv("NAIS_CLUSTER_NAME")) {
        "prod-gcp" -> listOf(
            HttpAuthProviders.AZURE_AD,
        )
        "dev-gcp" -> listOf(
            HttpAuthProviders.AZURE_AD,
            HttpAuthProviders.FAKEDINGS_PRODUSENT,
        )
        null -> listOf(
            HttpAuthProviders.FAKEDINGS_PRODUSENT,
        )
        else -> {
            val msg = "ukjent NAIS_CLUSTER_NAME '$name'"
            log.error(msg)
            throw Error(msg)
        }
    }

    fun main(
        authProviders: List<JWTAuthentication> = defaultAuthProviders,
        httpPort: Int = 8080,
        produsentRegister: ProdusentRegister = PRODUSENT_REGISTER,
        altinn: Altinn = AltinnImpl(),
    ) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val produsentRepositoryAsync = async {
                ProdusentRepositoryImpl(database.await())
            }

            launch {
                val produsentRepository = produsentRepositoryAsync.await()
                forEachHendelse("produsent-model-builder") { event ->
                    produsentRepository.oppdaterModellEtterHendelse(event)
                }
            }

            val graphql = async {
                ProdusentAPI.newGraphQL(
                    kafkaProducer = createKafkaProducer(),
                    produsentRepository = produsentRepositoryAsync.await(),
                )
            }

            launch {
                val httpServer = embeddedServer(Netty, port = httpPort, configure = {
                    connectionGroupSize = 16
                    callGroupSize = 16
                    workerGroupSize = 16
                }) {
                    httpServerSetup(
                        authProviders = authProviders,
                        extractContext = extractProdusentContext(produsentRegister),
                        graphql = graphql
                    )
                }
                httpServer.start(wait = true)
            }
            launch {
                launchProcessingLoop(
                    "last Altinnroller",
                    pauseAfterEach = Duration.ofDays(1),
                ) {
                    AltinnRolleServiceImpl(altinn, produsentRepositoryAsync.await().altinnRolle).lastRollerFraAltinn()
                }
            }
        }
    }
}