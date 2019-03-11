package com.theevilroot.vkstatusclient

import com.google.gson.annotations.SerializedName
import java.util.*


// Event: {"user_id":436146371,"first_name":"Максим","last_name":"Иосифов","time":1552286858974.975,"old":1,"new":0}
data class Event (
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("first_name")
    val firstName: String,
    @SerializedName("last_name")
    val lastName: String,
    @SerializedName("time")
    private val time: Double,
    @SerializedName("old")
    private val old: Int,
    @SerializedName("new")
    private val new: Int
) {
    val oldStatus: Boolean = old == 1
    val newStatus: Boolean = new == 1
    val date: Date = Date(time.toLong())
}