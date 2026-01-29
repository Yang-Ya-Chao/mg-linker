package com.my.mg.widget.data

import java.util.Locale

/**
 * 功能：能耗计算器（优化版）。
 * 优化点：
 * 1. 修复：增加前缀 " / "。
 * 2. 诊断：解决因容量配置为 0 导致计算结果为空的问题。
 * 3. 策略：混合动力模式下，如果只有单侧数据有效，依然尝试输出结果。
 */
object EnergyCalculator {

    // 物理常数：1升汽油约等于 8.9 kWh 能量
    private const val KWH_PER_LITER_GASOLINE = 8.9

    data class ConsumptionResult(
        val displayText: String,   // UI显示文本 (例如 " / 5.6L/100km")
        val rawValue: Double       // 原始数值
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
        fuelCapacity: Double, // ⚠️ 关键：必须确保传入正确的油箱容量(L)，否则结果为0
        fuelLevel: Double,
        batteryRange: Double,
        batteryCapacity: Double, // ⚠️ 关键：必须确保传入正确的电池容量(kWh)，否则结果为0
        batteryPackPrc: Double
    ): ConsumptionResult {

        // 1. 模式判定
        // 如果油箱几乎没有但有电池 -> 纯电
        val isEV = fuelCapacity <= 0.5 && batteryCapacity > 0.5
        // 如果电池几乎没有但有油箱 -> 纯油
        val isICE = batteryCapacity <= 0.5 && fuelCapacity > 0.5

        // 2. 纯电模式 (EV)
        if (isEV) {
            val kwhPer100km =
                calculateElectricConsumption(batteryRange, batteryCapacity, batteryPackPrc)
            return formatResult(kwhPer100km, "kWh/100km")
        }

        // 3. 纯油模式 (ICE)
        if (isICE) {
            val litersPer100km = calculateFuelConsumption(fuelRange, fuelCapacity, fuelLevel)
            return formatResult(litersPer100km, "L/100km")
        }

        // 4. 混动模式 (Hybrid / PHEV)
        // 即使是混动，如果当前没油了只有电，或者没电了只有油，我们也尽量算出一个数
        val fuelCons = calculateFuelConsumption(fuelRange, fuelCapacity, fuelLevel)
        val elecCons = calculateElectricConsumption(batteryRange, batteryCapacity, batteryPackPrc)

        // 将电耗转化为“等效油耗”
        val electricToFuelEquivalent = if (elecCons > 0) elecCons / KWH_PER_LITER_GASOLINE else 0.0
        val totalEquivalentConsumption = fuelCons + electricToFuelEquivalent

        return formatResult(totalEquivalentConsumption, "L/100km")
    }

    /**
     * 统一格式化输出
     * 规则：如果有值，返回 " / 5.6L/100km"；如果是0，返回 ""
     */
    private fun formatResult(value: Double, unit: String): ConsumptionResult {
        // 过滤异常值：能耗不可能超过 50L/100km (坦克?) 或小于 1.0 (除非下坡)
        // 这里设置一个宽松范围 0.1 ~ 99.0 防止除以极小数导致的 Infinity
        val isValid = value in 0.1..99.0

        val text = if (isValid) {
            // 注意：这里加了 " / " 前缀
            String.format(Locale.US, "%.1f%s", value, unit)
        } else {
            ""
        }
        return ConsumptionResult(text, value)
    }

    // --- 内部算法 ---

    private fun calculateFuelConsumption(
        range: Double,
        capacity: Double,
        levelPercent: Double
    ): Double {
        // 必须有续航且有容量配置
        if (range < 1.0 || capacity <= 0.0) return 0.0
        val currentFuelLiters = capacity * (levelPercent / 100.0)
        return (currentFuelLiters / range) * 100.0
    }

    private fun calculateElectricConsumption(
        range: Double,
        capacity: Double,
        levelPercent: Double
    ): Double {
        if (range < 1.0 || capacity <= 0.0) return 0.0
        val currentEnergyKwh = capacity * (levelPercent / 100.0)
        return (currentEnergyKwh / range) * 100.0
    }
}