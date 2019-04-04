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
private const val EVENT_ACTION_SUB = "client_sub"
private const val EVENT_ACTION_SUB_ALL = "client_sub_all"
private const val EVENT_ACTION_UN_SUB = "client_unsub"
private const val EVENT_ACTION_UN_SUB_ALL = "client_unsub_all"

class VKStatusClient (
    private val host: String,
    private val jsonParser: JsonParser,
    private val gson: Gson
) {
    companion object {
        val EVENT_EVENT = "event"
        val EVENT_AUTH = "auth"
        val EVENT_SUBS_LIST = "client_subs"
        val EVENT_AUTH_START = "auth_start"
        val EVENT_AUTH_SUCCESS = "auth_success"
        val EVENT_AUTH_FAILED = "auth_failed"
    }

    private lateinit var clientSocket: Socket
    private var clientId: Int = 0
    private var isAuthenticated: Boolean = false
    private val subscription: ArrayList<Int> = ArrayList()
    private var allSubscription: Boolean = false

    /**
     * Event bus for socket events (Socket.EVENT_CONNECTING etc.)
     */
    private val systemBus: PublishSubject<SystemEvent> = PublishSubject.create()

    /**
     * Event bus for events coming from socket
     */
    private val eventBus: PublishSubject<Event> = PublishSubject.create()

    /**
     * [!] Warning
     *  Asyncronious operation
     *  Listen system bus for CONNECTED event
     */
    fun connect(clientId: Int) {
        this.clientId = clientId
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
        )) { event, _ ->
            systemBus.onNext(event.event())

            if(event == EVENT_CONNECT) {
                systemBus.onNext(EVENT_AUTH_START.event())
                auth(true)
            }
        }

        clientSocket.on(EVENT_EVENT) { array ->
            array
                .filter { it is String }
                .mapNotNull(this::parseOrNull)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map { gson.fromJson(it, Event::class.java) }
                .forEach(eventBus::onNext)
        }

        clientSocket.on(EVENT_AUTH) { array ->
            array
                .filter { it is String }
                .map { it as String }
                .forEach {
                    if (it.startsWith("Error")) {
                        systemBus.onNext(EVENT_AUTH_FAILED.event(it))
                    } else {
                        systemBus.onNext(EVENT_AUTH_SUCCESS.event(it))
                        isAuthenticated = true
                        syncSubs()
                    }
                }
        }

        clientSocket.on(EVENT_SUBS_LIST) { array ->
            subscription.clear()
            array.mapNotNull { it.toString().toIntOrNull() }.forEach {
                if (it < 0) {
                    allSubscription = true
                } else {
                    subscription.add(it)
                }
            }

            systemBus.onNext(EVENT_SUBS_LIST.event())
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

    fun auth(forceReAuth: Boolean = false) {
        if (!isConnected() || (isAuthenticated && !forceReAuth)) return

        clientSocket.emit("client_auth", clientId)
    }

    fun syncSubs() {

        if (!isConnected() || !isAuthenticated) return

        clientSocket.emit(EVENT_SUBS_LIST, clientId)
    }

    fun subscribe(userId: Int) {
        if (!isConnected() || !isAuthenticated) return

        clientSocket.emit(EVENT_ACTION_SUB, userId)
    }

    fun subscribeAll() {
        if (!isConnected() || !isAuthenticated) return

        clientSocket.emit(EVENT_ACTION_SUB_ALL, clientId)
    }

    fun unsubscribe(userId: Int) {
        if (!isConnected() || !isAuthenticated) return

        clientSocket.emit(EVENT_ACTION_UN_SUB, userId)
    }

    fun unsubscribeAll() {
        if (!isConnected() || !isAuthenticated) return

        clientSocket.emit(EVENT_ACTION_UN_SUB_ALL, clientId)
    }

    fun isConnected(): Boolean {
        return this::clientSocket.isInitialized && clientSocket.connected()
    }

    fun isReady(): Boolean {
        return isConnected() && isAuthenticated
    }

    fun systemBus(): Observable<SystemEvent> {
        return systemBus
    }

    fun subscriptions(): List<Int> {
        return subscription
    }

    fun isAllSubscribed(): Boolean {
        return allSubscription
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

    private fun String.event(message: String? = null): SystemEvent =
            SystemEvent(this, message)

}