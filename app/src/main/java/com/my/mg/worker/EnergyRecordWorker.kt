package com.my.mg.worker

import android.content.Context
import android.util.Log
import com.my.mg.VehicleStatusResponse
import com.my.mg.data.FuelRecord
import com.my.mg.data.WidgetContextData
import com.my.mg.util.FuelRecordStore
import com.my.mg.util.EnergyCalculator

suspend fun processEnergyRecord(
    context: Context,
    vehicleData: VehicleStatusResponse?,
    baseData: WidgetContextData
) {
    try {
        val v = vehicleData?.data?.vehicle_value ?: return

        val odometer = v.odometer ?: return
        val fuelPrc = v.fuel_level_prc ?: return
        val batteryPrc = v.battery_pack_prc ?: return
        vehicleData.data.calculator = ""
        val record = FuelRecord(
            odometer = odometer,
            fuelPrc = fuelPrc,
            batteryPrc = batteryPrc,
            timestamp = System.currentTimeMillis()
        )

        val list = FuelRecordStore.load(context)
        val last = list.lastOrNull()

        // 检测加油（油量突然上升超过 5%）
        val isRefuel = last != null && fuelPrc - last.fuelPrc > 5

        // 检测充电（电量突然上升超过 5%）
        val isRecharge = last != null && batteryPrc - last.batteryPrc > 5

        if (!isRefuel && !isRecharge) {
            list.add(record)

            // 只保留最近 5 条
            while (list.size > 5) list.removeAt(0)

            FuelRecordStore.save(context, list)
        } else {
            Log.d("WidgetUpdateWorker", "Refuel or recharge detected, skip this record.")
        }

        // 记录满 5 条后计算真实能耗
        if (list.size >= 5) {
            val results = mutableListOf<Double>()

            for (i in 0 until list.size - 1) {
                val r1 = list[i]
                val r2 = list[i + 1]

                val res = EnergyCalculator.calculate(
                    prev = r1,
                    now = r2,
                    fuelCapacity = baseData.fuelCapacity,
                    batteryCapacity = baseData.batteryCapacity
                )

                if (res != null) results.add(res)
            }

            if (results.isNotEmpty()) {
                val avg = results.average()
                Log.d("WidgetUpdateWorker", "真实总能耗（5 次平均）= $avg L/100km")
                // 如果 avg > 0.0，则进行格式化并赋值
                if (avg > 0.0) {
                     vehicleData.data.calculator = "%.1f L/100km".format(avg)
                }
            }
        }

    } catch (e: Exception) {
        Log.e("WidgetUpdateWorker", "Energy record error", e)
    }
}
