import React from 'react'
import {ApolloProvider} from "@apollo/client";
import styles from './styles.module.css'
import NotifikasjonWidgetComponent from './NotifikasjonWidget/NotifikasjonWidget'
import {createClient} from "./api/graphql";

interface Props {
  text: string
}

export const NotifikasjonWidget = ({ text }: Props) => {
  return (
    <div className={styles.test}>
      til eksempel: {text}
      <ApolloProvider client={createClient()}>
        <NotifikasjonWidgetComponent />
      </ApolloProvider>
    </div>
  )
}
