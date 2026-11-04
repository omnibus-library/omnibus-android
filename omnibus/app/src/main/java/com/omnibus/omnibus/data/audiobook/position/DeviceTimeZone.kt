package com.omnibus.omnibus.data.audiobook.position

import java.util.TimeZone
import javax.inject.Inject

/** The device's time zone, read at each call so a zone change during a trip is picked up (I-SESS-1). */
fun interface DeviceTimeZone {
    /** The zone the device is in now. */
    fun current(): TimeZone
}

/** Reads the zone from the platform. Uses `java.util.TimeZone` only, since `java.time` needs desugaring below API 26. */
class SystemDeviceTimeZone @Inject constructor() : DeviceTimeZone {
    override fun current(): TimeZone = TimeZone.getDefault()
}
