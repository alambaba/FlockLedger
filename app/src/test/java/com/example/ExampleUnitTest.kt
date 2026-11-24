package com.example

import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.LedgerEntry
import com.example.data.model.computeFarmSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun computeFarmSummary_calculatesBalancesAndFlockAccurately() {
        val farm = Farm(
            id = "f1",
            ownerId = "u1",
            name = "Test Farm",
            shepherds = listOf("Ali", "Sajan"),
            initialFlockCount = 50L
        )
        val entries = listOf(
            LedgerEntry(id = "1", category = EntryCategory.OWNER_FUNDS.name, amount = 100_000.0, title = "Owner Cash"),
            LedgerEntry(id = "2", category = EntryCategory.EXPENSE.name, amount = 12_000.0, title = "Fodder"),
            LedgerEntry(id = "3", category = EntryCategory.MEDICINE_VET.name, amount = 3_000.0, title = "Vaccine"),
            LedgerEntry(id = "4", category = EntryCategory.LIVESTOCK_BUY.name, amount = 40_000.0, livestockCount = 4L, title = "Bought 4"),
            LedgerEntry(id = "5", category = EntryCategory.LIVESTOCK_SELL.name, amount = 25_000.0, livestockCount = 2L, title = "Sold 2"),
            LedgerEntry(id = "6", category = EntryCategory.SHEPHERD_ADVANCE.name, amount = 5_000.0, shepherdName = "Ali", title = "Advance"),
            LedgerEntry(id = "7", category = EntryCategory.SHEPHERD_SALARY.name, amount = 15_000.0, shepherdName = "Ali", title = "Salary"),
        )

        val summary = computeFarmSummary(farm, entries)

        assertEquals(100_000.0, summary.ownerFundsReceived, 0.01)
        assertEquals(25_000.0, summary.livestockSalesRevenue, 0.01)
        assertEquals(75_000.0, summary.totalOutflows, 0.01)
        // Remaining = 100,000 + 25,000 - 75,000 = 50,000
        assertEquals(50_000.0, summary.remainingCashBalance, 0.01)
        // Flock = 50 + 4 - 2 = 52
        assertEquals(52, summary.currentFlockCount)
        assertEquals(4, summary.livestockBoughtCount)
        assertEquals(2, summary.livestockSoldCount)

        val ali = summary.shepherdBreakdown.first { it.shepherdName == "Ali" }
        assertEquals(5_000.0, ali.totalAdvance, 0.01)
        assertEquals(15_000.0, ali.totalSalary, 0.01)
        assertEquals(20_000.0, ali.totalPaid, 0.01)
    }
}
