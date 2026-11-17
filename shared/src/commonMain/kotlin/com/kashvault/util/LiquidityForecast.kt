package com.kashvault.util

data class HorizonPoint(
    val day: String,
    val balance: Double,
)

data class LiquidityInput(
    val currentLiquidity: Double,
    val monthlyIncome: Double,
    val monthlyAvgSpend: Double,
    val upcomingLien: Double,
)

data class LiquidityForecastOutput(
    val forecast: String,
    val riskLevel: String,
    val runwayDays: Int,
    val tips: List<String> = emptyList(),
)

fun buildHorizonSeries(input: LiquidityInput): List<HorizonPoint> {
    val dailyNet = (input.monthlyIncome - input.monthlyAvgSpend - input.upcomingLien) / 30.0
    val points = mutableListOf<HorizonPoint>()
    var balance = input.currentLiquidity
    var day = 0
    while (day <= 30) {
        if (day > 0) balance += dailyNet * 5
        points.add(HorizonPoint(day = "D$day", balance = kotlin.math.round(maxOf(balance, 0.0))))
        day += 5
    }
    return points
}

fun heuristicLiquidityForecast(input: LiquidityInput): LiquidityForecastOutput {
    val dailyNet = (input.monthlyIncome - input.monthlyAvgSpend - input.upcomingLien) / 30.0
    val runwayDays = when {
        dailyNet >= 0 -> 999
        input.currentLiquidity <= 0 -> 0
        else -> kotlin.math.floor(input.currentLiquidity / -dailyNet).toInt().coerceAtLeast(0)
    }
    val riskLevel = when {
        runwayDays > 60 -> "Low"
        runwayDays > 21 -> "Medium"
        else -> "High"
    }
    val forecast = when (riskLevel) {
        "Low" -> "Cash looks stable over the next 30 days. Income covers spend and upcoming bills."
        "Medium" -> "Liquidity is adequate but thinning. Watch upcoming SIPs, EMIs, and discretionary spend."
        else -> "Cash runway is short. Reduce non-essential spend or delay optional outflows this month."
    }
    val tips = buildList {
        if (input.upcomingLien > 0) add("Set aside ${formatINR(input.upcomingLien)} for upcoming SIPs, insurance, and EMIs.")
        if (input.monthlyAvgSpend > input.monthlyIncome && input.monthlyIncome > 0) {
            add("This month's spend is outpacing income — review the largest expense categories.")
        }
        add("Keep an emergency buffer of at least 1 month of average spend.")
    }
    return LiquidityForecastOutput(
        forecast = forecast,
        riskLevel = riskLevel,
        runwayDays = runwayDays.coerceAtMost(999),
        tips = tips,
    )
}
