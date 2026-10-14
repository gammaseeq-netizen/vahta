package com.vakht.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class VakhtWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        refreshAll(context)
    }

    override fun onEnabled(context: Context) {
        refreshAll(context)
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_TICK = "com.vakht.calendar.TICK"

        // Пороги размера виджета (dp)
        private const val FULL_MIN_HEIGHT = 145   // полный макет
        private const val ROOMY_MIN_HEIGHT = 100  // компактный, но со всеми строками
        private const val ART_MIN_WIDTH = 270     // ниже этой ширины картинка скрывается

        private fun tickIntent(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 1,
                Intent(ctx, VakhtWidget::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** Контекст с языком из настроек приложения (или системным, если там русский/украинский). */
        private fun localized(ctx: Context): Context {
            val prefs = ctx.getSharedPreferences("vakht", Context.MODE_PRIVATE)
            val chosen = prefs.getString("lang", "auto") ?: "auto"
            val sys = ctx.resources.configuration.locales[0].language
            val use = when {
                chosen == "ru" || chosen == "uk" -> chosen
                sys == "uk" -> "uk"
                else -> "ru"
            }
            if (use == sys) return ctx
            val cfg = Configuration(ctx.resources.configuration)
            cfg.setLocale(Locale(use))
            return ctx.createConfigurationContext(cfg)
        }

        fun refreshAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, VakhtWidget::class.java))
            if (ids.isEmpty()) return
            var boundary = Long.MAX_VALUE
            for (id in ids) {
                val b = update(ctx, manager, id)
                if (b < boundary) boundary = b
            }
            scheduleNext(ctx, boundary)
        }

        /** Следующий пересчёт: через выбранный интервал или раньше, если вот-вот сменится состояние. */
        private fun scheduleNext(ctx: Context, boundary: Long) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val minutes = ctx.getSharedPreferences("vakht", Context.MODE_PRIVATE)
                .getInt("interval", 10).coerceIn(1, 180)
            val now = System.currentTimeMillis()
            var at = now + minutes * 60_000L
            if (boundary != Long.MAX_VALUE && boundary + 1000L < at) at = boundary + 1000L
            if (at < now + 5000L) at = now + 5000L
            val pi = tickIntent(ctx)
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
            }
        }

        /** Обновляет один виджет. Возвращает момент (мс), когда состояние сменится, либо Long.MAX_VALUE. */
        private fun update(ctx: Context, manager: AppWidgetManager, id: Int): Long {
            val lctx = localized(ctx)
            val res = lctx.resources
            val density = ctx.resources.displayMetrics.density
            fun px(dp: Int): Int = (dp * density + 0.5f).toInt()

            val opts = manager.getAppWidgetOptions(id)
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).let { if (it <= 0) 300 else it }
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).let { if (it <= 0) 150 else it }
            val full = hDp >= FULL_MIN_HEIGHT
            val showArt = wDp >= ART_MIN_WIDTH
            val showTexts = full || hDp >= ROOMY_MIN_HEIGHT   // заголовок и нижняя строка

            val v = RemoteViews(ctx.packageName, if (full) R.layout.widget else R.layout.widget_compact)
            v.setViewVisibility(R.id.art, if (showArt) View.VISIBLE else View.GONE)
            if (full) {
                v.setViewPadding(R.id.content, px(16), px(8), if (showArt) px(92) else px(16), px(8))
            } else {
                v.setViewPadding(R.id.content, px(12), px(6), if (showArt) px(72) else px(12), px(6))
            }

            val prefs = ctx.getSharedPreferences("vakht", Context.MODE_PRIVATE)
            val s = prefs.getString("start", "") ?: ""
            val work = prefs.getInt("work", 0).toLong()
            val rest = prefs.getInt("rest", 0).toLong()
            val ws = prefs.getInt("ws", -1)
            val we = prefs.getInt("we", -1)
            val start = try {
                if (s.isEmpty()) null else LocalDate.parse(s)
            } catch (e: Exception) {
                null
            }

            val nowDt = LocalDateTime.now()
            val zone = ZoneId.systemDefault()
            fun ms(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()
            val nowMs = System.currentTimeMillis()
            // как минимум в полночь нужно пересчитать дни
            var boundary = ms(nowDt.toLocalDate().plusDays(1).atStartOfDay())

            if (start == null || work <= 0 || rest <= 0) {
                v.setInt(R.id.root, "setBackgroundResource", R.drawable.bg_glass_idle)
                v.setImageViewResource(R.id.hdr_icon, R.drawable.ic_briefcase)
                v.setImageViewBitmap(R.id.art, Art.cloud())
                v.setViewVisibility(R.id.title, if (showTexts) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.title, lctx.getString(R.string.w_not_set_title))
                v.setTextViewText(R.id.subtitle, lctx.getString(R.string.w_not_set_sub))
                v.setViewVisibility(R.id.ringbox, View.GONE)
                v.setViewVisibility(R.id.numrow, View.GONE)
                v.setViewVisibility(R.id.divider, View.GONE)
                v.setViewVisibility(R.id.bottom, View.GONE)
            } else {
                val today = nowDt.toLocalDate()
                val cycle = work + rest
                val idx = ChronoUnit.DAYS.between(start, today)
                val pos = ((idx % cycle) + cycle) % cycle
                val working = pos < work
                val minutes = nowDt.hour * 60 + nowDt.minute
                val dayFrac = (minutes + nowDt.second / 60.0) / 1440.0
                val left = if (working) work - pos else cycle - pos

                // На вахте кольцо уменьшается, на выходных заполняется
                val progress = if (working) {
                    (work - pos - dayFrac) / work
                } else {
                    ((pos - work) + dayFrac) / rest
                }.coerceIn(0.0, 1.0).toFloat()

                val date = today.plusDays(left).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                val hasHours = ws >= 0 && we >= 0 && ws != we

                var label = ""
                var valueText = ""
                var countdownMs = -1L

                if (working) {
                    if (hasHours) {
                        val overnight = we < ws
                        val dayStart = today.atStartOfDay()
                        val startToday = dayStart.plusMinutes(ws.toLong())
                        val endSameDay = dayStart.plusMinutes(we.toLong())
                        val endToday = if (overnight) endSameDay.plusDays(1) else endSameDay
                        val yesterdayWork = pos >= 1

                        if (overnight && yesterdayWork && nowDt.isBefore(endSameDay)) {
                            // ночная смена, начатая вчера, ещё идёт
                            label = lctx.getString(R.string.w_left_work)
                            countdownMs = ms(endSameDay) - nowMs
                        } else if (!nowDt.isBefore(startToday) && nowDt.isBefore(endToday)) {
                            label = lctx.getString(R.string.w_left_work)
                            countdownMs = ms(endToday) - nowMs
                        } else if (nowDt.isBefore(startToday)) {
                            label = lctx.getString(R.string.w_until_shift)
                            countdownMs = ms(startToday) - nowMs
                        } else if (pos + 1 < work) {
                            // смена сегодня закончилась, завтра ещё рабочий день
                            label = lctx.getString(R.string.w_until_shift)
                            countdownMs = ms(startToday.plusDays(1)) - nowMs
                        } else {
                            label = lctx.getString(R.string.w_day_over)
                        }
                    } else {
                        label = lctx.getString(R.string.w_rest_from)
                        valueText = date
                    }
                    v.setInt(R.id.root, "setBackgroundResource", R.drawable.bg_glass_work)
                    v.setImageViewResource(R.id.hdr_icon, R.drawable.ic_briefcase)
                    v.setImageViewResource(R.id.ring_icon, R.drawable.ic_home)
                    v.setImageViewBitmap(R.id.art, Art.briefcase())
                    v.setTextViewText(R.id.title, lctx.getString(R.string.w_state_work))
                    v.setTextViewText(R.id.subtitle, lctx.getString(R.string.w_until_rest))
                } else {
                    label = lctx.getString(R.string.w_work_from)
                    valueText = date
                    v.setInt(R.id.root, "setBackgroundResource", R.drawable.bg_glass_rest)
                    v.setImageViewResource(R.id.hdr_icon, R.drawable.ic_home)
                    v.setImageViewResource(R.id.ring_icon, R.drawable.ic_briefcase)
                    v.setImageViewBitmap(R.id.art, Art.cloud())
                    v.setTextViewText(R.id.title, lctx.getString(R.string.w_state_rest))
                    v.setTextViewText(R.id.subtitle, lctx.getString(R.string.w_until_work))
                }

                v.setViewVisibility(R.id.title, if (showTexts) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.ringbox, View.VISIBLE)
                v.setViewVisibility(R.id.numrow, View.VISIBLE)
                v.setViewVisibility(R.id.divider, View.VISIBLE)
                v.setViewVisibility(R.id.bottom, if (showTexts) View.VISIBLE else View.GONE)
                v.setImageViewBitmap(R.id.ring, Art.ring(progress))
                v.setTextViewText(R.id.num, left.toString())
                v.setTextViewText(R.id.unit, res.getQuantityString(R.plurals.days_unit, left.toInt()))
                v.setTextViewText(R.id.blabel, label)

                if (countdownMs >= 0L) {
                    // Обратный отсчёт идёт по часам телефона сам, без пересчётов виджета
                    v.setViewVisibility(R.id.bvalue, View.GONE)
                    v.setViewVisibility(R.id.bchrono, View.VISIBLE)
                    v.setChronometerCountDown(R.id.bchrono, true)
                    v.setChronometer(R.id.bchrono, SystemClock.elapsedRealtime() + countdownMs, "%s", true)
                    val at = nowMs + countdownMs
                    if (at < boundary) boundary = at
                } else {
                    v.setViewVisibility(R.id.bchrono, View.GONE)
                    v.setViewVisibility(R.id.bvalue, View.VISIBLE)
                    v.setTextViewText(R.id.bvalue, valueText)
                }
            }

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.root, open)
            manager.updateAppWidget(id, v)
            return boundary
        }
    }
}
