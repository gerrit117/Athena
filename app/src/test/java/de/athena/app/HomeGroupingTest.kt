package de.athena.app

import org.junit.Assert.*
import org.junit.Test

class HomeGroupingTest {
    private val roomNames=mapOf("living" to "Wohnzimmer","bath" to "Badezimmer")
    private val input=listOf(Entity("climate.living","heat"),Entity("light.bath","on"),Entity("light.living","off"),Entity("sensor.unassigned","21"),Entity("light.other","on"))
    private val rooms=mapOf("climate.living" to "living","light.bath" to "bath","light.living" to "living","light.other" to "deleted-room")
    @Test fun oldConfigurationKeepsUngroupedLayoutAndNewChoiceRoundTrips() {
        val old=AppConfig(favorites=listOf("light.bath"),theme="dark").json().apply {remove("homeGrouping")}
        assertEquals("none",AppConfig.from(old).homeGrouping)
        assertEquals(listOf("light.bath"),AppConfig.from(old).favorites)
        val grouped=AppConfig(homeGrouping="area_category")
        assertEquals(grouped,AppConfig.from(grouped.json()))
        assertThrows(IllegalArgumentException::class.java) {AppConfig.from(grouped.json().put("homeGrouping","unsupported"))}
    }
    @Test fun ungroupedKeepsExactFavoriteOrderAndEmptyListStaysEmpty() {
        assertEquals(input,groupHomeEntities(input,"none",rooms,roomNames).single().entities)
        assertTrue(groupHomeEntities(emptyList(),"area",rooms,roomNames).isEmpty())
    }
    @Test fun categoriesKeepFavoriteOrderWithinGroupsAndDoNotDuplicate() {
        val result=groupHomeEntities(input,"category",rooms,roomNames)
        assertEquals(listOf("Beleuchtung","Heizung","Sensoren"),result.map {it.title})
        assertEquals(listOf("light.bath","light.living","light.other"),result.first().entities.map {it.id})
        assertEquals(input.map {it.id}.toSet(),result.flatMap {it.entities}.map {it.id}.toSet())
        assertEquals(input.size,result.sumOf {it.entities.size})
    }
    @Test fun roomsUseHaRegistryAndPutMissingOrDeletedRoomsLast() {
        val result=groupHomeEntities(input,"area",rooms,roomNames)
        assertEquals(listOf("Badezimmer","Wohnzimmer","Ohne Raum"),result.map {it.title})
        assertEquals(listOf("sensor.unassigned","light.other"),result.last().entities.map {it.id})
    }
    @Test fun combinedGroupsSplitRoomAndCategoryAndNeverAddUnselectedDevices() {
        val selected=input.take(3)
        val result=groupHomeEntities(selected,"area_category",rooms,roomNames)
        assertEquals(listOf("Badezimmer · Beleuchtung","Wohnzimmer · Beleuchtung","Wohnzimmer · Heizung"),result.map {it.title})
        assertEquals(selected.map {it.id}.toSet(),result.flatMap {it.entities}.map {it.id}.toSet())
    }
}
