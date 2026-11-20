package app.lume.data.photos

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do agrupamento da grade por dia (Home v5). Usa só
 * Longs como itens — a função é genérica e pura, sem Uri.
 */
class PhotoGroupsTest {

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, hour, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // "Agora" fixo: 19 de novembro de 2026, meio-dia.
    private val now = millis(2026, Calendar.NOVEMBER, 19, 12)

    @Test
    fun listaVaziaNaoTemGrupos() {
        assertTrue(groupByDay(emptyList<Long>()) { it }.isEmpty())
    }

    @Test
    fun fotoDeHojeRotuladaHoje() {
        // "Agora" fixo como nos outros testes: sem nowMillis, o
        // teste comparava a foto fixa de 19/11 com o dia real e
        // quebrava no dia seguinte (20/11: "Ontem", run #45).
        val groups = groupByDay(listOf(now / 1000), nowMillis = now) { it }
        assertEquals(1, groups.size)
        assertEquals("Hoje, 19 de novembro", groups[0].label)
        assertEquals(1, groups[0].items.size)
    }

    @Test
    fun ontemRotuladoOntemEOrdemDecrescente() {
        val today = millis(2026, Calendar.NOVEMBER, 19, 9) / 1000
        val yesterday = millis(2026, Calendar.NOVEMBER, 18, 23) / 1000
        val older = millis(2026, Calendar.NOVEMBER, 17, 8) / 1000
        val groups = groupByDay(listOf(today, yesterday, older), nowMillis = now) { it }
        assertEquals(3, groups.size)
        assertEquals("Hoje, 19 de novembro", groups[0].label)
        assertEquals("Ontem, 18 de novembro", groups[1].label)
        assertEquals("17 de novembro de 2026", groups[2].label)
    }

    @Test
    fun mesmoDiaViraUmGrupoSo() {
        val morning = millis(2026, Calendar.NOVEMBER, 19, 8) / 1000
        val evening = millis(2026, Calendar.NOVEMBER, 19, 21) / 1000
        val groups = groupByDay(listOf(morning, evening), nowMillis = now) { it }
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].items.size)
    }

    @Test
    fun viradaDaMeiaNoiteSeparaOsDias() {
        val lateNight = millis(2026, Calendar.NOVEMBER, 18, 23) / 1000
        val earlyMorning = millis(2026, Calendar.NOVEMBER, 19, 0) / 1000
        val groups = groupByDay(listOf(earlyMorning, lateNight), nowMillis = now) { it }
        assertEquals(2, groups.size)
        assertEquals("Hoje, 19 de novembro", groups[0].label)
        assertEquals("Ontem, 18 de novembro", groups[1].label)
    }

    @Test
    fun anoPassadoMostraOAno() {
        val lastYear = millis(2025, Calendar.DECEMBER, 25, 10) / 1000
        val groups = groupByDay(listOf(lastYear), nowMillis = now) { it }
        assertEquals(1, groups.size)
        assertEquals("25 de dezembro de 2025", groups[0].label)
    }
}
