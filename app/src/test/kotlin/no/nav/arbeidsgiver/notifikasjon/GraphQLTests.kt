package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.convertValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import org.apache.kafka.clients.producer.Producer
import java.util.*

data class GraphQLError(
    val message: String,
    val locations: Any,
    val extensions: Map<String, Any>?
)

inline fun <reified T> TestApplicationResponse.getTypedContent(name: String): T{
    val errors = getGraphqlErrors()
    if (errors.isEmpty()) {
        val tree = objectMapper.readTree(this.content!!)
        val node = tree.get("data").get(name)
        return objectMapper.convertValue(node)
    } else {
        throw Exception("Got errors $errors")
    }
}

fun TestApplicationResponse.getGraphqlErrors(): List<GraphQLError> {
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = objectMapper.readTree(this.content!!)
    val errors = tree.get("errors")
    return if (errors == null) emptyList() else objectMapper.convertValue(errors)
}

class GraphQLTests : DescribeSpec({
    val engine by ktorEngine()

    describe("POST /api/graphql") {
        lateinit var response: TestApplicationResponse
        lateinit var query: String

        beforeEach {
            mockkStatic(Producer<KafkaKey, Event>::sendEvent)
            response = engine.post("/api/graphql") {
                addHeader(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader(HttpHeaders.Accept, "application/json")
                setJsonBody(GraphQLRequest(
                    query = query
                ))
            }
        }
        context("Mutation.nyBeskjed") {
            query = """
                mutation {
                    nyBeskjed(nyBeskjed: {
                        lenke: "http://foo.bar",
                        tekst: "hello world",
                        merkelapp: "tag",
                        eksternId: "heu",
                        mottaker: {
                            fnr: {
                                fodselsnummer: "12345678910",
                                virksomhetsnummer: "42"
                            } 
                        }
                    }) {
                        id
                    }
                }
            """.trimIndent()

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            context("respons er parsed som BeskjedResultat") {
                lateinit var resultat: BeskjedResultat

                beforeEach {
                    resultat = response.getTypedContent<BeskjedResultat>("nyBeskjed")
                }

                it("id er gitt") {
                    resultat.id shouldNot beBlank()
                }

                it("sends message to kafka") {
                    val eventSlot = slot<BeskjedOpprettet>()
                    verify {
                        any<Producer<KafkaKey, Event>>().sendEvent(any(), capture(eventSlot))
                    }
                    val event = eventSlot.captured
                    event.guid.toString() shouldBe  resultat.id
                    event.lenke shouldBe "http://foo.bar"
                    event.tekst shouldBe "hello world"
                    event.merkelapp shouldBe "tag"
                    event.mottaker shouldBe FodselsnummerMottaker(
                        fodselsnummer = "12345678910",
                        virksomhetsnummer = "42"
                    )
                }
            }
        }
    }
})
