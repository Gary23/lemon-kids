"use client"

import { useState } from "react"
import { StoreProvider } from "@/components/lemon/store"
import { BottomNav, type TabKey } from "@/components/lemon/bottom-nav"
import { TaskListPage } from "@/components/lemon/task-list-page"
import { CalendarPage } from "@/components/lemon/calendar-page"
import { RewardsPage } from "@/components/lemon/rewards-page"
import { ProfilePage } from "@/components/lemon/profile-page"

export default function Page() {
  const [tab, setTab] = useState<TabKey>("tasks")

  return (
    <StoreProvider>
      {/* 居中的 iPad 竖屏画框 */}
      <main className="flex min-h-screen items-center justify-center bg-gradient-to-b from-sunny/40 to-background p-0 sm:p-6">
        <div className="relative flex h-screen w-full max-w-[768px] flex-col overflow-hidden bg-background shadow-2xl sm:h-[1024px] sm:rounded-[2.5rem] sm:ring-8 sm:ring-card">
          <div className="flex-1 overflow-y-auto">
            {tab === "tasks" && <TaskListPage />}
            {tab === "calendar" && <CalendarPage />}
            {tab === "rewards" && <RewardsPage />}
            {tab === "profile" && <ProfilePage />}
            <div className="h-28" />
          </div>
          <BottomNav active={tab} onChange={setTab} />
        </div>
      </main>
    </StoreProvider>
  )
}
