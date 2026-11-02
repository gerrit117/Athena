package de.athena.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    @Test fun friendlyNamesWinAndRegistryNamesReplaceTechnicalFallbacks() {
        assertEquals("Deckenlicht",Entity("light.a","on",JSONObject().put("friendly_name","Deckenlicht"),"Alt","Lampe").name)
        assertEquals("Lampe · Helligkeit",Entity("sensor.a","1",JSONObject().put("friendly_name",JSONObject.NULL),"Lampe · Helligkeit","Lampe").name)
        assertEquals("Lampe",Entity("light.a","on",JSONObject().put("friendly_name",""),"Lampe").name)
        assertEquals("front door",Entity("binary_sensor.front_door","off").name)
    }
    @Test fun pairingCodeIsFourDigitsSingleUseAndExpires() {
        var now=1000L;val gate=PairingGate {now}
        val pin=gate.open();assertTrue(pin.matches(Regex("[0-9]{4}")))
        assertTrue(gate.consume(pin));assertFalse(gate.consume(pin));assertEquals("",gate.visible())
        val next=gate.open();now+=300001;assertFalse(gate.consume(next))
    }
    @Test fun fiveWrongAttemptsClosePairingUntilLocalAction() {
        val gate=PairingGate {1000L};val pin=gate.open()
        repeat(5) {assertFalse(gate.consume("invalid"))}
        assertEquals("",gate.visible());assertFalse(gate.consume(pin))
        assertTrue(gate.consume(gate.open()))
    }
    @Test fun configurationRoundTripsIncludingFiltersAndIndependentDesignChoices() {
        val c = AppConfig(name="Küche", haUrl="http://homeassistant.local:8123", theme="dark", animation="pulse",
            favorites=listOf("light.a", "cover.b"), areas=listOf("kitchen"), camera="camera.front", doorbell="binary_sensor.bell",visibleCameras=listOf("camera.front"),doorOpener="button.door",doorLockAction="unlock")
        assertEquals(c, AppConfig.from(c.json()))
        assertFalse(c.json().has("haToken"))
    }
    @Test fun ringtoneSelectionRoundTripsAndRejectsOutOfBoundsVolume() {
        val config=AppConfig(ringtone="soft",ringVolume=35)
        assertEquals(config,AppConfig.from(config.json()))
        assertThrows(IllegalArgumentException::class.java) {AppConfig.from(config.json().put("ringVolume",101))}
        assertThrows(IllegalArgumentException::class.java) {AppConfig.from(config.json().put("ringtone","../../file"))}
    }
    @Test fun refusesInvalidOriginsAndUnsupportedPackages() {
        for(url in listOf("file:///data", "http://user:pass@ha:8123", "http://ha:8123/path", "javascript:alert(1)")) {
            assertThrows(IllegalArgumentException::class.java) {AppConfig.from(AppConfig().json().put("haUrl",url))}
        }
        assertThrows(IllegalArgumentException::class.java) {AppConfig.from(AppConfig().json().put("cameraSeconds",0))}
        assertThrows(IllegalArgumentException::class.java) {PackageManifest.parse(JSONObject("""{"id":"example.theme","kind":"theme","version":"1.0","apiVersion":9}"""))}
    }
    @Test fun doorbellDoesNotReplayOnSnapshotOrAttributesOnlyOrUnavailable() {
        val c=AppConfig(doorbell="binary_sensor.bell",camera="camera.front")
        val off=Entity(c.doorbell,"off");val on=Entity(c.doorbell,"on")
        assertTrue(isDoorbellChange(c,off,on))
        assertFalse(isDoorbellChange(c,null,on))
        assertFalse(isDoorbellChange(c,on,on))
        assertFalse(isDoorbellChange(c,Entity(c.doorbell,"unavailable"),on))
        assertFalse(isDoorbellChange(c,on,off))
        assertFalse(isDoorbellChange(c,off,Entity("binary_sensor.other","on")))
    }
    @Test fun eventDoorbellRequiresNewStateAndRealPreviousEvent() {
        val c=AppConfig(doorbell="event.bell")
        assertTrue(isDoorbellChange(c,Entity(c.doorbell,"2026-11-01T12:00:00Z"),Entity(c.doorbell,"2026-11-01T12:01:00Z")))
        assertFalse(isDoorbellChange(c,null,Entity(c.doorbell,"2026-11-01T12:01:00Z")))
    }
}
