package de.athena.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun DoorOpenerSettings(r: AthenaRuntime) {
    var selecting by remember {mutableStateOf(false)}
    var query by remember {mutableStateOf("")}
    Text(r.t("Türöffner · optional"),style=MaterialTheme.typography.titleMedium)
    val selected=r.entities.firstOrNull {it.id==r.config.doorOpener}
    OutlinedButton(onClick={selecting=true}) {Text(selected?.name ?: r.config.doorOpener.ifBlank {r.t("Keine Auswahl")})}
    if(r.config.doorOpener.startsWith("lock.")) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf("unlock" to "Entriegeln","open" to "Falle öffnen").forEach {(action,label) ->
            FilterChip(selected=r.config.doorLockAction==action,onClick={r.updateConfig {it.copy(doorLockAction=action)}},label={Text(r.t(label))})
        }
    }
    Text(r.t("Ohne Auswahl bleibt die Funktion ausgeschaltet. Die Schaltfläche erscheint nur im automatisch geöffneten Klingelbild."),style=MaterialTheme.typography.bodySmall)
    if(selecting) AlertDialog(onDismissRequest={selecting=false},title={Text(r.t("Türöffner auswählen"))},text={
        Column(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query,{query=it},label={Text(r.t("Name, Gerät oder Entitäts-ID suchen …"))},singleLine=true)
            LazyColumn {
                items(r.entities.filter {e -> e.domain in setOf("lock","button","input_button","script","switch","input_boolean","cover") && listOf(e.name,e.id,e.deviceName).joinToString(" ").contains(query,true)},key={it.id}) {e ->
                    Column(Modifier.fillMaxWidth().clickable {r.updateConfig {it.copy(doorOpener=e.id)};selecting=false}.padding(vertical=10.dp)) {
                        Text(e.name);Text(e.id,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick={r.updateConfig {it.copy(doorOpener="")};selecting=false}) {Text(r.t("Keine Auswahl"))}},dismissButton={TextButton(onClick={selecting=false}) {Text(r.t("Abbrechen"))}})
}
