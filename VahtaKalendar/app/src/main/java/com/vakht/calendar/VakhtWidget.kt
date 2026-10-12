package com.vakht.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class VakhtWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        scheduleTick(context)
    }

    override fun onEnabled(context: Context) {
        scheduleTick(context)
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
        private const val INTERVAL = 10 * 60 * 1000L

        private fun tickIntent(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 1,
                Intent(ctx, VakhtWidget::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun scheduleTick(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + INTERVAL,
                INTERVAL,
                tickIntent(ctx)
            )
        }

        fun refreshAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, VakhtWidget::class.java))
            ids.forEach { update(ctx, manager, it) }
        }

        private fun fmtMinutes(ctx: Context, total: Int): String {
            val h = total / 60
            val m = total % 60
            return if (h > 0) ctx.getString(R.string.w_hm, h, m) else ctx.getString(R.string.w_min, m)
        }

        private fun update(ctx: Context, manager: AppWidgetManager, id: Int) {
            val res = ctx.resources
            val v = RemoteViews(ctx.packageName, R.layout.widget)
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

            if (start == null || work <= 0 || rest <= 0) {
                v.setInt(R.id.root, "setBackgroundResource", R.drawable.bg_glass_idle)
                v.setImageViewResource(R.id.hdr_icon, R.drawable.ic_briefcase)
                v.setImageViewBitmap(R.id.art, Art.cloud())
                v.setTextViewText(R.id.title, ctx.getString(R.string.w_not_set_title))
                v.setTextViewText(R.id.subtitle, ctx.getString(R.string.w_not_set_sub))
                v.setViewVisibility(R.id.row, View.GONE)
                v.setViewVisibility(R.id.divider, View.GONE)
                v.setViewVisibility(R.id.bottom, View.GONE)
            } else {
                val now = LocalDateTime.now()
                val today = now.toLocalDate()
                val cycle = work + rest
                val idx = ChronoUnit.DAYS.between(start, today)
                val pos = ((idx % cycle) + cycle) % cycle
                val working = pos < work
                val minutes = now.hour * 60 + now.minute
                val dayFrac = minutes / 1440.0
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
                var value = ""

                if (working) {
                    v.setInt(R.id.root, "setBackgroundResource", R.drawable.bg_glass_work)
                    v.setImageViewResource(R.id.hdr_icon, R.drawable.ic_briefcase)
                    v.setImageViewResource(R.id.ring_icon, R.drawable.ic_palm)
                    v.setImageViewBitmap(R.id.art, Art.briefcase())
                    v.setTextViewText(R.id.title, ctx.getString(R.string.w_state_work))
                    v.setTextViewText(R.id.subtitle, ctx.getString(R.string.w_until_rest))

                    if (hasHours) {
                        val inside = if (ws < we) minutes in ws until we else (minutes >= ws || minutes < we)
                        if (inside) {
                            val rem = if (ws < we) we - minutes else if (minutes >= ws) 1440 - minutes + we else we - minutes
                            label = ctx.getString(R.string.w_left_work)
                            value = fmtMinutes(ctx, rem)
                        } else if (left == 1L && ws < we && minutes >= we) {
                            label = ctx.getString(R.string.w_day_over)
                        } else {
                            val untilStart = if (minutes < ws) ws - minutes else 1440 - minutes + ws
                            label = ctx.getString(R.string.w_until_shift)
                            value = fmtMinutes(ctx, untilStart)
                        }
                    } else {
                        label = ctx.getString(R.string.w_rest_from)
                        value = date
                    }
                } else {
                    v.setInt(R.id.root, "setBackgroundResource", R.drawable.bg_glass_rest)
                    v.setImageViewResource(R.id.hdr_icon, R.drawable.ic_palm)
                    v.setImageViewResource(R.id.ring_icon, R.drawable.ic_briefcase)
                    v.setImageViewBitmap(R.id.art, Art.cloud())
                    v.setTextViewText(R.id.title, ctx.getString(R.string.w_state_rest))
                    v.setTextViewText(R.id.subtitle, ctx.getString(R.string.w_until_work))
                    label = ctx.getString(R.string.w_work_from)
                    value = date
                }

                v.setViewVisibility(R.id.row, View.VISIBLE)
                v.setViewVisibility(R.id.divider, View.VISIBLE)
                v.setViewVisibility(R.id.bottom, View.VISIBLE)
                v.setImageViewBitmap(R.id.ring, Art.ring(progress))
                v.setTextViewText(R.id.num, left.toString())
                v.setTextViewText(R.id.unit, res.getQuantityString(R.plurals.days_unit, left.toInt()))
                v.setTextViewText(R.id.blabel, label)
                v.setTextViewText(R.id.bvalue, value)
            }

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.root, open)
            manager.updateAppWidget(id, v)
        }
    }
}
