package com.budget.app

import com.budget.app.data.EnrollLinks
import com.budget.app.data.LocalNetwork
import com.budget.app.data.Money
import com.budget.app.data.ServerUrl
import com.budget.app.data.remote.HalfInfo
import com.budget.app.data.remote.HalfSummary
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView
import com.budget.app.data.remote.YearMonthRef
import com.budget.app.data.sync.Overlay
import com.budget.app.data.sync.PendingWrite
import com.budget.app.data.sync.SendOutcome
import com.budget.app.data.sync.SyncRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun formats() {
        assertEquals("$1,234.56", Money.format(123456))
        assertEquals("$0.05", Money.format(5))
        assertEquals("-$5.00", Money.format(-500))
    }

    @Test fun parses() {
        assertEquals(17240L, Money.parse("172.40"))
        assertEquals(17240L, Money.parse("$172.4"))
        assertEquals(120000L, Money.parse("1,200"))
        assertEquals(50L, Money.parse(".5"))
        assertEquals(0L, Money.parse("0"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }

    @Test fun decimal() {
        assertEquals("172.40", Money.toDecimal(17240))
        assertEquals("0.05", Money.toDecimal(5))
    }
}

class ServerUrlTest {
    @Test fun normalises() {
        assertEquals("https://budget.example.com", ServerUrl.normalize("budget.example.com"))
        assertEquals("https://budget.example.com", ServerUrl.normalize(" https://Budget.Example.com/month/2026 "))
        assertEquals("http://192.168.1.20:3000", ServerUrl.normalize("http://192.168.1.20:3000/"))
    }

    @Test fun refuses() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("ftp://example.com"))
        assertNull(ServerUrl.normalize("javascript:alert(1)"))
        assertNull(ServerUrl.normalize("https://user:pw@example.com"))
        assertNull(ServerUrl.normalize("exa mple.com"))
    }
}

class EnrollLinkTest {
    @Test fun readsTheQrLink() {
        val link = EnrollLinks.parse("budget://enroll?server=https%3A%2F%2Fbudget.example.com&code=abcd-efgh")
        assertEquals("https://budget.example.com", link?.server)
        assertEquals("ABCD-EFGH", link?.code)
    }

    @Test fun refusesAnythingElse() {
        assertNull(EnrollLinks.parse("https://budget.example.com"))
        assertNull(EnrollLinks.parse("budget://enroll?server=ftp%3A%2F%2Fx&code=ABCD-EFGH"))
        assertNull(EnrollLinks.parse("budget://enroll?server=https%3A%2F%2Fx.com&code=../../"))
        assertNull(EnrollLinks.parse("budget://other?server=https%3A%2F%2Fx.com&code=ABCD-EFGH"))
        assertNull(EnrollLinks.parse(null))
    }
}

class LocalNetworkTest {
    @Test fun spotsHomeAddresses() {
        assertTrue(LocalNetwork.isLikelyLocal("http://192.168.1.20:3000"))
        assertTrue(LocalNetwork.isLikelyLocal("http://10.0.0.5"))
        assertTrue(LocalNetwork.isLikelyLocal("http://server.lan"))
        assertFalse(LocalNetwork.isLikelyLocal("https://budget.example.com"))
        assertFalse(LocalNetwork.isLikelyLocal("http://100.64.1.1"))
    }
}

class OverlayTest {
    private fun entry(id: String, budget: Long, actual: Long? = null, year: Int = 2026, month: Int = 11, carried: Boolean = false) =
        MonthEntry(
            lineItemId = id, name = id, year = year, month = month, kind = "BILL",
            budgetedCents = budget, actualCents = actual,
            carriedFrom = if (carried) YearMonthRef(year, month) else null,
        )

    private val view = MonthView(
        year = 2026, month = 11,
        halves = listOf(HalfSummary(HalfInfo(0, "First half"), listOf(entry("a", 1000), entry("b", 2000, 2000)), 3000, 2000, 1000)),
        carried = listOf(entry("c", 500, year = 2026, month = 10, carried = true)),
        billsBalanceCents = 10000,
    )

    private fun write(type: String, id: String?, amount: Long? = null, month: Int = 11) =
        PendingWrite(id = "w-$id-$type", type = type, year = 2026, month = month, lineItemId = id, amountCents = amount, enteredAt = "2026-11-13T00:00:00Z", label = "x")

    @Test fun showsQueuedChanges() {
        val result = Overlay.apply(
            view,
            listOf(
                write(PendingWrite.RECORD, "a", 900),
                write(PendingWrite.CLEAR, "b"),
                write(PendingWrite.RECORD, "c", 500, month = 10),
                write(PendingWrite.BALANCE, null, 7700),
            ),
        )
        val half = result.halves[0]
        assertEquals(900L, half.entries[0].actualCents)
        assertNull(half.entries[1].actualCents)
        assertEquals(900L, half.paidCents)
        assertEquals(2000L, half.remainingCents)
        assertEquals(500L, result.carried[0].actualCents)
        assertEquals(7700L, result.billsBalanceCents)
    }

    @Test fun laterChangesWin() {
        val result = Overlay.apply(view, listOf(write(PendingWrite.RECORD, "a", 900), write(PendingWrite.CLEAR, "a")))
        assertNull(result.halves[0].entries[0].actualCents)
    }

    @Test fun ignoresOtherMonths() {
        val result = Overlay.apply(view, listOf(write(PendingWrite.RECORD, "a", 900, month = 12)))
        assertNull(result.halves[0].entries[0].actualCents)
    }
}

class SyncRulesTest {
    @Test fun classifies() {
        assertEquals(SendOutcome.DONE, SyncRules.classify(200))
        assertEquals(SendOutcome.REFUSED, SyncRules.classify(409))
        assertEquals(SendOutcome.REFUSED, SyncRules.classify(422))
        assertEquals(SendOutcome.SIGNED_OUT, SyncRules.classify(401))
        assertEquals(SendOutcome.RETRY_LATER, SyncRules.classify(429))
        assertEquals(SendOutcome.RETRY_LATER, SyncRules.classify(503))
        assertEquals(SendOutcome.RETRY_LATER, SyncRules.classify(null))
    }
}
