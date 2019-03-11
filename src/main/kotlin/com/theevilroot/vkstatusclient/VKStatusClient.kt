package com.theevilroot.vkstatusclient

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.reactivex.subjects.PublishSubject
import io.socket.client.IO
import io.socket.client.Socket

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
    val systemBus: PublishSubject<String> = PublishSubject.create()

    /**
     * Event bus for events coming from socket
     */
    val eventBus: PublishSubject<Event> = PublishSubject.create()

    fun connect() {
        clientSocket = IO.socket("http://$host:$socketPort")

        clientSocket.on(Socket.EVENT_CONNECTING) { systemBus.onNext(Socket.EVENT_CONNECTING) }
        clientSocket.on(Socket.EVENT_CONNECT) { systemBus.onNext(Socket.EVENT_CONNECT) }
        clientSocket.on(Socket.EVENT_CONNECT_ERROR) { systemBus.onNext(Socket.EVENT_CONNECT_ERROR) }
        clientSocket.on(Socket.EVENT_CONNECT_TIMEOUT) { systemBus.onNext(Socket.EVENT_CONNECT_TIMEOUT) }
        clientSocket.on(Socket.EVENT_DISCONNECT) { systemBus.onNext(Socket.EVENT_DISCONNECT) }
        clientSocket.on(Socket.EVENT_ERROR) { systemBus.onNext(Socket.EVENT_ERROR) }
        clientSocket.on(Socket.EVENT_PING) { systemBus.onNext(Socket.EVENT_PING) }
        clientSocket.on(Socket.EVENT_PONG) { systemBus.onNext(Socket.EVENT_PONG) }
        clientSocket.on(Socket.EVENT_RECONNECT) { systemBus.onNext(Socket.EVENT_RECONNECT) }
        clientSocket.on(Socket.EVENT_RECONNECTING) { systemBus.onNext(Socket.EVENT_RECONNECTING) }
        clientSocket.on(Socket.EVENT_RECONNECT_ERROR) { systemBus.onNext(Socket.EVENT_RECONNECT_ERROR) }
        clientSocket.on(Socket.EVENT_RECONNECT_FAILED) { systemBus.onNext(Socket.EVENT_RECONNECT_FAILED) }

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

    private fun parseOrNull(any: Any): JsonElement? {
        return try {
            jsonParser.parse(any as String)
        } catch (e: Exception) {
            null
        }
    }

    fun isConnected(): Boolean {
        return this::clientSocket.isInitialized && clientSocket.connected()
    }

}