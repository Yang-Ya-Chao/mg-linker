package com.my.mg.widget.data // 或者 com.my.mg.util

import java.util.Locale

/**
 * 功能：能耗计算器。
 * 实现目的：
 * 1. 根据剩余油量/电量和剩余续航，反推车辆当前的百公里能耗。
 * 2. 支持纯油、纯电、混动三种模式的计算策略。
 * 3. 将电耗转化为“等效油耗”以便进行综合能耗展示。
 */
object EnergyCalculator {

    // 物理常数：1升汽油约等于 8.9 kWh 能量
    private const val KWH_PER_LITER_GASOLINE = 8.9

    /**
     * 计算综合能耗结果
     */
    data class ConsumptionResult(
        val displayText: String,   // 用于UI显示的文本 (例如 "5.6 L/100km")
        val rawValue: Double,      // 原始数值 (用于逻辑判断)
        val unit: String           // 单位
    )

    /**
     * 核心计算函数
     *
     * @param fuelRange 剩余油续航 (km)
     * @param fuelCapacity 油箱总容量 (L)
     * @param fuelLevel 油量百分比 (0-100)
     * @param batteryRange 剩余电续航 (km)
     * @param batteryCapacity 电池总容量 (kWh)
     * @param batteryPackPrc 电量百分比 (0-100)
     */
    fun calculate(
        fuelRange: Double,
        fuelCapacity: Double,
        fuelLevel: Double,
        batteryRange: Double,
        batteryCapacity: Double,
        batteryPackPrc: Double
    ): ConsumptionResult {

        // 1. 判断车辆类型模式
        val isEV = fuelCapacity <= 0.1 && batteryCapacity > 0.0 // 纯电车
        val isICE = batteryCapacity <= 0.1 && fuelCapacity > 0.0// 纯油车 (假设电池极小或为0)
        // 其他情况视为混动 (PHEV/EREV)

        // ================== 纯电模式 (EV) ==================
        if (isEV) {
            val kwhPer100km = calculateElectricConsumption(batteryRange, batteryCapacity, batteryPackPrc)
            return ConsumptionResult(
                displayText = if (kwhPer100km > 0.0) String.format(Locale.US, "%.1fkWh/100km", kwhPer100km) else "",
                rawValue = kwhPer100km,
                unit = "kWh/100km"
            )
        }

        // ================== 纯油模式 (ICE) ==================
        if (isICE) {
            val litersPer100km = calculateFuelConsumption(fuelRange, fuelCapacity, fuelLevel)
            return ConsumptionResult(
                displayText = if (litersPer100km > 0.0) String.format(Locale.US, "%.1fL/100km", litersPer100km) else "",
                rawValue = litersPer100km,
                unit = "L/100km"
            )
        }

        // ================== 混动模式 (Hybrid) ==================
        // 策略：分别计算，然后将电耗转化为等效油耗相加

        // 1. 算出基础油耗 (L/100km)
        val fuelConsumption = calculateFuelConsumption(fuelRange, fuelCapacity, fuelLevel)

        // 2. 算出基础电耗 (kWh/100km)
        val electricConsumption = calculateElectricConsumption(batteryRange, batteryCapacity, batteryPackPrc)

        // 3. 电耗转等效油耗 (Equivalent L/100km) = 电耗 / 8.9
        val electricToFuelEquivalent = electricConsumption / KWH_PER_LITER_GASOLINE

        // 4. 综合油耗
        val totalEquivalentConsumption = fuelConsumption + electricToFuelEquivalent

        return ConsumptionResult(
            displayText = if (totalEquivalentConsumption > 0.0) String.format(Locale.US, "%.1fL/100km ", totalEquivalentConsumption) else "",
            rawValue = totalEquivalentConsumption,
            unit = "L/100km"
        )
    }

    /**
     * 内部算法：计算百公里油耗
     * 公式：(当前剩余油量L / 当前剩余续航km) * 100
     */
    private fun calculateFuelConsumption(range: Double, capacity: Double, levelPercent: Double): Double {
        if (range <= 1.0) return 0.0 // 避免除以0，或者续航极低时不准确

        val currentFuelLiters = capacity * (levelPercent / 100.0)
        return (currentFuelLiters / range) * 100.0
    }

    /**
     * 内部算法：计算百公里电耗
     * 公式：(当前剩余电量kWh / 当前剩余续航km) * 100
     */
    private fun calculateElectricConsumption(range: Double, capacity: Double, levelPercent: Double): Double {
        if (range <= 1.0) return 0.0

        val currentEnergyKwh = capacity * (levelPercent / 100.0)
        return (currentEnergyKwh / range) * 100.0
    }
}