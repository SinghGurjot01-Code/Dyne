package com.dyne.integrations

/** A system integration that is started only while its feature is enabled and permitted. */
interface Integration {
    fun start()
    fun stop()
}
