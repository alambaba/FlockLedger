package com.example

import com.example.data.model.ApprovalStatus
import com.example.data.model.EntryCategory
import com.example.data.model.Farm
import com.example.data.model.LedgerEntry
import com.example.data.model.computeFarmSummary
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(50_000.0, summary.remainingCashBalance, 0.01)
        assertEquals(52, summary.currentFlockCount)
        assertEquals(4, summary.livestockBoughtCount)
        assertEquals(2, summary.livestockSoldCount)

        val ali = summary.shepherdBreakdown.first { it.shepherdName == "Ali" }
        assertEquals(5_000.0, ali.totalAdvance, 0.01)
        assertEquals(15_000.0, ali.totalSalary, 0.01)
        assertEquals(20_000.0, ali.totalPaid, 0.01)
    }

    @Test
    fun computeFarmSummary_ignoresPendingAndRejectedAndCalculatesLoans() {
        val farm = Farm(id = "f2", name = "Laar Sheep Farm", initialFlockCount = 54L)
        val entries = listOf(
            LedgerEntry(id = "1", category = EntryCategory.OWNER_FUNDS.name, amount = 50_000.0, title = "Cash"),
            LedgerEntry(
                id = "2",
                category = EntryCategory.LIVESTOCK_BUY.name,
                amount = 20_000.0,
                livestockCount = 3L,
                title = "Pending Buy",
                approvalStatus = ApprovalStatus.PENDING.name
            ),
            LedgerEntry(
                id = "3",
                category = EntryCategory.EXPENSE.name,
                amount = 5_000.0,
                title = "Rejected Expense",
                approvalStatus = ApprovalStatus.REJECTED.name
            ),
            LedgerEntry(id = "4", category = EntryCategory.LOAN_TAKEN.name, amount = 30_000.0, shepherdName = "Karim", title = "Borrowed"),
            LedgerEntry(id = "5", category = EntryCategory.LOAN_REPAY.name, amount = 10_000.0, shepherdName = "Karim", title = "Repaid"),
            LedgerEntry(id = "6", category = EntryCategory.LOAN_GIVEN.name, amount = 15_000.0, shepherdName = "Aslam", title = "Lent"),
        )

        val summary = computeFarmSummary(farm, entries)
        assertEquals(1, summary.pendingApprovalCount)
        // Flock stays 54 because pending buy of 3 sheep is NOT approved yet
        assertEquals(54, summary.currentFlockCount)
        // Payable loan: 30,000 - 10,000 = 20,000; Receivable loan: 15,000
        assertEquals(20_000.0, summary.loansPayableBalance, 0.01)
        assertEquals(15_000.0, summary.loansReceivableBalance, 0.01)
        // Cash: +50,000 +30,000 -10,000 -15,000 = 55,000
        assertEquals(55_000.0, summary.remainingCashBalance, 0.01)
    }

    @Test
    fun pinHashing_resolvesSeparateAdminAndCaretakerRoles() {
        val adminSalt = "a1b2"
        val caretakerSalt = "c3d4"
        val prefs = UserPreferences(
            adminPinSalt = adminSalt,
            adminPinHash = AppPreferencesRepository.hashPin("4321", adminSalt),
            caretakerPinSalt = caretakerSalt,
            caretakerPinHash = AppPreferencesRepository.hashPin("1122", caretakerSalt),
        )

        assertEquals(UserRole.ADMIN, AppPreferencesRepository.resolveRoleForPin("4321", prefs))
        assertEquals(UserRole.CARETAKER, AppPreferencesRepository.resolveRoleForPin("1122", prefs))
        assertNull(AppPreferencesRepository.resolveRoleForPin("9999", prefs))
    }

    @Test
    fun computeFarmSummary_talliesFarmExpenseSubCategoriesWithoutDoubleCounting() {
        val farm = Farm(id = "f3", name = "Laar Sheep Farm", initialFlockCount = 54L)
        val entries = listOf(
            LedgerEntry(id = "1", category = EntryCategory.OWNER_FUNDS.name, amount = 100_000.0, title = "Cash"),
            LedgerEntry(
                id = "2",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.TREATMENT.name,
                amount = 4_000.0,
                title = "Deworming"
            ),
            LedgerEntry(
                id = "3",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.FEED.name,
                amount = 15_000.0,
                title = "Dry Fodder"
            ),
            LedgerEntry(
                id = "4",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.FARM_MAINTENANCE.name,
                amount = 6_000.0,
                title = "Fence Repair"
            ),
            LedgerEntry(
                id = "5",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.MACHINERY.name,
                amount = 10_000.0,
                title = "Chaff Cutter"
            ),
            // Legacy entry from v1/v2 with blank subCategory
            LedgerEntry(
                id = "6",
                category = EntryCategory.EXPENSE.name,
                subCategory = "",
                amount = 5_000.0,
                title = "Legacy Farm Expense"
            ),
        )

        val summary = computeFarmSummary(farm, entries)
        assertEquals(40_000.0, summary.generalExpenses, 0.01)
        assertEquals(40_000.0, summary.totalOutflows, 0.01)
        assertEquals(60_000.0, summary.remainingCashBalance, 0.01)
        assertEquals(4_000.0, summary.farmExpenseSubTotals[com.example.data.model.ExpenseSubCategory.TREATMENT] ?: 0.0, 0.01)
        assertEquals(15_000.0, summary.farmExpenseSubTotals[com.example.data.model.ExpenseSubCategory.FEED] ?: 0.0, 0.01)
        assertEquals(6_000.0, summary.farmExpenseSubTotals[com.example.data.model.ExpenseSubCategory.FARM_MAINTENANCE] ?: 0.0, 0.01)
        assertEquals(10_000.0, summary.farmExpenseSubTotals[com.example.data.model.ExpenseSubCategory.MACHINERY] ?: 0.0, 0.01)
        assertEquals(5_000.0, summary.farmExpenseSubTotals[com.example.data.model.ExpenseSubCategory.OTHER] ?: 0.0, 0.01)
    }

    @Test
    fun versionComparison_detectsNewerAppReleasesAccurately() {
        org.junit.Assert.assertTrue(com.example.data.backup.BackupManager.isVersionNewer("v1.2", "1.1"))
        org.junit.Assert.assertTrue(com.example.data.backup.BackupManager.isVersionNewer("1.1.1", "1.1"))
        org.junit.Assert.assertFalse(com.example.data.backup.BackupManager.isVersionNewer("v1.1", "1.1"))
        org.junit.Assert.assertFalse(com.example.data.backup.BackupManager.isVersionNewer("1.0", "1.1"))
    }

    @Test
    fun normalizeUpdateUrl_handlesGithubUrlsAndRepoSlugs() {
        assertEquals(
            "https://api.github.com/repos/alam/flockledger/releases/latest",
            com.example.data.backup.BackupManager.normalizeUpdateUrl("alam/flockledger")
        )
        assertEquals(
            "https://api.github.com/repos/alam/flockledger/releases/latest",
            com.example.data.backup.BackupManager.normalizeUpdateUrl("https://github.com/alam/flockledger")
        )
        assertEquals(
            "https://api.github.com/repos/alam/flockledger/releases/latest",
            com.example.data.backup.BackupManager.normalizeUpdateUrl("https://github.com/alam/flockledger.git/")
        )
        assertEquals(
            "https://example.com/version.json",
            com.example.data.backup.BackupManager.normalizeUpdateUrl("https://example.com/version.json")
        )
    }

    @Test
    fun validationReportFigures_matchPdfAndDashboardAccounting() {
        val farm = Farm(id = "val", name = "Laar Sheep Farm", initialFlockCount = 0L)
        val entries = listOf(
            LedgerEntry(id = "1", category = EntryCategory.OWNER_FUNDS.name, amount = 2_650_345.0, title = "Owner Funds"),
            LedgerEntry(id = "2", category = EntryCategory.LIVESTOCK_SELL.name, amount = 45_000.0, livestockCount = 2L, title = "Sales"),
            LedgerEntry(id = "3", category = EntryCategory.EXPENSE.name, amount = 608_287.0, title = "Farm Expenses"),
            LedgerEntry(id = "4", category = EntryCategory.MEDICINE_VET.name, amount = 2_000.0, title = "Medicine & Vet"),
            LedgerEntry(id = "5", category = EntryCategory.LIVESTOCK_BUY.name, amount = 1_870_400.0, livestockCount = 56L, title = "Livestock Purchases"),
            LedgerEntry(id = "6", category = EntryCategory.SHEPHERD_SALARY.name, amount = 8_000.0, shepherdName = "Sajan", title = "Salaries"),
            LedgerEntry(id = "7", category = EntryCategory.LOAN_GIVEN.name, amount = 206_658.0, shepherdName = "Party A", title = "Loan Given"),
            LedgerEntry(id = "8", category = EntryCategory.LOAN_TAKEN.name, amount = 2_000.0, shepherdName = "Party B", title = "Loan Taken"),
        )

        val summary = computeFarmSummary(farm, entries)

        assertEquals(2_650_345.0, summary.ownerFundsReceived, 0.01)
        assertEquals(45_000.0, summary.livestockSalesRevenue, 0.01)
        assertEquals(608_287.0, summary.generalExpenses, 0.01)
        assertEquals(2_000.0, summary.medicineVetExpenses, 0.01)
        assertEquals(1_870_400.0, summary.livestockBuyCost, 0.01)
        assertEquals(8_000.0, summary.shepherdSalariesTotal, 0.01)
        assertEquals(206_658.0, summary.loansGivenTotal, 0.01)
        assertEquals(2_000.0, summary.loansTakenTotal, 0.01)

        // Cash validation
        assertEquals(2_697_345.0, summary.totalCashAvailable, 0.01)
        assertEquals(2_695_345.0, summary.totalOutflows, 0.01)
        assertEquals(2_000.0, summary.remainingCashBalance, 0.01)

        // Livestock validation
        assertEquals(0, summary.openingFlockCount)
        assertEquals(56, summary.livestockBoughtCount)
        assertEquals(2, summary.livestockSoldCount)
        assertEquals(54, summary.currentFlockCount)
        assertEquals(33_400.0, summary.avgPurchaseCostPerSheep, 0.01)
        assertEquals(1_803_600.0, summary.livestockInventoryValue, 0.01)

        // Accounting equation: Assets = Liabilities + Owner's Equity
        assertEquals(summary.totalAssets, summary.totalLiabilities + summary.totalOwnerEquity, 0.01)
    }

    @Test
    fun creditTransactionsDeathsAndAdvanceRecovery_calculateWithoutCashDistortion() {
        val farm = Farm(id = "c1", name = "Laar Sheep Farm", initialFlockCount = 10L)
        val entries = listOf(
            LedgerEntry(id = "1", category = EntryCategory.OWNER_FUNDS.name, amount = 100_000.0, title = "Capital"),
            LedgerEntry(id = "2", category = EntryCategory.LIVESTOCK_BUY.name, amount = 50_000.0, livestockCount = 5L, title = "Buy 5"),
            // Credit feed expense should NOT reduce Cash, but creates Other Payables & Operating Expense
            LedgerEntry(
                id = "3",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.FEED.name,
                paymentMethod = com.example.data.model.PaymentMethod.CREDIT.name,
                amount = 10_000.0,
                title = "Credit Feed"
            ),
            // Shepherd advance 8,000 then recovery 3,000 -> Outstanding Advance = 5,000, Cash -5,000 net
            LedgerEntry(id = "4", category = EntryCategory.SHEPHERD_ADVANCE.name, amount = 8_000.0, shepherdName = "Ali", title = "Adv"),
            LedgerEntry(id = "5", category = EntryCategory.SHEPHERD_ADVANCE_RECOVERY.name, amount = 3_000.0, shepherdName = "Ali", title = "Recov"),
            // 1 sheep death -> Current flock = 10 + 5 - 1 = 14
            LedgerEntry(id = "6", category = EntryCategory.LIVESTOCK_DEATH.name, amount = 0.0, livestockCount = 1L, title = "Death"),
        )

        val summary = computeFarmSummary(farm, entries)

        assertEquals(14, summary.currentFlockCount)
        assertEquals(1, summary.livestockDeathCount)
        assertEquals(10_000.0, summary.otherPayablesBalance, 0.01)
        assertEquals(5_000.0, summary.shepherdAdvancesOutstanding, 0.01)
        // Cash = 100,000 + 3,000 - 50,000 - 8,000 = 45,000 (10k credit feed excluded from cash)
        assertEquals(45_000.0, summary.remainingCashBalance, 0.01)
        assertEquals(summary.totalAssets, summary.totalLiabilities + summary.totalOwnerEquity, 0.01)
    }

    @Test
    fun monthlyBudgetTracking_calculatesCurrentMonthUsageAndAlerts() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 15, 10, 0, 0)
        }
        val novMillis = cal.timeInMillis
        val octMillis = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 20, 10, 0, 0)
        }.timeInMillis

        val entries = listOf(
            // Current month Feed = 42,000 (84% of 50,000 -> APPROACHING at 80%)
            LedgerEntry(
                id = "f1",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.FEED.name,
                amount = 42_000.0,
                entryDateMillis = novMillis,
                title = "Nov Feed"
            ),
            // Previous month Feed should be ignored for November budget
            LedgerEntry(
                id = "f_old",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.FEED.name,
                amount = 30_000.0,
                entryDateMillis = octMillis,
                title = "Oct Feed"
            ),
            // Pending Feed should not count until approved
            LedgerEntry(
                id = "f_pending",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.FEED.name,
                amount = 15_000.0,
                entryDateMillis = novMillis,
                approvalStatus = ApprovalStatus.PENDING.name,
                title = "Pending Feed"
            ),
            // Current month Medicine & Vet + Treatment = 12,000 (120% of 10,000 -> EXCEEDED)
            LedgerEntry(
                id = "m1",
                category = EntryCategory.MEDICINE_VET.name,
                amount = 7_500.0,
                entryDateMillis = novMillis,
                title = "Vaccines"
            ),
            LedgerEntry(
                id = "m2",
                category = EntryCategory.EXPENSE.name,
                subCategory = com.example.data.model.ExpenseSubCategory.TREATMENT.name,
                amount = 4_500.0,
                entryDateMillis = novMillis,
                title = "Vet Treatment"
            ),
        )

        val status = com.example.data.model.computeMonthlyBudgetStatus(
            entries = entries,
            feedCap = 50_000.0,
            medVetCap = 10_000.0,
            alertThresholdPercent = 80,
            nowMillis = novMillis,
        )

        assertEquals(42_000.0, status.feedBudget.spentThisMonth, 0.01)
        assertEquals(8_000.0, status.feedBudget.remainingAmount, 0.01)
        assertEquals(84, status.feedBudget.percentUsed)
        assertEquals(com.example.data.model.BudgetAlertLevel.APPROACHING, status.feedBudget.alertLevel)

        assertEquals(12_000.0, status.medVetBudget.spentThisMonth, 0.01)
        assertEquals(2_000.0, status.medVetBudget.overAmount, 0.01)
        assertEquals(120, status.medVetBudget.percentUsed)
        assertEquals(com.example.data.model.BudgetAlertLevel.EXCEEDED, status.medVetBudget.alertLevel)
        org.junit.Assert.assertTrue(status.hasAnyAlert)
    }
}


