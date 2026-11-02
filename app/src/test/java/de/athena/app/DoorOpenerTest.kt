package de.athena.app
import org.junit.Assert.*
import org.junit.Test
class DoorOpenerTest {
    @Test fun lockMustUseTheExplicitlyConfiguredLatchAction() {
        assertEquals("lock" to "unlock",doorOpeningAction("lock.front","unlock"))
        assertEquals("lock" to "open",doorOpeningAction("lock.front","open"))
    }
    @Test fun buttonAndAutomationTargetsUseFixedActions() {
        assertEquals("button" to "press",doorOpeningAction("button.front","unlock"))
        assertEquals("input_button" to "press",doorOpeningAction("input_button.front","unlock"))
        assertEquals("script" to "turn_on",doorOpeningAction("script.front","unlock"))
        assertEquals("switch" to "turn_on",doorOpeningAction("switch.front","unlock"))
        assertEquals("cover" to "open_cover",doorOpeningAction("cover.front","unlock"))
    }
    @Test fun arbitraryServicesAndEntitiesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {doorOpeningAction("light.front","unlock")}
        assertThrows(IllegalArgumentException::class.java) {doorOpeningAction("lock.front","toggle")}
        assertThrows(IllegalArgumentException::class.java) {doorOpeningAction("https://host/lock.front","open")}
    }
}
