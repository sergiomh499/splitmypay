package com.splitmypay.app.data.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

object SplitCalculator {

    data class Allocation(
        val memberUuid: String,
        val amount: Double
    )

    /**
     * Splits totalAmount among selected members equally.
     * Cents remainder is distributed to the first members so the sum of allocations
     * is exactly equal to totalAmount (e.g., 10.00 / 3 -> [3.34, 3.33, 3.33]).
     */
    fun calculateEqualSplit(totalAmount: Double, memberUuids: List<String>): List<Allocation> {
        if (memberUuids.isEmpty()) return emptyList()
        if (memberUuids.size == 1) {
            return listOf(Allocation(memberUuids.first(), roundToTwoDecimals(totalAmount)))
        }

        val totalCents = (BigDecimal.valueOf(totalAmount).setScale(2, RoundingMode.HALF_UP).toDouble() * 100).roundToLong()
        val count = memberUuids.size.toLong()
        val baseCents = totalCents / count
        val remainder = (totalCents % count).toInt()

        return memberUuids.mapIndexed { index, uuid ->
            val memberCents = baseCents + (if (index < remainder) 1 else 0)
            Allocation(uuid, memberCents / 100.0)
        }
    }

    /**
     * Splits totalAmount based on integer shares/weights (e.g. 1x, 2x).
     */
    fun calculateWeightedSplit(totalAmount: Double, memberWeights: Map<String, Int>): List<Allocation> {
        val totalWeight = memberWeights.values.filter { it > 0 }.sum()
        if (totalWeight == 0) return emptyList()

        val totalCents = (BigDecimal.valueOf(totalAmount).setScale(2, RoundingMode.HALF_UP).toDouble() * 100).roundToLong()
        var distributedCents = 0L

        val entries = memberWeights.filter { it.value > 0 }.toList()
        val allocations = mutableListOf<Allocation>()

        for (i in entries.indices) {
            val (uuid, weight) = entries[i]
            val cents = if (i == entries.lastIndex) {
                totalCents - distributedCents
            } else {
                val share = (totalCents * weight) / totalWeight
                distributedCents += share
                share
            }
            allocations.add(Allocation(uuid, cents / 100.0))
        }

        return allocations
    }

    fun roundToTwoDecimals(value: Double): Double {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}
