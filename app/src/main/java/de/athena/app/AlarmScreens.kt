package de.athena.app

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

@Composable fun AlarmsScreen(r: AthenaRuntime) {
    var adding by remember {mutableStateOf(false)}
    val context=LocalContext.current
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(r.t("Wecker"),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
            Button(onClick={adding=true},enabled=r.alarms.alarms.size<32) {Text(r.t("Wecker hinzufügen"))}
        }
        Text(r.t("Wecker werden auf dem Echo gespeichert und klingeln auch ohne HA-Verbindung."),style=MaterialTheme.typography.bodySmall)
        Text(r.t(r.alarmSync.status),style=MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(r.alarms.alarms,key={it.id}) {alarm ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text("%02d:%02d".format(alarm.hour,alarm.minute),fontSize=30.sp,fontWeight=FontWeight.Medium)
                        Column(Modifier.weight(1f)) {
                            Text(alarm.label.ifBlank {r.t("Wecker")})
                            Text(if(alarm.repeatDays.isNotEmpty() && alarm.repeatDays.size!=7)alarm.repeatDays.joinToString(", ") {java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT,r.locale)} else r.t(if(alarm.daily)"Täglich" else "Einmalig"),style=MaterialTheme.typography.bodySmall)
                            if(alarm.enabled) Text(SimpleDateFormat("EEE, d. MMM · HH:mm",r.locale).apply {timeZone=TimeZone.getTimeZone(alarm.zone)}.format(Date(alarm.next)),style=MaterialTheme.typography.bodySmall)
                        }
                        Switch(alarm.enabled,{value -> r.alarms.toggle(alarm.id,value)})
                        IconButton(onClick={r.alarms.remove(alarm.id)}) {Icon(Icons.Outlined.Delete,r.t("Löschen"))}
                    }
                }
            }
        }
        if(r.alarms.alarms.isEmpty()) Text(r.t("Noch kein Wecker eingerichtet"))
    }
    if(adding) {
        var hour by remember {mutableIntStateOf(7)};var minute by remember {mutableIntStateOf(0)}
        var label by remember {mutableStateOf("")};var daily by remember {mutableStateOf(false)}
        AlertDialog(onDismissRequest={adding=false},title={Text(r.t("Wecker hinzufügen"))},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={TimePickerDialog(context,{_,h,m -> hour=h;minute=m},hour,minute,true).show()}) {Text("%02d:%02d".format(hour,minute),fontSize=26.sp)}
                OutlinedTextField(label,{label=it.take(80)},label={Text(r.t("Bezeichnung"))},singleLine=true)
                Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(daily,{daily=it});Text(r.t("Täglich"))}
                Text(r.timeZone,style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(onClick={runCatching {r.alarms.create(hour,minute,label,daily,r.timeZone)}
            .onSuccess {adding=false}.onFailure {r.message=it.message ?: r.t("Fehler")}}) {Text(r.t("Speichern"))}},
            dismissButton={TextButton(onClick={adding=false}) {Text(r.t("Abbrechen"))}})
    }
}

@Composable fun AlarmRingingScreen(r: AthenaRuntime) {
    val alarm=r.alarms.active ?: return
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Icon(Icons.Outlined.Alarm,r.t("Wecker"),Modifier.size(54.dp))
            Text("%02d:%02d".format(alarm.hour,alarm.minute),fontSize=76.sp,fontWeight=FontWeight.Light)
            Text(alarm.label.ifBlank {r.t("Wecker")},style=MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                OutlinedButton(onClick={runCatching {r.alarms.snooze()}.onFailure {r.message=it.message ?: r.t("Fehler")}}) {Text(r.t("5 Minuten schlummern"))}
                Button(onClick={r.alarms.dismiss()}) {Text(r.t("Wecker ausschalten"))}
            }
        }
    }
}
