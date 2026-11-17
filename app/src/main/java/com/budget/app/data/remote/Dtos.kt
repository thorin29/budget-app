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
data class JoinResponse(val token: String, val expiresAt: String = "", val device: DeviceInfo, val user: UserInfo)

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
    /** Recorded on its own from a bank charge (server 0.23+). */
    val autoPaid: Boolean = false,
    /** Tied to a bank charge. */
    val bankLinked: Boolean = false,
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
    /** The payday the month divides at, when it follows the pay calendar (server 0.25+). */
    val splitPayday: String? = null,
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
data class ProjectionEvent(val name: String = "", val amountCents: Long = 0, val kind: String = "")

@Serializable
data class ProjectionDay(
    val date: String = "",
    val outCents: Long = 0,
    val inCents: Long = 0,
    /** Closing balance for the day. */
    val balanceCents: Long = 0,
    val events: List<ProjectionEvent> = emptyList(),
)

@Serializable
data class Projection(
    val safeToPayCents: Long = 0,
    val lowPoint: LowPoint = LowPoint(),
    val balanceMissing: Boolean = false,
    val horizonDays: Int = 45,
    val nextPayDate: String? = null,
    val committedBeforeNextPayCents: Long = 0,
    val openingBalanceCents: Long = 0,
    val bufferCents: Long = 0,
    val shortfall: Boolean = false,
    val days: List<ProjectionDay> = emptyList(),
)

@Serializable
data class ErrorDetail(val code: String = "", val message: String = "")

@Serializable
data class ErrorBody(val error: ErrorDetail = ErrorDetail())

// ------------------------------------------------------------ cards (server 0.27+)

@Serializable
data class CardColorDto(val id: String = "graphite", val from: String = "#2b2f36", val to: String = "#4a505a", val ink: String = "light")

@Serializable
data class CardStatusDto(
    val nextDue: String = "",
    /** Left to pay on the current statement. */
    val dueCents: Long = 0,
    val statementCents: Long = 0,
    /** What to pay this cycle: the statement, or the monthly payoff on a 0% balance. */
    val suggestedCents: Long = 0,
    /** Charges since the statement closed: building toward the next one. */
    val newChargesCents: Long = 0,
    /** statement | payoff | paid */
    val plan: String = "statement",
    val paymentsLeft: Int? = null,
    val promoEndsOn: String? = null,
)

@Serializable
data class CardDto(
    val id: String,
    val name: String = "",
    val institution: String? = null,
    val lastDigits: String? = null,
    val color: CardColorDto = CardColorDto(),
    val owedNowCents: Long = 0,
    val status: CardStatusDto? = null,
)

@Serializable
data class CardsResponse(
    val totalOwedCents: Long = 0,
    val payThisCycleCents: Long = 0,
    val nextDue: String? = null,
    val cards: List<CardDto> = emptyList(),
)

// ------------------------------------------------------------ review (server 0.23+)

@Serializable
data class LooksPaidDto(
    val lineItemId: String,
    val year: Int,
    val month: Int,
    val billName: String = "",
    val kind: String = "BILL",
    val budgetedCents: Long = 0,
    val txnId: String,
    val amountCents: Long = 0,
    val postedOn: String = "",
    val merchant: String = "",
    val account: String = "",
    val reason: String = "",
) {
    val isIncome: Boolean get() = kind == "INCOME"
}

@Serializable
data class AutoRecordedDto(
    val lineItemId: String,
    val year: Int,
    val month: Int,
    val name: String = "",
    val amountCents: Long = 0,
    val paidOn: String? = null,
    val merchant: String? = null,
)

@Serializable
data class DueDayMovedDto(val lineItemId: String, val name: String = "", val from: Int? = null, val to: Int? = null)

@Serializable
data class ReviewResponse(
    val looksPaid: List<LooksPaidDto> = emptyList(),
    val repeats: Int = 0,
    val recorded: List<AutoRecordedDto> = emptyList(),
    val moved: List<DueDayMovedDto> = emptyList(),
    val count: Int = 0,
)

// ------------------------------------------------------------ insights (server 0.27+)

@Serializable
data class CategorySpendDto(
    val id: String? = null,
    val name: String = "",
    val cents: Long = 0,
    val averageCents: Long = 0,
    /** Server 0.32+: the same month a year before, when history reaches that far. */
    val lastYearCents: Long? = null,
)

@Serializable
data class MonthTotalDto(val year: Int = 0, val month: Int = 0, val everydayCents: Long = 0, val billsCents: Long = 0, val inCents: Long = 0, val netCents: Long = 0)

@Serializable
data class MerchantSpendDto(val merchant: String = "", val cents: Long = 0, val count: Int = 0)

@Serializable
data class ManagedDto(val name: String = "", val heldCents: Long = 0, val monthInCents: Long = 0, val monthOutCents: Long = 0)

@Serializable
data class LastYearDto(val everydayCents: Long = 0, val billsCents: Long = 0, val inCents: Long = 0)

@Serializable
data class PayoffDto(
    val name: String = "",
    val startCents: Long? = null,
    val balanceCents: Long = 0,
    val paidCents: Long? = null,
    val endsOn: String = "",
    val paymentsLeft: Int = 0,
    val monthlyCents: Long = 0,
    val planMonthlyCents: Long? = null,
    val onTrack: Boolean = true,
)

@Serializable
data class MonthAmountDto(val label: String = "", val cents: Long = 0)

@Serializable
data class CategoryMonthDto(val year: Int = 0, val month: Int = 0, val cents: Long = 0)

@Serializable
data class CategoryMerchantDto(val merchant: String = "", val cents: Long = 0, val count: Int = 0, val averageCents: Long = 0)

@Serializable
data class CategoryChargeDto(val id: String = "", val postedOn: String = "", val merchant: String = "", val account: String = "", val cents: Long = 0)

/** Server 0.32+: GET /api/v1/insights/category. */
@Serializable
data class CategoryDetailResponse(
    val id: String? = null,
    val name: String = "",
    val year: Int = 0,
    val month: Int = 0,
    val months: List<CategoryMonthDto> = emptyList(),
    val merchants: List<CategoryMerchantDto> = emptyList(),
    val charges: List<CategoryChargeDto> = emptyList(),
)

@Serializable
data class InsightsResponse(
    val year: Int = 0,
    val month: Int = 0,
    val everydayCents: Long = 0,
    val everydayAverageCents: Long = 0,
    val billsCents: Long = 0,
    val inCents: Long = 0,
    val netCents: Long = 0,
    val categories: List<CategorySpendDto> = emptyList(),
    val uncategorizedCount: Int = 0,
    /** Server 0.30+: the month in progress against a usual month by the same day. */
    val pace: PaceDto? = null,
    /** Server 0.29+: suggestions from the last six complete months, most worthwhile first. */
    val suggestions: List<SuggestionDto> = emptyList(),
    /** Server 0.31+: yearly charges expected in the next 45 days. */
    val upcoming: List<UpcomingDto> = emptyList(),
    // Server 0.32+:
    /** What the bills account has left after this month's bills and paychecks. */
    val leftAfterBillsCents: Long? = null,
    val weekCents: Long? = null,
    val lastYear: LastYearDto? = null,
    val months: List<MonthTotalDto> = emptyList(),
    val changes: List<CategorySpendDto> = emptyList(),
    val merchants: List<MerchantSpendDto> = emptyList(),
    val managed: List<ManagedDto> = emptyList(),
    val payoff: List<PayoffDto> = emptyList(),
    /** Server 0.33+, month in progress: the most everyday spending can be each month and keep within the plan. */
    val limit: LimitDto? = null,
    /** Server 0.34+, month in progress: where the income goes, the card budget, category targets. */
    val budget: BudgetPlanDto? = null,
)

@Serializable
data class IncomeBarDto(
    val bankBillsCents: Long = 0,
    val cardBillsCents: Long = 0,
    val cardSpendingCents: Long = 0,
    val debitSpendingCents: Long = 0,
    val payoffCents: Long = 0,
    /** Negative when spending and bills run past income. */
    val leftOverCents: Long = 0,
)

@Serializable
data class CardBudgetRowDto(
    val id: String = "",
    val name: String = "",
    val color: String? = null,
    val budgetCents: Long = 0,
    val billsCents: Long = 0,
    val spendingBudgetCents: Long = 0,
    val averageSpendingCents: Long = 0,
    val averageBillsCents: Long = 0,
    val periodFrom: String = "",
    val periodIsStatement: Boolean = false,
    val sinceBillsCents: Long = 0,
    val sinceSpendingCents: Long = 0,
    val billsToComeCents: Long = 0,
    val leftCents: Long = 0,
    val balanceCents: Long = 0,
)

@Serializable
data class CardBudgetDto(
    val totalCents: Long = 0,
    val billsCents: Long = 0,
    val spendingCents: Long = 0,
    val averageCents: Long = 0,
    val cards: List<CardBudgetRowDto> = emptyList(),
)

@Serializable
data class CategoryTargetDto(val id: String? = null, val name: String = "", val essential: Boolean = false, val averageCents: Long = 0, val targetCents: Long = 0, val monthCents: Long = 0)

@Serializable
data class CategoryTargetsDto(
    val everydayBudgetCents: Long = 0,
    val monthSpentCents: Long = 0,
    val leftCents: Long = 0,
    val perWeekLeftCents: Long = 0,
    val flexibleScale: Double = 1.0,
    val rows: List<CategoryTargetDto> = emptyList(),
)

@Serializable
data class BudgetPlanDto(
    val incomeCents: Long = 0,
    val incomeSource: String = "plan",
    val bar: IncomeBarDto = IncomeBarDto(),
    val cardBudget: CardBudgetDto = CardBudgetDto(),
    val categoryTargets: CategoryTargetsDto = CategoryTargetsDto(),
)

@Serializable
data class LimitMonthDto(val spentCents: Long = 0, val leftCents: Long = 0, val perWeekLeftCents: Long = 0, val projectedCents: Long? = null, val daysLeft: Int = 0)

@Serializable
data class LimitDto(
    val incomeCents: Long = 0,
    /** plan | bank */
    val incomeSource: String = "plan",
    val fixedBillsCents: Long = 0,
    val payoffCents: Long = 0,
    val limitCents: Long = 0,
    val averageCents: Long = 0,
    val overCents: Long = 0,
    val averageOnCardsCents: Long = 0,
    val cardPaymentNames: List<String> = emptyList(),
    val thisMonth: LimitMonthDto = LimitMonthDto(),
)

@Serializable
data class UpcomingDto(val merchant: String = "", val expectedOn: String = "", val cents: Long = 0, val years: Int = 1)

@Serializable
data class PaceDto(
    val day: Int = 0,
    val daysInMonth: Int = 30,
    val soFarCents: Long = 0,
    val usualByNowCents: Long = 0,
    val usualMonthCents: Long = 0,
    val projectedCents: Long = 0,
    val restOnCardsCents: Long = 0,
)

@Serializable
data class SuggestionItemDto(val name: String = "", val cents: Long = 0, val fromCents: Long? = null)

@Serializable
data class SuggestionDto(
    /** rising | trim | repeating | new-repeat | price-up | interest | fees | payoff */
    val kind: String = "",
    val title: String = "",
    val detail: String = "",
    val monthlyCents: Long = 0,
    val items: List<SuggestionItemDto> = emptyList(),
    val categoryId: String? = null,
    /** What was actually spent each complete month, oldest first. */
    val months: List<MonthAmountDto> = emptyList(),
)
