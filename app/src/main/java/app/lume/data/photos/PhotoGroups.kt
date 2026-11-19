package app.lume.data.photos

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Um dia da grade, com o rótulo que aparece no cabeçalho. */
data class DayGroup<T>(
    val label: String,
    val items: List<T>
)

/**
 * Agrupa itens por dia (a ordem de entrada é preservada — a
 * consulta da MediaStore já vem da mais recente para a mais
 * antiga). Rótulos: "Hoje, 19 de novembro", "Ontem, 18 de
 * novembro" e "17 de novembro de 2026" para os demais dias.
 *
 * Genérica e pura (sem Context nem Uri) para poder ser testada
 * na JVM: o chamador passa como extrair a data de cada item.
 * Sem java.time (minSdk 24): Calendar + SimpleDateFormat.
 */
fun <T> groupByDay(
    items: List<T>,
    dateAddedSeconds: (T) -> Long,
    nowMillis: Long = System.currentTimeMillis()
): List<DayGroup<T>> {
    if (items.isEmpty()) return emptyList()
    val locale = Locale("pt", "BR")
    val todayStart = startOfDay(nowMillis)
    val yesterdayStart = startOfDay(todayStart - 24 * 60 * 60 * 1000L)
    val dayFormat = SimpleDateFormat("d 'de' MMMM", locale)
    val fullFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", locale)
    val byDay = LinkedHashMap<Long, MutableList<T>>()
    for (item in items) {
        val key = startOfDay(dateAddedSeconds(item) * 1000L)
        byDay.getOrPut(key) { ArrayList() }.add(item)
    }
    return byDay.entries.map { (key, list) ->
        DayGroup(labelFor(key, todayStart, yesterdayStart, dayFormat, fullFormat), list)
    }
}

private fun startOfDay(millis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun labelFor(
    dayStart: Long,
    todayStart: Long,
    yesterdayStart: Long,
    dayFormat: SimpleDateFormat,
    fullFormat: SimpleDateFormat
): String {
    return when (dayStart) {
        todayStart -> "Hoje, ${dayFormat.format(Date(dayStart))}"
        yesterdayStart -> "Ontem, ${dayFormat.format(Date(dayStart))}"
        else -> fullFormat.format(Date(dayStart))
    }
}
