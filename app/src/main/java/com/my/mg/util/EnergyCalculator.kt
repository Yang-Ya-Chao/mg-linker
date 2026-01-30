package com.my.mg.util

import com.my.mg.data.FuelRecord


object EnergyCalculator {

    private const val ELECTRIC_TO_FUEL_RATIO = 0.31 // 工信部折算

    fun calculate(
        prev: FuelRecord,
        now: FuelRecord,
        fuelCapacity: Double,
        batteryCapacity: Double
    ): Double? {

        val deltaKm = now.odometer - prev.odometer
        //行程大于5km才算有效
        if (deltaKm <= 5) return null

        val deltaFuel = fuelCapacity * (prev.fuelPrc - now.fuelPrc) / 100.0
        val deltaBattery = batteryCapacity * (prev.batteryPrc - now.batteryPrc) / 100.0

        val deltaBatteryAsFuel = deltaBattery * ELECTRIC_TO_FUEL_RATIO
        val totalFuelEquivalent = deltaFuel + deltaBatteryAsFuel

        return (totalFuelEquivalent / deltaKm) * 100.0
    }
}
