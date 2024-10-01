package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

internal class MutationNyStatusSak(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {

    private fun DataFetchingEnvironment.getStatus() =
        NyStatusSakInput(
            status = SaksStatusInput(
                status = getTypedArgument("nyStatus"),
                tidspunkt = getTypedArgumentOrNull("tidspunkt"),
                overstyrStatustekstMed = getTypedArgumentOrNull("overstyrStatustekstMed"),
            ),
            idempotencyKey = getTypedArgumentOrNull("idempotencyKey"),
            hardDelete = getTypedArgumentOrNull("hardDelete"),
            nyLenkeTilSak = getTypedArgumentOrNull("nyLenkeTilSak"),
        )

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NyStatusSakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nyStatusSak") { env ->
                nyStatusSak(
                    context = env.notifikasjonContext(),
                    id = env.getTypedArgument("id"),
                    status = env.getStatus(),
                )
            }
            coDataFetcher("nyStatusSakByGrupperingsid") { env ->
                nyStatusSakByGrupperingsid(
                    context = env.notifikasjonContext(),
                    grupperingsid = env.getTypedArgument("grupperingsid"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                    status = env.getStatus(),
                )
            }
        }
    }

    data class NyStatusSakInput(
        val status: SaksStatusInput,
        val idempotencyKey: String?,
        val hardDelete: HardDeleteUpdateInput?,
        val nyLenkeTilSak: String?,
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyStatusSakResultat

    @JsonTypeName("NyStatusSakVellykket")
    data class NyStatusSakVellykket(
        val id: UUID,
        val statuser: List<StatusOppdatering>,
    ): NyStatusSakResultat

    data class StatusOppdatering(
        val status: SaksStatus,
        val tidspunkt: OffsetDateTime,
        val overstyrStatusTekstMed: String?,
    )


    private suspend fun nyStatusSak(
        context: ProdusentAPI.Context,
        id: UUID,
        status: NyStatusSakInput,
    ): NyStatusSakResultat {
        val sak = produsentRepository.hentSak(id)
            ?: return Error.SakFinnesIkke("sak med id=$id finnes ikke")
        return nyStatusSak(context = context, sak = sak, status = status)
    }

    private suspend fun nyStatusSakByGrupperingsid(
        context: ProdusentAPI.Context,
        grupperingsid: String,
        merkelapp: String,
        status: NyStatusSakInput,
    ): NyStatusSakResultat {
        val sak = produsentRepository.hentSak(merkelapp = merkelapp, grupperingsid = grupperingsid)
            ?: return Error.SakFinnesIkke("sak med merkelapp='$merkelapp' og grupperingsid='$grupperingsid' finnes ikke")
        return nyStatusSak(context = context, sak = sak, status = status)
    }

    private suspend fun nyStatusSak(
        context: ProdusentAPI.Context,
        sak: ProdusentModel.Sak,
        status: NyStatusSakInput,
    ) : NyStatusSakResultat {
        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(
            produsent,
            sak.merkelapp,
        ) { error -> return error }

        val hendelseId = UUID.randomUUID()

        val idempotencyKey =
            if (status.idempotencyKey != null)
                IdempotenceKey.userSupplied(status.idempotencyKey)
            else
                IdempotenceKey.generated(hendelseId)

        val existing = sak.statusoppdateringer.find {
            it.idempotencyKey == idempotencyKey
        }

        val eksisterendeOppdateringer = sak.statusoppdateringer.map {
            StatusOppdatering(
                status = when (it.status) {
                    HendelseModel.SakStatus.MOTTATT -> SaksStatus.MOTTATT
                    HendelseModel.SakStatus.UNDER_BEHANDLING -> SaksStatus.UNDER_BEHANDLING
                    HendelseModel.SakStatus.FERDIG -> SaksStatus.FERDIG
                },
                tidspunkt = it.tidspunktOppgitt ?: it.tidspunktMottatt,
                overstyrStatusTekstMed = it.overstyrStatustekstMed,
            )
        }

        return when {
            existing == null -> {
                val nyStatusSakHendelse = NyStatusSak(
                    hendelseId = hendelseId,
                    virksomhetsnummer = sak.virksomhetsnummer,
                    produsentId = produsent.id,
                    kildeAppNavn = context.appName,
                    sakId = sak.id,
                    status = status.status.status.hendelseType,
                    overstyrStatustekstMed = status.status.overstyrStatustekstMed,
                    oppgittTidspunkt = status.status.tidspunkt,
                    mottattTidspunkt = OffsetDateTime.now(),
                    idempotensKey = idempotencyKey,
                    hardDelete = status.hardDelete?.tilHendelseModel(),
                    nyLenkeTilSak = status.nyLenkeTilSak,
                )

                hendelseDispatcher.send(nyStatusSakHendelse)
                NyStatusSakVellykket(
                    id = hendelseId,
                    statuser = (listOf(
                        StatusOppdatering(
                            status = status.status.status,
                            tidspunkt = status.status.tidspunkt ?: nyStatusSakHendelse.mottattTidspunkt,
                            overstyrStatusTekstMed = status.status.overstyrStatustekstMed,
                        )
                    ) + eksisterendeOppdateringer).sortedByDescending { it.tidspunkt }
                )
            }
            status.status.isDuplicateOf(existing) -> {
                NyStatusSakVellykket(
                    id = existing.id,
                    statuser = (listOf(
                        StatusOppdatering(
                            status = when (existing.status) {
                                HendelseModel.SakStatus.MOTTATT -> SaksStatus.MOTTATT
                                HendelseModel.SakStatus.UNDER_BEHANDLING -> SaksStatus.UNDER_BEHANDLING
                                HendelseModel.SakStatus.FERDIG -> SaksStatus.FERDIG
                            },
                            tidspunkt = existing.tidspunktOppgitt ?: existing.tidspunktMottatt,
                            overstyrStatusTekstMed = existing.overstyrStatustekstMed,
                        )
                    ) + eksisterendeOppdateringer).sortedByDescending { it.tidspunkt })
            }
            else -> {
                Error.Konflikt(
                    feilmelding = "statusoppdatering med idempotency eksisterer, men er annerledes"
                )
            }
        }
    }
}

fun SaksStatusInput.isDuplicateOf(existing: ProdusentModel.SakStatusOppdatering): Boolean {
    return this.status.hendelseType == existing.status
            && this.overstyrStatustekstMed == existing.overstyrStatustekstMed
            && this.tidspunkt == existing.tidspunktOppgitt
}

