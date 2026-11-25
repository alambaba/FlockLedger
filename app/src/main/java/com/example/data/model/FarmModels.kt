package com.example.data.model

enum class EntryCategory {
    EXPENSE,
    MEDICINE_VET,
    LIVESTOCK_BUY,
    LIVESTOCK_SELL,
    SHEPHERD_ADVANCE,
    SHEPHERD_SALARY,
    OWNER_FUNDS;

    companion object {
        fun fromRaw(raw: String?): EntryCategory =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

data class Farm(
    val id: String = "",
    val ownerId: String = "local_admin",
    val name: String = "",
    val shepherds: List<String> = emptyList(),
    val initialFlockCount: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class LedgerEntry(
    val id: String = "",
    val farmId: String = "",
    val createdByName: String = "",
    val category: String = EntryCategory.EXPENSE.name,
    val amount: Double = 0.0,
    val livestockCount: Long = 0L,
    val shepherdName: String = "",
    val title: String = "",
    val note: String = "",
    val receiptBase64: String? = null,
    val entryDateMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun parsedCategory(): EntryCategory = EntryCategory.fromRaw(category)
}

data class ShepherdLedgerSummary(
    val shepherdName: String,
    val totalAdvance: Double,
    val totalSalary: Double,
    val entryCount: Int,
) {
    val totalPaid: Double get() = totalAdvance + totalSalary
}

data class FarmSummary(
    val ownerFundsReceived: Double,
    val generalExpenses: Double,
    val medicineVetExpenses: Double,
    val livestockBuyCost: Double,
    val livestockSalesRevenue: Double,
    val shepherdAdvancesTotal: Double,
    val shepherdSalariesTotal: Double,
    val livestockBoughtCount: Int,
    val livestockSoldCount: Int,
    val currentFlockCount: Int,
    val totalOutflows: Double,
    val remainingCashBalance: Double,
    val shepherdBreakdown: List<ShepherdLedgerSummary>,
)

fun computeFarmSummary(farm: Farm?, entries: List<LedgerEntry>): FarmSummary {
    var ownerFunds = 0.0
    var generalExpenses = 0.0
    var medicineVet = 0.0
    var livestockBuyCost = 0.0
    var livestockSellRev = 0.0
    var advances = 0.0
    var salaries = 0.0
    var boughtCount = 0
    var soldCount = 0

    val shepherdMap = linkedMapOf<String, MutableList<LedgerEntry>>()

    for (entry in entries) {
        when (entry.parsedCategory()) {
            EntryCategory.OWNER_FUNDS -> ownerFunds += entry.amount
            EntryCategory.EXPENSE -> generalExpenses += entry.amount
            EntryCategory.MEDICINE_VET -> medicineVet += entry.amount
            EntryCategory.LIVESTOCK_BUY -> {
                livestockBuyCost += entry.amount
                boughtCount += entry.livestockCount.toInt()
            }
            EntryCategory.LIVESTOCK_SELL -> {
                livestockSellRev += entry.amount
                soldCount += entry.livestockCount.toInt()
            }
            EntryCategory.SHEPHERD_ADVANCE -> {
                advances += entry.amount
                val name = entry.shepherdName.trim().ifEmpty { "Unknown" }
                shepherdMap.getOrPut(name) { mutableListOf() }.add(entry)
            }
            EntryCategory.SHEPHERD_SALARY -> {
                salaries += entry.amount
                val name = entry.shepherdName.trim().ifEmpty { "Unknown" }
                shepherdMap.getOrPut(name) { mutableListOf() }.add(entry)
            }
        }
    }

    // Include registered shepherds with 0 payments so admin sees full roster
    farm?.shepherds?.forEach { name ->
        val clean = name.trim()
        if (clean.isNotEmpty()) {
            shepherdMap.putIfAbsent(clean, mutableListOf())
        }
    }

    val shepherdBreakdown = shepherdMap.map { (name, list) ->
        val adv = list.filter { it.parsedCategory() == EntryCategory.SHEPHERD_ADVANCE }.sumOf { it.amount }
        val sal = list.filter { it.parsedCategory() == EntryCategory.SHEPHERD_SALARY }.sumOf { it.amount }
        ShepherdLedgerSummary(
            shepherdName = name,
            totalAdvance = adv,
            totalSalary = sal,
            entryCount = list.size,
        )
    }.sortedByDescending { it.totalPaid }

    val totalOutflows = generalExpenses + medicineVet + livestockBuyCost + advances + salaries
    val remainingCash = ownerFunds + livestockSellRev - totalOutflows
    val startFlock = (farm?.initialFlockCount ?: 0L).toInt()
    val currentFlock = (startFlock + boughtCount - soldCount).coerceAtLeast(0)

    return FarmSummary(
        ownerFundsReceived = ownerFunds,
        generalExpenses = generalExpenses,
        medicineVetExpenses = medicineVet,
        livestockBuyCost = livestockBuyCost,
        livestockSalesRevenue = livestockSellRev,
        shepherdAdvancesTotal = advances,
        shepherdSalariesTotal = salaries,
        livestockBoughtCount = boughtCount,
        livestockSoldCount = soldCount,
        currentFlockCount = currentFlock,
        totalOutflows = totalOutflows,
        remainingCashBalance = remainingCash,
        shepherdBreakdown = shepherdBreakdown,
    )
}
