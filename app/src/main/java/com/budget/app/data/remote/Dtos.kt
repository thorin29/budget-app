package com.budget.app.data.remote

import kotlinx.serialization.Serializable

/*
 * The server's JSON, as far as the phone needs it. Unknown fields are ignored
 * (see ApiClient's Json), so the server can add fields without breaking older
 * phone builds. Money is whole cents.
 */

@Serializable
data class Meta(val app: String = "", val version: String = "", val api: Int = 0)

@Serializable
data class LoginResponse(val proof: String, val expiresAt: String = "")

@Serializable
data class DeviceInfo(val id: String, val name: String, val expiresAt: String? = null)

@Serializable
data class EnrollResponse(val token: String, val expiresAt: String = "", val device: DeviceInfo)

@Serializable
data class UserInfo(val id: String, val username: String)

@Serializable
data class MeResponse(val user: UserInfo, val device: DeviceInfo)

@Serializable
data class RefreshResponse(val token: String, val expiresAt: String = "")

@Serializable
data class YearMonthRef(val year: Int, val month: Int)

@Serializable
data class MonthEntry(
    val lineItemId: String,
    val name: String,
    val year: Int,
    val month: Int,
    val kind: String,
    val categoryName: String? = null,
    val accountName: String? = null,
    val paymentUrl: String? = null,
    val dueDay: Int? = null,
    val budgetedCents: Long = 0,
    val plannedCents: Long = 0,
    val actualCents: Long? = null,
    val paidOn: String? = null,
    val adjusted: Boolean = false,
    val paidFromSurplus: Boolean = false,
    val carriedFrom: YearMonthRef? = null,
) {
    val isIncome: Boolean get() = kind == "INCOME"
    val settled: Boolean get() = actualCents != null
}

@Serializable
data class HalfInfo(val index: Int = 0, val label: String = "")

@Serializable
data class HalfSummary(
    val half: HalfInfo = HalfInfo(),
    val entries: List<MonthEntry> = emptyList(),
    val budgetedCents: Long = 0,
    val paidCents: Long = 0,
    val remainingCents: Long = 0,
)

@Serializable
data class MonthView(
    val year: Int,
    val month: Int,
    val splitDay: Int = 15,
    val halves: List<HalfSummary> = emptyList(),
    val surplusItems: List<MonthEntry> = emptyList(),
    val surplusBudgetedCents: Long = 0,
    val surplusPaidCents: Long = 0,
    val carried: List<MonthEntry> = emptyList(),
    val carriedCents: Long = 0,
    val income: List<MonthEntry> = emptyList(),
    val incomeExpectedCents: Long = 0,
    val incomeReceivedCents: Long = 0,
    val billsBalanceCents: Long? = null,
    val billsBalanceFrom: YearMonthRef? = null,
    val billsAccountId: String? = null,
    val billsAccountName: String? = null,
    val transferAfterFirstCents: Long? = null,
    val transferAfterSecondCents: Long? = null,
    val totalBudgetedCents: Long = 0,
    val totalPaidCents: Long = 0,
    val totalRemainingCents: Long = 0,
)

@Serializable
data class LowPoint(val date: String = "", val balanceCents: Long = 0)

@Serializable
data class Projection(
    val safeToPayCents: Long = 0,
    val lowPoint: LowPoint = LowPoint(),
    val balanceMissing: Boolean = false,
    val horizonDays: Int = 45,
    val nextPayDate: String? = null,
    val committedBeforeNextPayCents: Long = 0,
)

@Serializable
data class ErrorDetail(val code: String = "", val message: String = "")

@Serializable
data class ErrorBody(val error: ErrorDetail = ErrorDetail())
