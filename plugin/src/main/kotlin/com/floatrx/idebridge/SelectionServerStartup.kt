package com.floatrx.idebridge

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.diagnostic.Logger

/**
 * Application lifecycle listener that starts the HTTP server when IDE launches.
 */
class SelectionServerStartup : AppLifecycleListener {
    private val logger = Logger.getInstance(SelectionServerStartup::class.java)
    private var server: SelectionServer? = null

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        logger.info("IDE Bridge: Starting selection server...")
        server = SelectionServer()
        server?.start()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        logger.info("IDE Bridge: Stopping selection server...")
        server?.stop()
        server = null
    }
}
