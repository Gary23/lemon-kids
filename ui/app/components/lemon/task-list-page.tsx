"use client"

import { useMemo, useState } from "react"
import { ChevronDown, Clock, Flame, Star, Volume2, Check, RotateCcw, PartyPopper, X } from "lucide-react"
import { useStore, type Task, type TaskGroup } from "./store"

/* 分组主题配置 */
const GROUPS: {
  key: TaskGroup
  title: string
  countLabel: (n: number) => string
  bar: string
  soft: string
  text: string
  btn: string
  chip: string
  emoji: string
}[] = [
  {
    key: "today",
    title: "今日任务",
    countLabel: (n) => `还有 ${n} 个`,
    bar: "bg-pink",
    soft: "bg-pink-soft/40",
    text: "text-pink",
    btn: "bg-pink text-primary-foreground",
    chip: "bg-pink-soft/50 text-pink",
    emoji: "🌸",
  },
  {
    key: "missed",
    title: "忘记做的任务",
    countLabel: (n) => `过期 ${n} 个`,
    bar: "bg-coral",
    soft: "bg-coral-soft/40",
    text: "text-coral",
    btn: "bg-coral text-primary-foreground",
    chip: "bg-coral-soft/50 text-coral",
    emoji: "🍊",
  },
  {
    key: "later",
    title: "之后的小任务",
    countLabel: (n) => `未来 ${n} 个`,
    bar: "bg-lavender",
    soft: "bg-lavender-soft/40",
    text: "text-lavender",
    btn: "bg-lavender text-primary-foreground",
    chip: "bg-lavender-soft/60 text-lavender",
    emoji: "💜",
  },
]

function greeting() {
  const h = new Date().getHours()
  if (h < 11) return "早上好！"
  if (h < 18) return "下午好！"
  return "晚上好！"
}

export function TaskListPage() {
  const { nickname, points, streak, tasks, completeTask, undoTask } = useStore()
  const [open, setOpen] = useState<Record<TaskGroup, boolean>>({ today: true, missed: false, later: false })
  const [confirmId, setConfirmId] = useState<string | null>(null)

  const grouped = useMemo(() => {
    const g: Record<TaskGroup, Task[]> = { today: [], missed: [], later: [] }
    tasks.forEach((t) => g[t.group].push(t))
    return g
  }, [tasks])

  const todayPending = grouped.today.filter((t) => t.status === "pending").length
  const allTodayDone = grouped.today.length > 0 && todayPending === 0

  function speak(text: string) {
    if (typeof window !== "undefined" && "speechSynthesis" in window) {
      const u = new SpeechSynthesisUtterance(text)
      u.lang = "zh-CN"
      u.rate = 0.9
      window.speechSynthesis.cancel()
      window.speechSynthesis.speak(u)
    }
  }

  const confirmTask = tasks.find((t) => t.id === confirmId)

  return (
    <div className="min-h-full pb-4">
      {/* Header */}
      <header className="relative overflow-hidden rounded-b-[2.5rem] bg-gradient-to-br from-pink-soft to-primary px-6 pb-9 pt-10 shadow-[0_12px_30px_-12px_rgba(255,133,162,0.6)]">
        <div className="pointer-events-none absolute -right-6 -top-6 text-7xl opacity-30">🌸</div>
        <div className="pointer-events-none absolute right-16 top-16 text-4xl opacity-30">☁️</div>
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="font-display text-3xl font-extrabold text-primary-foreground drop-shadow-sm">
              {greeting()}
            </p>
            <p className="mt-1 text-lg font-bold text-primary-foreground/90">{nickname}，今天也要加油鸭～</p>
          </div>
          <div className="flex shrink-0 gap-2">
            <div className="flex items-center gap-1.5 rounded-full bg-card/90 px-3 py-2 shadow-sm">
              <Flame className="size-5 text-coral" strokeWidth={2.5} />
              <span className="font-display text-lg font-extrabold text-coral">{streak}</span>
            </div>
            <div className="flex items-center gap-1.5 rounded-full bg-card/90 px-3 py-2 shadow-sm">
              <Star className="size-5 fill-sunny text-sunny" strokeWidth={2.5} />
              <span className="font-display text-lg font-extrabold text-primary">{points}</span>
            </div>
          </div>
        </div>
      </header>

      {/* 分组任务 */}
      <div className="space-y-4 px-4 pt-5">
        {allTodayDone && <CelebrationCard />}

        {GROUPS.map((cfg) => {
          const list = grouped[cfg.key]
          const pending = list.filter((t) => t.status === "pending").length
          const isOpen = open[cfg.key]
          return (
            <section key={cfg.key} className="overflow-hidden rounded-3xl bg-card shadow-[0_8px_24px_-14px_rgba(0,0,0,0.15)]">
              <button
                type="button"
                onClick={() => setOpen((o) => ({ ...o, [cfg.key]: !o[cfg.key] }))}
                className={`flex w-full items-center justify-between gap-3 px-5 py-4 text-left ${cfg.soft}`}
              >
                <span className="flex items-center gap-2.5">
                  <span className="text-2xl">{cfg.emoji}</span>
                  <span className="font-display text-xl font-extrabold text-foreground">{cfg.title}</span>
                  <span className={`rounded-full px-3 py-0.5 text-sm font-bold ${cfg.chip}`}>
                    {cfg.countLabel(cfg.key === "today" ? pending : list.length)}
                  </span>
                </span>
                <ChevronDown
                  className={`size-6 shrink-0 text-muted-foreground transition-transform duration-300 ${isOpen ? "rotate-180" : ""}`}
                  strokeWidth={2.5}
                />
              </button>

              {isOpen && (
                <div className="space-y-3 p-4">
                  {list.length === 0 && (
                    <p className="py-6 text-center text-base font-semibold text-muted-foreground">这里空空的呀～</p>
                  )}
                  {list.map((task) => (
                    <TaskCard
                      key={task.id}
                      task={task}
                      cfg={cfg}
                      onSpeak={() => speak(`${task.title}。${task.desc}`)}
                      onComplete={() => setConfirmId(task.id)}
                      onUndo={() => undoTask(task.id)}
                    />
                  ))}
                </div>
              )}
            </section>
          )
        })}
      </div>

      {/* 完成确认弹窗 */}
      {confirmTask && (
        <ConfirmDialog
          onCancel={() => setConfirmId(null)}
          onConfirm={() => {
            completeTask(confirmTask.id)
            setConfirmId(null)
          }}
        />
      )}
    </div>
  )
}

/* ---------- 任务卡片 ---------- */
function TaskCard({
  task,
  cfg,
  onSpeak,
  onComplete,
  onUndo,
}: {
  task: Task
  cfg: (typeof GROUPS)[number]
  onSpeak: () => void
  onComplete: () => void
  onUndo: () => void
}) {
  if (task.status === "done") {
    return (
      <div className="flex items-center gap-3 rounded-2xl bg-mint-soft/40 p-4 opacity-90">
        <div className="flex size-11 shrink-0 items-center justify-center rounded-full bg-mint text-primary-foreground">
          <Check className="size-6" strokeWidth={3} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-display text-lg font-bold text-foreground line-through decoration-mint decoration-2">
            {task.title}
          </p>
          <p className="text-sm font-semibold text-mint">✅ 已完成 · 得到 {task.points} 颗星星</p>
        </div>
        <button
          type="button"
          onClick={onUndo}
          className="shrink-0 rounded-full px-3 py-2 text-sm font-bold text-muted-foreground underline underline-offset-2"
        >
          撤销
        </button>
      </div>
    )
  }

  if (task.status === "missed") {
    return (
      <div className="relative flex items-center gap-3 overflow-hidden rounded-2xl bg-muted p-4">
        <span className="absolute left-0 top-0 h-full w-1 bg-muted-foreground/30" />
        <div className="flex size-11 shrink-0 items-center justify-center rounded-full bg-muted-foreground/15 text-muted-foreground">
          <Clock className="size-5" strokeWidth={2.5} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-display text-lg font-bold text-muted-foreground">{task.title}</p>
          <p className="text-sm font-semibold text-muted-foreground">⏰ 已错过 · 截止 {task.deadline}</p>
        </div>
        <button
          type="button"
          onClick={onComplete}
          className="shrink-0 rounded-full bg-coral/90 px-4 py-2.5 text-sm font-extrabold text-primary-foreground shadow-sm active:scale-95"
        >
          补做啦
        </button>
      </div>
    )
  }

  return (
    <div className="relative flex items-center gap-3 overflow-hidden rounded-2xl bg-background/60 p-4 ring-1 ring-border">
      <span className={`absolute left-0 top-0 h-full w-1 ${cfg.bar}`} />
      <button
        type="button"
        onClick={onSpeak}
        aria-label="朗读任务"
        className={`flex size-11 shrink-0 items-center justify-center rounded-full ${cfg.soft} ${cfg.text} active:scale-90`}
      >
        <Volume2 className="size-5" strokeWidth={2.5} />
      </button>
      <div className="min-w-0 flex-1">
        <p className="font-display text-lg font-extrabold text-foreground">{task.title}</p>
        <p className="truncate text-sm font-semibold text-muted-foreground">{task.desc}</p>
        <div className="mt-1.5 flex items-center gap-3 text-sm font-bold">
          <span className="flex items-center gap-1 text-primary">
            <Star className="size-4 fill-sunny text-sunny" strokeWidth={2.5} />
            {task.points} 积分
          </span>
          <span className="flex items-center gap-1 text-muted-foreground">
            <Clock className="size-4" strokeWidth={2.5} />
            {task.deadline}
          </span>
        </div>
      </div>
      <button
        type="button"
        onClick={onComplete}
        className={`shrink-0 rounded-full px-4 py-3 text-sm font-extrabold shadow-md active:scale-95 ${cfg.btn}`}
      >
        我做完啦！
      </button>
    </div>
  )
}

/* ---------- 全部完成庆祝卡片 ---------- */
function CelebrationCard() {
  return (
    <div className="animate-pop-in relative overflow-hidden rounded-3xl bg-gradient-to-br from-sunny to-pink-soft p-7 text-center shadow-lg">
      {["🌟", "⭐", "✨", "💫", "⭐", "🌟"].map((s, i) => (
        <span
          key={i}
          className="animate-star-fall pointer-events-none absolute text-2xl"
          style={{ left: `${10 + i * 15}%`, animationDelay: `${i * 0.2}s`, top: 0 }}
        >
          {s}
        </span>
      ))}
      <div className="animate-bounce-soft mx-auto flex size-20 items-center justify-center rounded-full bg-card shadow-md">
        <Star className="size-11 fill-sunny text-sunny" strokeWidth={2} />
      </div>
      <p className="mt-3 font-display text-2xl font-extrabold text-foreground text-balance">太棒了！所有任务都完成啦！</p>
      <p className="mt-1 text-base font-bold text-foreground/70">你今天超级厉害，奖励自己一颗大星星吧～</p>
    </div>
  )
}

/* ---------- 完成确认弹窗 ---------- */
function ConfirmDialog({ onCancel, onConfirm }: { onCancel: () => void; onConfirm: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/30 p-6 backdrop-blur-sm" onClick={onCancel}>
      <div
        className="animate-pop-in w-full max-w-sm rounded-[2rem] bg-card p-7 text-center shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          onClick={onCancel}
          aria-label="关闭"
          className="ml-auto flex size-9 items-center justify-center rounded-full bg-muted text-muted-foreground active:scale-90"
        >
          <X className="size-5" strokeWidth={2.5} />
        </button>
        <div className="animate-wiggle mx-auto -mt-2 flex size-20 items-center justify-center rounded-full bg-pink-soft/50">
          <PartyPopper className="size-10 text-pink" strokeWidth={2} />
        </div>
        <p className="mt-3 font-display text-2xl font-extrabold text-foreground">完成啦？</p>
        <p className="mt-1.5 text-base font-semibold text-muted-foreground text-pretty">
          做完了才能得到小星星哦～<br />要诚实的小朋友才最棒！
        </p>
        <div className="mt-6 flex gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-full bg-muted py-3.5 font-display text-lg font-extrabold text-muted-foreground active:scale-95"
          >
            还没有
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="flex-1 rounded-full bg-pink py-3.5 font-display text-lg font-extrabold text-primary-foreground shadow-md active:scale-95"
          >
            完成啦！
          </button>
        </div>
      </div>
    </div>
  )
}
