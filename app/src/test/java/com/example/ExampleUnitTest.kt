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
}
