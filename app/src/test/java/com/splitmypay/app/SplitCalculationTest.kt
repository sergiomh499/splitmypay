package com.splitmypay.app

import com.splitmypay.app.data.util.SplitCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class SplitCalculationTest {

    @Test
    fun testEqualSplitThreeMembersCentsPrecision() {
        val members = listOf("user1", "user2", "user3")
        val allocations = SplitCalculator.calculateEqualSplit(10.00, members)

        assertEquals(3, allocations.size)
        assertEquals(3.34, allocations[0].amount, 0.001)
        assertEquals(3.33, allocations[1].amount, 0.001)
        assertEquals(3.33, allocations[2].amount, 0.001)

        val sum = allocations.sumOf { BigDecimal.valueOf(it.amount) }.setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(10.00, sum, 0.001)
    }

    @Test
    fun testEqualSplitSixMembers() {
        val members = listOf("m1", "m2", "m3", "m4", "m5", "m6")
        val total = 100.00
        val allocations = SplitCalculator.calculateEqualSplit(total, members)

        assertEquals(6, allocations.size)
        val sum = allocations.sumOf { BigDecimal.valueOf(it.amount) }.setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(100.00, sum, 0.001)
    }

    @Test
    fun testEqualSplitSingleMember() {
        val members = listOf("m1")
        val total = 42.50
        val allocations = SplitCalculator.calculateEqualSplit(total, members)

        assertEquals(1, allocations.size)
        assertEquals(42.50, allocations.first().amount, 0.001)
    }

    @Test
    fun testEqualSplitSmallAmount() {
        val members = listOf("m1", "m2", "m3")
        val total = 0.01
        val allocations = SplitCalculator.calculateEqualSplit(total, members)

        assertEquals(3, allocations.size)
        val sum = allocations.sumOf { BigDecimal.valueOf(it.amount) }.setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(0.01, sum, 0.001)
    }

    @Test
    fun testWeightedSplitSimple() {
        val weights = mapOf("m1" to 1, "m2" to 2)
        val total = 30.00
        val allocations = SplitCalculator.calculateWeightedSplit(total, weights)

        assertEquals(2, allocations.size)
        val m1 = allocations.find { it.memberUuid == "m1" }?.amount ?: 0.0
        val m2 = allocations.find { it.memberUuid == "m2" }?.amount ?: 0.0

        assertEquals(10.00, m1, 0.001)
        assertEquals(20.00, m2, 0.001)
        assertEquals(30.00, m1 + m2, 0.001)
    }

    @Test
    fun testWeightedSplitRemainder() {
        val weights = mapOf("m1" to 1, "m2" to 2, "m3" to 3)
        val total = 10.00
        val allocations = SplitCalculator.calculateWeightedSplit(total, weights)

        assertEquals(3, allocations.size)
        val sum = allocations.sumOf { BigDecimal.valueOf(it.amount) }.setScale(2, RoundingMode.HALF_UP).toDouble()
        assertEquals(10.00, sum, 0.001)
    }
}
