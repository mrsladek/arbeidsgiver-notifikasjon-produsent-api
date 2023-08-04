import React, { FC, ReactNode } from 'react'
import { Tag } from '@navikt/ds-react'
import './StatusLinje.css'
import { Notifikasjon, OppgaveTilstand } from '../../../api/graphql-types'
import { StopWatch } from '@navikt/ds-icons'
import { formatterDato, uformellDatotekst } from '../dato-funksjoner'

export interface StatusLinjeProps {
  notifikasjon: Notifikasjon
}

export const StatusLinje: FC<StatusLinjeProps> = ({ notifikasjon }) => {
  if (notifikasjon.__typename !== 'Oppgave') {
    return null
  }

  switch (notifikasjon.tilstand) {
    case OppgaveTilstand.Utfoert:
      return (
        <Tag size="small" className="notifikasjon_StatusLinje" variant='success'>
            Fullført {notifikasjon.utfoertTidspunkt ? uformellDatotekst(new Date(notifikasjon.utfoertTidspunkt)) : null}
        </Tag>
      )

    case OppgaveTilstand.Utgaatt:
      return (
        <Tag size="small" className="notifikasjon_StatusLinje" variant='neutral'>
          <StatusIkonMedTekst icon={<StopWatch aria-hidden={true} />}>
            Fristen gikk ut {uformellDatotekst(new Date(notifikasjon.utgaattTidspunkt))}
          </StatusIkonMedTekst>
        </Tag>
      )

    case OppgaveTilstand.Ny:
      if (!notifikasjon.frist && !notifikasjon.paaminnelseTidspunkt) {
        return null
      } else {
        let innhold
        if (!notifikasjon.frist && notifikasjon.paaminnelseTidspunkt) {
          innhold = <>Påminnelse</>
        } else if (notifikasjon.frist && !notifikasjon.paaminnelseTidspunkt) {
          innhold = <>Frist {formatterDato(new Date(notifikasjon.frist))}</>
        } else {
          innhold = <>Påminnelse &ndash; Frist {formatterDato(new Date(notifikasjon.frist))}</>
        }
        return <StatusMedFristPaminnelse> {innhold} </StatusMedFristPaminnelse>
      }
    default:
      return null
  }
}

type StatusMedFristPaminnelseProps = {
  children: ReactNode
}

const StatusMedFristPaminnelse = ({ children }: StatusMedFristPaminnelseProps) => {
  return <Tag size="small" className="notifikasjon_StatusLinje" variant='warning'>
    <StatusIkonMedTekst icon={<StopWatch aria-hidden={true} />}>
      {children}
    </StatusIkonMedTekst>
  </Tag>
}

type StatusIkonMedTekstProps = {
  icon: ReactNode;
  className?: string;
  children: ReactNode;
}

const StatusIkonMedTekst: FC<StatusIkonMedTekstProps> = ({ icon, className, children }) =>
  <span className={`notifikasjon_oppgave_status_text ${className}`}>
    {icon} {children}
  </span>
