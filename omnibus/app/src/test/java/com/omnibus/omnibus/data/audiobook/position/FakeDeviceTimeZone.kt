package com.omnibus.omnibus.data.audiobook.position

import java.util.TimeZone

/** A [DeviceTimeZone] that tests set by hand. Starts at UTC. */
class FakeDeviceTimeZone(var zone: TimeZone = TimeZone.getTimeZone("UTC")) : DeviceTimeZone {
    override fun current(): TimeZone = zone
}
