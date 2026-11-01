package de.athena.app

import org.junit.Assert.*
import org.junit.Test

class NightPolicyTest {
    @Test fun darknessMustPersistAndBriefFlashesDoNotExit() {
        val policy=NightPolicy()
        assertFalse(policy.update("auto",1f,5f,30,0))
        assertFalse(policy.update("auto",1f,5f,30,29999))
        assertTrue(policy.update("auto",1f,5f,30,30000))
        assertTrue(policy.update("auto",50f,5f,30,31000))
        assertTrue(policy.update("auto",1f,5f,30,32000))
        assertTrue(policy.update("auto",50f,5f,30,33000))
        assertFalse(policy.update("auto",50f,5f,30,36000))
    }
    @Test fun changingLightResetsEntryDelayAndHysteresisAvoidsFlicker() {
        val policy=NightPolicy()
        policy.update("auto",1f,5f,10,0)
        policy.update("auto",8f,5f,10,9000)
        assertFalse(policy.update("auto",1f,5f,10,10000))
        assertTrue(policy.update("auto",1f,5f,10,20000))
        assertTrue(policy.update("auto",6f,5f,10,60000))
    }
    @Test fun absentOrInvalidSensorNeverSilentlyMeansDark() {
        for(lux in listOf(null,Float.NaN,-1f,Float.POSITIVE_INFINITY)) {
            assertFalse(NightPolicy().update("auto",lux,5f,0,0))
        }
    }
    @Test fun manualModeDoesNotRequireSensorAndOffRestoresNormalMode() {
        val policy=NightPolicy()
        assertTrue(policy.update("always",null,5f,30,0))
        assertFalse(policy.update("off",null,5f,30,1))
    }
}
