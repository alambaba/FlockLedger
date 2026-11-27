package com.example.data.model

enum class EntryCategory {
    EXPENSE,
    MEDICINE_VET,
    LIVESTOCK_BUY,
    LIVESTOCK_SELL,
    LIVESTOCK_DEATH,
    SHEPHERD_ADVANCE,
    SHEPHERD_ADVANCE_RECOVERY,
    SHEPHERD_SALARY,
    OWNER_FUNDS,
    OTHER_INCOME,
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

enum class PaymentMethod {
    CASH,
    CREDIT;

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: CASH
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

enum class CommentStatus {
    OPEN,
    RESOLVED;

    companion object {
        fun fromRaw(raw: String?): CommentStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
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
    val paymentMethod: String = PaymentMethod.CASH.name,
) {
    fun parsedCategory(): EntryCategory = EntryCategory.fromRaw(category)
    fun parsedSubCategory(): ExpenseSubCategory? = ExpenseSubCategory.fromRaw(subCategory)
    fun parsedApprovalStatus(): ApprovalStatus = ApprovalStatus.fromRaw(approvalStatus)
    fun parsedPaymentMethod(): PaymentMethod = PaymentMethod.fromRaw(paymentMethod)
}

data class ReportComment(
    val id: String = "",
    val farmId: String = "",
    val entryId: String? = null,
    val entryTitleSnapshot: String = "",
    val authorRole: String = "Caretaker",
    val commentText: String = "",
    val status: String = CommentStatus.OPEN.name,
    val adminResponse: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun parsedStatus(): CommentStatus = CommentStatus.fromRaw(status)
}

data class ShepherdLedgerSummary(
    val shepherdName: String,
    val totalAdvance: Double,
    val advanceRecovery: Double,
    val totalSalary: Double,
    val salaryPaid: Double,
    val salaryPayable: Double,
    val entryCount: Int,
) {
    val outstandingAdvance: Double get() = (totalAdvance - advanceRecovery).coerceAtLeast(0.0)
    val totalPaid: Double get() = salaryPaid + totalAdvance
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
    // Section 1: Capital, Income & Expenses (Total Approved)
    val ownerFundsReceived: Double,
    val livestockSalesRevenue: Double,
    val otherIncomeTotal: Double,
    val generalExpenses: Double,
    val feedExpenses: Double,
    val operatingFarmExpenses: Double,
    val farmExpenseSubTotals: Map<ExpenseSubCategory, Double>,
    val medicineVetExpenses: Double,
    val livestockBuyCost: Double,
    val shepherdAdvancesTotal: Double,
    val shepherdAdvanceRecoveryTotal: Double,
    val shepherdAdvancesOutstanding: Double,
    val shepherdSalariesTotal: Double,
    val shepherdSalariesPaid: Double,
    val shepherdSalariesPayable: Double,
    val totalOperatingExpenses: Double,
    // Loans
    val loansTakenTotal: Double,
    val loansRepaidTotal: Double,
    val loansGivenTotal: Double,
    val loansRecoveredTotal: Double,
    val loansPayableBalance: Double,
    val loansReceivableBalance: Double,
    // Cash Flow (Actual Cash Transactions Only)
    val totalCashAvailable: Double,
    val totalOutflows: Double,
    val remainingCashBalance: Double,
    // Section 2: Balance Sheet (Assets = Liabilities + Owner's Equity)
    val accountsReceivableBalance: Double,
    val otherPayablesBalance: Double,
    val machineryEquipmentValue: Double,
    val livestockInventoryValue: Double,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netAssets: Double,
    val openingFlockEquity: Double,
    val costOfLivestockSold: Double,
    val retainedProfitLoss: Double,
    val totalOwnerEquity: Double,
    // Section 3: Livestock Inventory
    val openingFlockCount: Int,
    val livestockBoughtCount: Int,
    val livestockSoldCount: Int,
    val livestockDeathCount: Int,
    val currentFlockCount: Int,
    val avgPurchaseCostPerSheep: Double,
    // Meta & Breakdowns
    val pendingApprovalCount: Int,
    val shepherdBreakdown: List<ShepherdLedgerSummary>,
    val loanPartyBreakdown: List<LoanPartySummary>,
)

fun computeFarmSummary(farm: Farm?, entries: List<LedgerEntry>): FarmSummary {
    val pendingCount = entries.count { it.parsedApprovalStatus() == ApprovalStatus.PENDING }
    val approvedEntries = entries.filter { it.parsedApprovalStatus() == ApprovalStatus.APPROVED }

    var ownerFunds = 0.0
    var ownerFundsCash = 0.0
    var livestockSellRev = 0.0
    var livestockSellCash = 0.0
    var livestockSellCredit = 0.0
    var otherIncome = 0.0
    var otherIncomeCash = 0.0
    var otherIncomeCredit = 0.0

    var generalExpenses = 0.0
    var generalExpensesCash = 0.0
    var generalExpensesCredit = 0.0
    var medicineVet = 0.0
    var medicineVetCash = 0.0
    var medicineVetCredit = 0.0
    var livestockBuyCost = 0.0
    var livestockBuyCash = 0.0
    var livestockBuyCredit = 0.0

    var advances = 0.0
    var advancesCash = 0.0
    var advanceRecovery = 0.0
    var advanceRecoveryCash = 0.0
    var salaries = 0.0
    var salariesPaidCash = 0.0
    var salariesPayableCredit = 0.0

    var loansTaken = 0.0
    var loansTakenCash = 0.0
    var loansRepaid = 0.0
    var loansRepaidCash = 0.0
    var loansGiven = 0.0
    var loansGivenCash = 0.0
    var loansRecovered = 0.0
    var loansRecoveredCash = 0.0

    var boughtCount = 0
    var soldCount = 0
    var deathCount = 0

    val subTotals = linkedMapOf<ExpenseSubCategory, Double>()
    val shepherdMap = linkedMapOf<String, MutableList<LedgerEntry>>()
    val loanMap = linkedMapOf<String, MutableList<LedgerEntry>>()

    for (entry in approvedEntries) {
        val isCash = entry.parsedPaymentMethod() == PaymentMethod.CASH
        when (entry.parsedCategory()) {
            EntryCategory.OWNER_FUNDS -> {
                ownerFunds += entry.amount
                if (isCash) ownerFundsCash += entry.amount
            }
            EntryCategory.OTHER_INCOME -> {
                otherIncome += entry.amount
                if (isCash) otherIncomeCash += entry.amount else otherIncomeCredit += entry.amount
            }
            EntryCategory.EXPENSE -> {
                generalExpenses += entry.amount
                if (isCash) generalExpensesCash += entry.amount else generalExpensesCredit += entry.amount
                val sub = entry.parsedSubCategory() ?: ExpenseSubCategory.OTHER
                subTotals[sub] = (subTotals[sub] ?: 0.0) + entry.amount
            }
            EntryCategory.MEDICINE_VET -> {
                medicineVet += entry.amount
                if (isCash) medicineVetCash += entry.amount else medicineVetCredit += entry.amount
            }
            EntryCategory.LIVESTOCK_BUY -> {
                livestockBuyCost += entry.amount
                if (isCash) livestockBuyCash += entry.amount else livestockBuyCredit += entry.amount
                boughtCount += entry.livestockCount.toInt()
            }
            EntryCategory.LIVESTOCK_SELL -> {
                livestockSellRev += entry.amount
                if (isCash) livestockSellCash += entry.amount else livestockSellCredit += entry.amount
                soldCount += entry.livestockCount.toInt()
            }
            EntryCategory.LIVESTOCK_DEATH -> {
                deathCount += entry.livestockCount.toInt()
            }
            EntryCategory.SHEPHERD_ADVANCE -> {
                advances += entry.amount
                if (isCash) advancesCash += entry.amount
                val name = entry.shepherdName.trim().ifEmpty { "Unknown" }
                shepherdMap.getOrPut(name) { mutableListOf() }.add(entry)
            }
            EntryCategory.SHEPHERD_ADVANCE_RECOVERY -> {
                advanceRecovery += entry.amount
                if (isCash) advanceRecoveryCash += entry.amount
                val name = entry.shepherdName.trim().ifEmpty { "Unknown" }
                shepherdMap.getOrPut(name) { mutableListOf() }.add(entry)
            }
            EntryCategory.SHEPHERD_SALARY -> {
                salaries += entry.amount
                if (isCash) salariesPaidCash += entry.amount else salariesPayableCredit += entry.amount
                val name = entry.shepherdName.trim().ifEmpty { "Unknown" }
                shepherdMap.getOrPut(name) { mutableListOf() }.add(entry)
            }
            EntryCategory.LOAN_TAKEN -> {
                loansTaken += entry.amount
                if (isCash) loansTakenCash += entry.amount
                val party = entry.shepherdName.trim().ifEmpty { entry.title.trim().ifEmpty { "Unknown" } }
                loanMap.getOrPut(party) { mutableListOf() }.add(entry)
            }
            EntryCategory.LOAN_REPAY -> {
                loansRepaid += entry.amount
                if (isCash) loansRepaidCash += entry.amount
                val party = entry.shepherdName.trim().ifEmpty { entry.title.trim().ifEmpty { "Unknown" } }
                loanMap.getOrPut(party) { mutableListOf() }.add(entry)
            }
            EntryCategory.LOAN_GIVEN -> {
                loansGiven += entry.amount
                if (isCash) loansGivenCash += entry.amount
                val party = entry.shepherdName.trim().ifEmpty { entry.title.trim().ifEmpty { "Unknown" } }
                loanMap.getOrPut(party) { mutableListOf() }.add(entry)
            }
            EntryCategory.LOAN_RECOVER -> {
                loansRecovered += entry.amount
                if (isCash) loansRecoveredCash += entry.amount
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
        val rec = list.filter { it.parsedCategory() == EntryCategory.SHEPHERD_ADVANCE_RECOVERY }.sumOf { it.amount }
        val salList = list.filter { it.parsedCategory() == EntryCategory.SHEPHERD_SALARY }
        val salTotal = salList.sumOf { it.amount }
        val salPaid = salList.filter { it.parsedPaymentMethod() == PaymentMethod.CASH }.sumOf { it.amount }
        val salPayable = salList.filter { it.parsedPaymentMethod() == PaymentMethod.CREDIT }.sumOf { it.amount }
        ShepherdLedgerSummary(
            shepherdName = name,
            totalAdvance = adv,
            advanceRecovery = rec,
            totalSalary = salTotal,
            salaryPaid = salPaid,
            salaryPayable = salPayable,
            entryCount = list.size,
        )
    }.sortedByDescending { it.totalPaid + it.salaryPayable }

    val loanBreakdown = loanMap.map { (party, list) ->
        LoanPartySummary(
            partyName = party,
            taken = list.filter { it.parsedCategory() == EntryCategory.LOAN_TAKEN }.sumOf { it.amount },
            repaid = list.filter { it.parsedCategory() == EntryCategory.LOAN_REPAY }.sumOf { it.amount },
            given = list.filter { it.parsedCategory() == EntryCategory.LOAN_GIVEN }.sumOf { it.amount },
            recovered = list.filter { it.parsedCategory() == EntryCategory.LOAN_RECOVER }.sumOf { it.amount },
        )
    }.sortedByDescending { it.payableRemaining + it.receivableRemaining }

    // Cash Balance Formula (actual approved cash transactions only)
    val totalCashAvailable = ownerFundsCash + livestockSellCash + otherIncomeCash + loansTakenCash + loansRecoveredCash + advanceRecoveryCash
    val totalOutflows = livestockBuyCash + generalExpensesCash + medicineVetCash + salariesPaidCash + advancesCash + loansGivenCash + loansRepaidCash
    val remainingCash = totalCashAvailable - totalOutflows

    // Livestock Inventory
    val openingFlock = (farm?.initialFlockCount ?: 0L).toInt().coerceAtLeast(0)
    val currentFlock = (openingFlock + boughtCount - soldCount - deathCount).coerceAtLeast(0)
    val avgPurchaseCost = if (boughtCount > 0) livestockBuyCost / boughtCount.toDouble() else 0.0
    val livestockInventoryValue = currentFlock.toDouble() * avgPurchaseCost
    val openingFlockEquity = openingFlock.toDouble() * avgPurchaseCost

    // Operating Expenses vs Capitalized Assets
    val feedExpenses = subTotals[ExpenseSubCategory.FEED] ?: 0.0
    val machineryEquipmentValue = subTotals[ExpenseSubCategory.MACHINERY] ?: 0.0
    val operatingFarmExpenses = (generalExpenses - machineryEquipmentValue).coerceAtLeast(0.0)
    val totalOperatingExpenses = operatingFarmExpenses + medicineVet + salaries

    // Balance Sheet Assets & Liabilities
    val loansPayableBalance = (loansTaken - loansRepaid).coerceAtLeast(0.0)
    val loansReceivableBalance = (loansGiven - loansRecovered).coerceAtLeast(0.0)
    val accountsReceivable = livestockSellCredit + otherIncomeCredit
    val shepherdAdvancesOutstanding = (advances - advanceRecovery).coerceAtLeast(0.0)
    val otherPayables = generalExpensesCredit + medicineVetCredit + livestockBuyCredit + salariesPayableCredit

    val totalAssets = remainingCash +
        livestockInventoryValue +
        loansReceivableBalance +
        accountsReceivable +
        machineryEquipmentValue +
        shepherdAdvancesOutstanding
    val totalLiabilities = loansPayableBalance + otherPayables
    val netAssets = totalAssets - totalLiabilities

    // Accrual Retained Profit / Loss matching Assets = Liabilities + Owner's Equity
    val costOfLivestockSold = (openingFlockEquity + livestockBuyCost) - livestockInventoryValue
    val retainedProfitLoss = (livestockSellRev + otherIncome) - costOfLivestockSold - totalOperatingExpenses
    val totalOwnerEquity = ownerFunds + openingFlockEquity + retainedProfitLoss

    return FarmSummary(
        ownerFundsReceived = ownerFunds,
        livestockSalesRevenue = livestockSellRev,
        otherIncomeTotal = otherIncome,
        generalExpenses = generalExpenses,
        feedExpenses = feedExpenses,
        operatingFarmExpenses = operatingFarmExpenses,
        farmExpenseSubTotals = subTotals,
        medicineVetExpenses = medicineVet,
        livestockBuyCost = livestockBuyCost,
        shepherdAdvancesTotal = advances,
        shepherdAdvanceRecoveryTotal = advanceRecovery,
        shepherdAdvancesOutstanding = shepherdAdvancesOutstanding,
        shepherdSalariesTotal = salaries,
        shepherdSalariesPaid = salariesPaidCash,
        shepherdSalariesPayable = salariesPayableCredit,
        totalOperatingExpenses = totalOperatingExpenses,
        loansTakenTotal = loansTaken,
        loansRepaidTotal = loansRepaid,
        loansGivenTotal = loansGiven,
        loansRecoveredTotal = loansRecovered,
        loansPayableBalance = loansPayableBalance,
        loansReceivableBalance = loansReceivableBalance,
        totalCashAvailable = totalCashAvailable,
        totalOutflows = totalOutflows,
        remainingCashBalance = remainingCash,
        accountsReceivableBalance = accountsReceivable,
        otherPayablesBalance = otherPayables,
        machineryEquipmentValue = machineryEquipmentValue,
        livestockInventoryValue = livestockInventoryValue,
        totalAssets = totalAssets,
        totalLiabilities = totalLiabilities,
        netAssets = netAssets,
        openingFlockEquity = openingFlockEquity,
        costOfLivestockSold = costOfLivestockSold,
        retainedProfitLoss = retainedProfitLoss,
        totalOwnerEquity = totalOwnerEquity,
        openingFlockCount = openingFlock,
        livestockBoughtCount = boughtCount,
        livestockSoldCount = soldCount,
        livestockDeathCount = deathCount,
        currentFlockCount = currentFlock,
        avgPurchaseCostPerSheep = avgPurchaseCost,
        pendingApprovalCount = pendingCount,
        shepherdBreakdown = shepherdBreakdown,
        loanPartyBreakdown = loanBreakdown,
    )
}

enum class BudgetAlertLevel {
    NONE,
    APPROACHING,
    EXCEEDED,
}

data class CategoryBudgetStatus(
    val spentThisMonth: Double,
    val monthlyCap: Double,
    val utilizationFraction: Float,
    val percentUsed: Int,
    val remainingAmount: Double,
    val alertLevel: BudgetAlertLevel,
) {
    val isCapSet: Boolean get() = monthlyCap > 0.0
    val overAmount: Double get() = if (spentThisMonth > monthlyCap && monthlyCap > 0.0) spentThisMonth - monthlyCap else 0.0
}

data class MonthlyBudgetStatus(
    val monthTimestampMillis: Long,
    val alertThresholdPercent: Int,
    val feedBudget: CategoryBudgetStatus,
    val medVetBudget: CategoryBudgetStatus,
) {
    val hasAnyCapSet: Boolean get() = feedBudget.isCapSet || medVetBudget.isCapSet
    val hasAnyAlert: Boolean
        get() = feedBudget.alertLevel != BudgetAlertLevel.NONE ||
            medVetBudget.alertLevel != BudgetAlertLevel.NONE
}

fun computeMonthlyBudgetStatus(
    entries: List<LedgerEntry>,
    feedCap: Double,
    medVetCap: Double,
    alertThresholdPercent: Int = 80,
    nowMillis: Long = System.currentTimeMillis(),
): MonthlyBudgetStatus {
    val calNow = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
    val targetYear = calNow.get(java.util.Calendar.YEAR)
    val targetMonth = calNow.get(java.util.Calendar.MONTH)
    val entryCal = java.util.Calendar.getInstance()

    var feedSpent = 0.0
    var medVetSpent = 0.0

    for (entry in entries) {
        if (entry.parsedApprovalStatus() != ApprovalStatus.APPROVED) continue
        entryCal.timeInMillis = entry.entryDateMillis
        if (entryCal.get(java.util.Calendar.YEAR) != targetYear ||
            entryCal.get(java.util.Calendar.MONTH) != targetMonth
        ) {
            continue
        }
        val cat = entry.parsedCategory()
        val sub = entry.parsedSubCategory()
        if (cat == EntryCategory.EXPENSE && sub == ExpenseSubCategory.FEED) {
            feedSpent += entry.amount
        } else if (cat == EntryCategory.MEDICINE_VET ||
            (cat == EntryCategory.EXPENSE && sub == ExpenseSubCategory.TREATMENT)
        ) {
            medVetSpent += entry.amount
        }
    }

    fun evaluate(spent: Double, cap: Double): CategoryBudgetStatus {
        if (cap <= 0.0) {
            return CategoryBudgetStatus(
                spentThisMonth = spent,
                monthlyCap = 0.0,
                utilizationFraction = 0f,
                percentUsed = 0,
                remainingAmount = 0.0,
                alertLevel = BudgetAlertLevel.NONE,
            )
        }
        val ratio = spent / cap
        val pct = (ratio * 100.0).toInt()
        val level = when {
            spent >= cap -> BudgetAlertLevel.EXCEEDED
            ratio * 100.0 >= alertThresholdPercent -> BudgetAlertLevel.APPROACHING
            else -> BudgetAlertLevel.NONE
        }
        return CategoryBudgetStatus(
            spentThisMonth = spent,
            monthlyCap = cap,
            utilizationFraction = ratio.toFloat().coerceIn(0f, 1f),
            percentUsed = pct,
            remainingAmount = (cap - spent).coerceAtLeast(0.0),
            alertLevel = level,
        )
    }

    return MonthlyBudgetStatus(
        monthTimestampMillis = nowMillis,
        alertThresholdPercent = alertThresholdPercent,
        feedBudget = evaluate(feedSpent, feedCap),
        medVetBudget = evaluate(medVetSpent, medVetCap),
    )
}

fun isCashInflowCategory(cat: EntryCategory): Boolean =
    cat == EntryCategory.OWNER_FUNDS ||
        cat == EntryCategory.LIVESTOCK_SELL ||
        cat == EntryCategory.OTHER_INCOME ||
        cat == EntryCategory.LOAN_TAKEN ||
        cat == EntryCategory.LOAN_RECOVER ||
        cat == EntryCategory.SHEPHERD_ADVANCE_RECOVERY

fun computeCashRunningBalances(entries: List<LedgerEntry>): Map<String, Double> {
    val chronological = entries.sortedWith(
        compareBy<LedgerEntry> { it.entryDateMillis }.thenBy { it.createdAtMillis }.thenBy { it.id }
    )
    val result = LinkedHashMap<String, Double>(chronological.size)
    var running = 0.0
    for (entry in chronological) {
        val cat = entry.parsedCategory()
        val approved = entry.parsedApprovalStatus() == ApprovalStatus.APPROVED
        val isCash = entry.parsedPaymentMethod() == PaymentMethod.CASH
        if (approved && isCash && cat != EntryCategory.LIVESTOCK_DEATH) {
            if (isCashInflowCategory(cat)) {
                running += entry.amount
            } else {
                running -= entry.amount
            }
        }
        result[entry.id] = running
    }
    return result
}

enum class ReportFilterMode {
    MONTHLY,
    YEARLY,
    CATEGORY,
    CUSTOM,
}

fun filterLedgerEntriesForReport(
    entries: List<LedgerEntry>,
    mode: ReportFilterMode,
    year: Int,
    monthZeroBased: Int,
    filterCategory: EntryCategory? = null,
    filterSubCategory: ExpenseSubCategory? = null,
    customStartMillis: Long? = null,
    customEndMillis: Long? = null,
    includePending: Boolean = true,
): List<LedgerEntry> {
    val entryCal = java.util.Calendar.getInstance()
    val startBound = customStartMillis?.let {
        java.util.Calendar.getInstance().apply {
            timeInMillis = it
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val endBound = customEndMillis?.let {
        java.util.Calendar.getInstance().apply {
            timeInMillis = it
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    return entries.filter { entry ->
        val status = entry.parsedApprovalStatus()
        if (status == ApprovalStatus.REJECTED) return@filter false
        if (!includePending && status != ApprovalStatus.APPROVED) return@filter false

        entryCal.timeInMillis = entry.entryDateMillis
        val ey = entryCal.get(java.util.Calendar.YEAR)
        val em = entryCal.get(java.util.Calendar.MONTH)

        when (mode) {
            ReportFilterMode.MONTHLY -> ey == year && em == monthZeroBased
            ReportFilterMode.YEARLY -> ey == year
            ReportFilterMode.CATEGORY -> {
                val catOk = filterCategory == null || entry.parsedCategory() == filterCategory
                val subOk = filterSubCategory == null ||
                    (entry.parsedCategory() == EntryCategory.EXPENSE &&
                        (entry.parsedSubCategory() ?: ExpenseSubCategory.OTHER) == filterSubCategory)
                catOk && subOk
            }
            ReportFilterMode.CUSTOM -> {
                val afterStart = startBound == null || entry.entryDateMillis >= startBound
                val beforeEnd = endBound == null || entry.entryDateMillis <= endBound
                afterStart && beforeEnd
            }
        }
    }
}


