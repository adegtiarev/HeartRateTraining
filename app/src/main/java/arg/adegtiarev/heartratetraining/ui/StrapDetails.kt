package arg.adegtiarev.heartratetraining.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arg.adegtiarev.heartratetraining.R
import arg.adegtiarev.heartratetraining.sensor.SensorState
import java.text.NumberFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

internal enum class BatteryAlertPhase { Inactive, Regular, Flash }

internal fun batteryAlertPhases(low: Boolean) = flow {
    if (!low) {
        emit(BatteryAlertPhase.Inactive)
        return@flow
    }
    while (true) {
        emit(BatteryAlertPhase.Regular)
        delay(250)
        emit(BatteryAlertPhase.Flash)
        delay(250)
    }
}

@Composable
internal fun StrapDetails(sensor: SensorState) {
    val strap = sensor.active ?: sensor.remembered ?: return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        Text(strap.name ?: stringResource(R.string.unnamed_sensor), style = MaterialTheme.typography.labelLarge)
        val percent = sensor.batteryPercent ?: return@FlowRow
        val locale = LocalConfiguration.current.locales[0]
        val value = remember(locale, percent) { NumberFormat.getPercentInstance(locale).format(percent / 100.0) }
        val low = sensor.hasLowBattery
        val description = stringResource(if (low) R.string.strap_battery_low else R.string.strap_battery_level, value)
        val phase = remember(low) { batteryAlertPhases(low) }.collectAsStateWithLifecycle(BatteryAlertPhase.Inactive)
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.strap_battery_label), style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.drawWithContent {
                if (!low || phase.value != BatteryAlertPhase.Flash) drawContent()
            })
        }
    }
}
