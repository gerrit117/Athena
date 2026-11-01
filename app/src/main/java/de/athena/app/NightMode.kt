package de.athena.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Thresholds use elapsed time, so HA clock corrections do not change the delay. */
class NightPolicy {
    var active=false; private set
    private var darkSince: Long?=null
    private var lightSince: Long?=null
    fun update(mode: String,lux: Float?,threshold: Float,delaySeconds: Int,now: Long): Boolean {
        if(mode!="auto") {
            active=mode=="always";darkSince=null;lightSince=null
            return active
        }
        if(lux==null || !lux.isFinite() || lux<0f) {active=false;darkSince=null;lightSince=null;return false}
        if(!active) {
            lightSince=null
            if(lux<=threshold) {
                if(darkSince==null) darkSince=now
                if(now-darkSince!!>=delaySeconds*1000L) {active=true;darkSince=null}
            } else darkSince=null
        } else {
            if(lux>=threshold*1.5f+2f) {
                if(lightSince==null)lightSince=now
                if(now-lightSince!!>=3000L) {active=false;lightSince=null}
            } else lightSince=null
        }
        return active
    }
}

class AmbientLight(context: Context,private val changed: (Float)->Unit): SensorEventListener {
    private val manager=context.getSystemService(SensorManager::class.java)
    private val sensor=manager.getDefaultSensor(Sensor.TYPE_LIGHT)
    var available=false;private set
    fun start() {available=sensor?.let {manager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)} ?: false}
    fun close() {manager.unregisterListener(this);available=false}
    override fun onSensorChanged(event: SensorEvent) {event.values.firstOrNull()?.takeIf {it.isFinite() && it>=0f}?.let(changed)}
    override fun onAccuracyChanged(sensor: Sensor?,accuracy: Int) = Unit
}
