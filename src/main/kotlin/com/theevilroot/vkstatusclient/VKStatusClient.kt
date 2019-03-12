package com.theevilroot.vkstatusclient

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.client.Socket.*

private const val socketPort = 5101
private const val serverPort = 5100
private const val EVENT_EVENT = "event"

class VKStatusClient (
    private val host: String,
    private val jsonParser: JsonParser,
    private val gson: Gson
) {

    private lateinit var clientSocket: Socket

    /**
     * Event bus for socket events (Socket.EVENT_CONNECTING etc.)
     */
    private val systemBus: PublishSubject<String> = PublishSubject.create()

    /**
     * Event bus for events coming from socket
     */
    private val eventBus: PublishSubject<Event> = PublishSubject.create()

    /**
     * [!] Warning
     *  Asyncronious operation
     *  Listen system bus for CONNECTED event
     */
    fun connect() {
        clientSocket = IO.socket("http://$host:$socketPort")

        clientSocket.on(arrayOf(
            EVENT_CONNECTING,
            EVENT_CONNECT,
            EVENT_CONNECT_ERROR,
            EVENT_CONNECT_TIMEOUT,
            EVENT_DISCONNECT,
            EVENT_ERROR,
            EVENT_PING,
            EVENT_PONG,
            EVENT_RECONNECT,
            EVENT_RECONNECTING,
            EVENT_RECONNECT_ERROR,
            EVENT_RECONNECT_FAILED
        )) { event, _ -> systemBus.onNext(event) }

        clientSocket.on(EVENT_EVENT) { array ->
            array
                .filter { it is String }
                .mapNotNull(this::parseOrNull)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map { gson.fromJson(it, Event::class.java) }
                .forEach(eventBus::onNext)
        }

        clientSocket.connect()
    }

    /**
     * [!] Warning
     *  Asyncronious operation!
     *  After call listen system bus for DISCONNECT event
     */
    fun disconnect() {
        if (!isConnected()) {
            return
        }

        clientSocket.disconnect()
    }

    fun isConnected(): Boolean {
        return this::clientSocket.isInitialized && clientSocket.connected()
    }

    fun systemBus(): Observable<String> {
        return systemBus
    }

    fun eventBus(): Observable<Event> {
        return eventBus
    }

    private fun Socket.on(events: Array<String>, listener: (String, Array<Any>) -> Unit) {
        for (event in events) {
            this.on(event) { listener(event, it) }
        }
    }

    private fun parseOrNull(any: Any): JsonElement? {
        return try {
            jsonParser.parse(any as String)
        } catch (e: Exception) {
            null
        }
    }

}