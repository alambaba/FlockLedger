package com.example.data.model

enum class EntryCategory {
    EXPENSE,
    MEDICINE_VET,
    LIVESTOCK_BUY,
    LIVESTOCK_SELL,
    SHEPHERD_ADVANCE,
    SHEPHERD_SALARY,
    OWNER_FUNDS,
    LOAN_TAKEN,
    LOAN_GIVEN,
    LOAN_REPAY,
    LOAN_RECOVER;

    companion object {
        fun fromRaw(raw: String?): EntryCategory =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

enum class ExpenseSubCategory {
    TREATMENT,
    FEED,
    FARM_MAINTENANCE,
    MACHINERY,
    OTHER;

    companion object {
        fun fromRaw(raw: String?): ExpenseSubCategory? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class ApprovalStatus {
    APPROVED,
    PENDING,
    REJECTED;

    companion object {
        fun fromRaw(raw: String?): ApprovalStatus =
            entries.firstOrNull { it.name == raw } ?: APPROVED
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
    val subCategory: String = "",
    val amount: Double = 0.0,
    val livestockCount: Long = 0L,
    val shepherdName: String = "",
    val title: String = "",
    val note: String = "",
    val receiptBase64: String? = null,
    val entryDateMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val approvalStatus: String = ApprovalStatus.APPROVED.name,
) {
    fun parsedCategory(): EntryCategory = EntryCategory.fromRaw(category)
    fun parsedSubCategory(): ExpenseSubCategory? = ExpenseSubCategory.fromRaw(subCategory)
    fun parsedApprovalStatus(): ApprovalStatus = ApprovalStatus.fromRaw(approvalStatus)
}

data class ShepherdLedgerSummary(
    val shepherdName: String,
    val totalAdvance: Double,
    val totalSalary: Double,
    val entryCount: Int,
) {
    val totalPaid: Double get() = totalAdvance + totalSalary
}

data class LoanPartySummary(
    val partyName: String,
    val taken: Double,
    val repaid: Double,
    val given: Double,
    val recovered: Double,
) {
    val payableRemaining: Double get() = (taken - repaid).coerceAtLeast(0.0)
    val receivableRemaining: Double get() = (given - recovered).coerceAtLeast(0.0)
}

data class FarmSummary(
    val ownerFundsReceived: Double,
    val generalExpenses: Double,
    val farmExpenseSubTotals: Map<ExpenseSubCategory, Double>,
    val medicineVetExpenses: Double,
    val livestockBuyCost: Double,
    val livestockSalesRevenue: Double,
    val shepherdAdvancesTotal: Double,
    val shepherdSalariesTotal: Double,
    val loansTakenTotal: Double,
    val loansRepaidTotal: Double,
    val loansGivenTotal: Double,
    val loansRecoveredTotal: Double,
    val loansPayableBalance: Double,
    val loansReceivableBalance: Double,
    val livestockBoughtCount: Int,
    val livestockSoldCount: Int,
    val currentFlockCount: Int,
    val totalOutflows: Double,
    val remainingCashBalance: Double,
    val pendingApprovalCount: Int,
    val shepherdBreakdown: List<ShepherdLedgerSummary>,
    val loanPartyBreakdown: List<LoanPartySummary>,
)

fun computeFarmSummary(farm: Farm?, entries: List<LedgerEntry>): FarmSummary {
    val pendingCount = entries.count { it.parsedApprovalStatus() == ApprovalStatus.PENDING }
    val approvedEntries = entries.filter { it.parsedApprovalStatus() == ApprovalStatus.APPROVED }

    var ownerFunds = 0.0
    var generalExpenses = 0.0
    var medicineVet = 0.0
    var livestockBuyCost = 0.0
    var livestockSellRev = 0.0
    var advances = 0.0
    var salaries = 0.0
    var loansTaken = 0.0
    var loansRepaid = 0.0
    var loansGiven = 0.0
    var loansRecovered = 0.0
    var boughtCount = 0
    var soldCount = 0

    val subTotals = linkedMapOf<ExpenseSubCategory, Double>()
    val shepherdMap = linkedMapOf<String, MutableList<LedgerEntry>>()
    val loanMap = linkedMapOf<String, MutableList<LedgerEntry>>()

    for (entry in approvedEntries) {
        when (entry.parsedCategory()) {
            EntryCategory.OWNER_FUNDS -> ownerFunds += entry.amount
            EntryCategory.EXPENSE -> {
                generalExpenses += entry.amount
                val sub = entry.parsedSubCategory() ?: ExpenseSubCategory.OTHER
                subTotals[sub] = (subTotals[sub] ?: 0.0) + entry.amount
            }
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
            EntryCategory.LOAN_TAKEN -> {
                loansTaken += entry.amount
                val party = entry.shepherdName.trim().ifEmpty { entry.title.trim().ifEmpty { "Unknown" } }
                loanMap.getOrPut(party) { mutableListOf() }.add(entry)
            }
            EntryCategory.LOAN_REPAY -> {
                loansRepaid += entry.amount
                val party = entry.shepherdName.trim().ifEmpty { entry.title.trim().ifEmpty { "Unknown" } }
                loanMap.getOrPut(party) { mutableListOf() }.add(entry)
            }
            EntryCategory.LOAN_GIVEN -> {
                loansGiven += entry.amount
                val party = entry.shepherdName.trim().ifEmpty { entry.title.trim().ifEmpty { "Unknown" } }
                loanMap.getOrPut(party) { mutableListOf() }.add(entry)
            }
            EntryCategory.LOAN_RECOVER -> {
                loansRecovered += entry.amount
                val party = entry.shepherdName.trim().ifEmpty { entry.title.trim().ifEmpty { "Unknown" } }
                loanMap.getOrPut(party) { mutableListOf() }.add(entry)
            }
        }
    }

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

    val loanBreakdown = loanMap.map { (party, list) ->
        LoanPartySummary(
            partyName = party,
            taken = list.filter { it.parsedCategory() == EntryCategory.LOAN_TAKEN }.sumOf { it.amount },
            repaid = list.filter { it.parsedCategory() == EntryCategory.LOAN_REPAY }.sumOf { it.amount },
            given = list.filter { it.parsedCategory() == EntryCategory.LOAN_GIVEN }.sumOf { it.amount },
            recovered = list.filter { it.parsedCategory() == EntryCategory.LOAN_RECOVER }.sumOf { it.amount },
        )
    }.sortedByDescending { it.payableRemaining + it.receivableRemaining }

    val totalOutflows = generalExpenses + medicineVet + livestockBuyCost + advances + salaries + loansGiven + loansRepaid
    val totalInflows = ownerFunds + livestockSellRev + loansTaken + loansRecovered
    val remainingCash = totalInflows - totalOutflows
    val startFlock = (farm?.initialFlockCount ?: 0L).toInt()
    val currentFlock = (startFlock + boughtCount - soldCount).coerceAtLeast(0)

    return FarmSummary(
        ownerFundsReceived = ownerFunds,
        generalExpenses = generalExpenses,
        farmExpenseSubTotals = subTotals,
        medicineVetExpenses = medicineVet,
        livestockBuyCost = livestockBuyCost,
        livestockSalesRevenue = livestockSellRev,
        shepherdAdvancesTotal = advances,
        shepherdSalariesTotal = salaries,
        loansTakenTotal = loansTaken,
        loansRepaidTotal = loansRepaid,
        loansGivenTotal = loansGiven,
        loansRecoveredTotal = loansRecovered,
        loansPayableBalance = (loansTaken - loansRepaid).coerceAtLeast(0.0),
        loansReceivableBalance = (loansGiven - loansRecovered).coerceAtLeast(0.0),
        livestockBoughtCount = boughtCount,
        livestockSoldCount = soldCount,
        currentFlockCount = currentFlock,
        totalOutflows = totalOutflows,
        remainingCashBalance = remainingCash,
        pendingApprovalCount = pendingCount,
        shepherdBreakdown = shepherdBreakdown,
        loanPartyBreakdown = loanBreakdown,
    )
}
