"use client"

import { ClipboardList, CalendarDays, Gift, User } from "lucide-react"

export type TabKey = "tasks" | "calendar" | "rewards" | "profile"

const TABS: { key: TabKey; label: string; icon: typeof ClipboardList }[] = [
  { key: "tasks", label: "任务", icon: ClipboardList },
  { key: "calendar", label: "日历", icon: CalendarDays },
  { key: "rewards", label: "奖励", icon: Gift },
  { key: "profile", label: "我的", icon: User },
]

export function BottomNav({ active, onChange }: { active: TabKey; onChange: (t: TabKey) => void }) {
  return (
    <nav className="pointer-events-none absolute inset-x-0 bottom-0 z-40 flex justify-center px-5 pb-5">
      <div className="pointer-events-auto flex w-full max-w-md items-center justify-around gap-1 rounded-[2rem] bg-card/95 p-2 shadow-[0_10px_30px_-8px_rgba(255,133,162,0.4)] ring-1 ring-border backdrop-blur">
        {TABS.map((tab) => {
          const isActive = active === tab.key
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => onChange(tab.key)}
              aria-label={tab.label}
              aria-current={isActive ? "page" : undefined}
              className={`flex flex-1 flex-col items-center gap-1 rounded-3xl py-2.5 transition-colors ${
                isActive ? "bg-pink-soft/50" : ""
              }`}
            >
              <tab.icon
                className={`size-6 transition-transform ${isActive ? "scale-110 text-pink" : "text-muted-foreground"}`}
                strokeWidth={2.5}
              />
              <span
                className={`font-display text-xs font-extrabold ${isActive ? "text-pink" : "text-muted-foreground"}`}
              >
                {tab.label}
              </span>
            </button>
          )
        })}
      </div>
    </nav>
  )
}
