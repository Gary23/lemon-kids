"use client"

import { useMemo, useState } from "react"
import { ChevronLeft, ChevronRight, Star, Check, Clock } from "lucide-react"
import { useStore, TODAY, type Task } from "./store"

const WEEK = ["日", "一", "二", "三", "四", "五", "六"]

function ymd(y: number, m: number, d: number) {
  return `${y}-${String(m + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`
}

export function CalendarPage() {
  const { tasks } = useStore()
  const [year, setYear] = useState(2026)
  const [month, setMonth] = useState(6) // 0-indexed => 7月
  const [selected, setSelected] = useState<string>(TODAY)

  const tasksByDate = useMemo(() => {
    const map: Record<string, Task[]> = {}
    tasks.forEach((t) => {
      map[t.date] = map[t.date] ? [...map[t.date], t] : [t]
    })
    return map
  }, [tasks])

  const firstWeekday = new Date(year, month, 1).getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()

  const cells: (number | null)[] = []
  for (let i = 0; i < firstWeekday; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) cells.push(d)
  while (cells.length % 7 !== 0) cells.push(null)

  function shift(delta: number) {
    let m = month + delta
    let y = year
    if (m < 0) {
      m = 11
      y -= 1
    } else if (m > 11) {
      m = 0
      y += 1
    }
    setMonth(m)
    setYear(y)
  }

  function dayStatus(dateStr: string): "done" | "pending" | "none" {
    const list = tasksByDate[dateStr]
    if (!list || list.length === 0) return "none"
    const hasUnfinished = list.some((t) => t.status !== "done")
    return hasUnfinished ? "pending" : "done"
  }

  const selectedTasks = tasksByDate[selected] ?? []

  return (
    <div className="min-h-full pb-4">
      <header className="rounded-b-[2.5rem] bg-gradient-to-br from-lavender-soft to-lavender px-6 pb-8 pt-10 shadow-[0_12px_30px_-12px_rgba(195,174,214,0.7)]">
        <p className="font-display text-2xl font-extrabold text-primary-foreground drop-shadow-sm">我的任务日历 📅</p>
        <div className="mt-4 flex items-center justify-between">
          <button
            type="button"
            onClick={() => shift(-1)}
            aria-label="上个月"
            className="flex size-11 items-center justify-center rounded-full bg-card/90 text-lavender active:scale-90"
          >
            <ChevronLeft className="size-6" strokeWidth={3} />
          </button>
          <p className="font-display text-2xl font-extrabold text-primary-foreground">
            {year}年{month + 1}月
          </p>
          <button
            type="button"
            onClick={() => shift(1)}
            aria-label="下个月"
            className="flex size-11 items-center justify-center rounded-full bg-card/90 text-lavender active:scale-90"
          >
            <ChevronRight className="size-6" strokeWidth={3} />
          </button>
        </div>
      </header>

      <div className="px-4 pt-5">
        <div className="rounded-3xl bg-card p-4 shadow-[0_8px_24px_-14px_rgba(0,0,0,0.15)]">
          {/* 星期头 */}
          <div className="mb-2 grid grid-cols-7 gap-1">
            {WEEK.map((w) => (
              <div
                key={w}
                className="rounded-xl bg-lavender-soft/50 py-2 text-center font-display text-base font-extrabold text-lavender"
              >
                {w}
              </div>
            ))}
          </div>
          {/* 日期格子 */}
          <div className="grid grid-cols-7 gap-1">
            {cells.map((d, i) => {
              if (d === null) return <div key={`e${i}`} />
              const dateStr = ymd(year, month, d)
              const status = dayStatus(dateStr)
              const isToday = dateStr === TODAY
              const isSelected = dateStr === selected
              const hasTask = status !== "none"
              return (
                <button
                  key={dateStr}
                  type="button"
                  onClick={() => setSelected(dateStr)}
                  className={`flex aspect-square flex-col items-center justify-center gap-1 rounded-2xl transition-colors ${
                    hasTask ? "bg-sunny/25" : ""
                  } ${isSelected ? "ring-2 ring-pink" : ""}`}
                >
                  <span
                    className={`flex size-8 items-center justify-center rounded-full font-display text-base font-extrabold ${
                      isToday ? "bg-pink text-primary-foreground shadow-sm" : "text-foreground"
                    }`}
                  >
                    {d}
                  </span>
                  <span
                    className={`size-2 rounded-full ${
                      status === "done"
                        ? "bg-mint"
                        : status === "pending"
                          ? "bg-coral"
                          : "bg-transparent"
                    }`}
                  />
                </button>
              )
            })}
          </div>

          {/* 图例 */}
          <div className="mt-3 flex items-center justify-center gap-4 text-xs font-bold text-muted-foreground">
            <span className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-full bg-mint" />全部完成
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-full bg-coral" />有未完成
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-full bg-border" />无任务
            </span>
          </div>
        </div>

        {/* 选中日期任务列表 */}
        <div key={selected} className="animate-float-up mt-4 rounded-3xl bg-card p-5 shadow-[0_8px_24px_-14px_rgba(0,0,0,0.15)]">
          <p className="font-display text-xl font-extrabold text-foreground">
            {selected === TODAY ? "今天" : selected.replace(/^\d+-/, "").replace("-", "月") + "日"}的安排
          </p>
          <div className="mt-3 space-y-2.5">
            {selectedTasks.length === 0 && (
              <div className="rounded-2xl bg-muted py-8 text-center">
                <p className="text-3xl">🌈</p>
                <p className="mt-1 text-base font-bold text-muted-foreground">这一天没有任务，好好玩耍吧！</p>
              </div>
            )}
            {selectedTasks.map((t) => (
              <div key={t.id} className="flex items-center gap-3 rounded-2xl bg-background/60 p-3.5 ring-1 ring-border">
                <div
                  className={`flex size-10 shrink-0 items-center justify-center rounded-full ${
                    t.status === "done"
                      ? "bg-mint text-primary-foreground"
                      : t.status === "missed"
                        ? "bg-muted-foreground/15 text-muted-foreground"
                        : "bg-pink-soft/50 text-pink"
                  }`}
                >
                  {t.status === "done" ? (
                    <Check className="size-5" strokeWidth={3} />
                  ) : t.status === "missed" ? (
                    <Clock className="size-5" strokeWidth={2.5} />
                  ) : (
                    <Star className="size-5 fill-sunny text-sunny" strokeWidth={2} />
                  )}
                </div>
                <p className="min-w-0 flex-1 truncate font-display text-lg font-bold text-foreground">{t.title}</p>
                <span className="flex shrink-0 items-center gap-1 text-sm font-bold text-primary">
                  <Star className="size-4 fill-sunny text-sunny" strokeWidth={2} />
                  {t.points}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
