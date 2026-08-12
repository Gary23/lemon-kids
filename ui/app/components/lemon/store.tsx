"use client"

import { createContext, useContext, useMemo, useState, type ReactNode } from "react"

/* ---------- 类型 ---------- */
export type TaskGroup = "today" | "missed" | "later"
export type TaskStatus = "pending" | "done" | "missed"

export type Task = {
  id: string
  title: string
  desc: string
  points: number
  deadline: string // HH:MM
  date: string // YYYY-MM-DD
  group: TaskGroup
  status: TaskStatus
}

export type Reward = {
  id: string
  name: string
  emoji: string
  cost: number
  redeemed: boolean
}

export type LedgerType = "earn" | "spend" | "expire"
export type LedgerEntry = {
  id: string
  type: LedgerType
  label: string
  amount: number // 正负
  balance: number
  time: string
}

/* ---------- 常量：今天 ---------- */
export const TODAY = "2026-07-10"

/* ---------- 初始数据 ---------- */
const initialTasks: Task[] = [
  {
    id: "t1",
    title: "认真刷牙牙",
    desc: "早晚各刷一次，牙齿白白哒",
    points: 5,
    deadline: "08:00",
    date: TODAY,
    group: "today",
    status: "pending",
  },
  {
    id: "t2",
    title: "整理小书包",
    desc: "把明天上课的书本收拾好",
    points: 8,
    deadline: "19:30",
    date: TODAY,
    group: "today",
    status: "pending",
  },
  {
    id: "t3",
    title: "读绘本 15 分钟",
    desc: "挑一本喜欢的故事书读给妈妈听",
    points: 10,
    deadline: "20:00",
    date: TODAY,
    group: "today",
    status: "pending",
  },
  {
    id: "t4",
    title: "练琴小时光",
    desc: "弹一遍新学的曲子",
    points: 12,
    deadline: "18:00",
    date: TODAY,
    group: "today",
    status: "done",
  },
  {
    id: "m1",
    title: "浇花小任务",
    desc: "给窗台的小花浇浇水",
    points: 5,
    deadline: "17:00",
    date: "2026-07-09",
    group: "missed",
    status: "missed",
  },
  {
    id: "m2",
    title: "写数学作业",
    desc: "完成第 3 页的练习题",
    points: 10,
    deadline: "20:00",
    date: "2026-07-08",
    group: "missed",
    status: "missed",
  },
  {
    id: "l1",
    title: "画一幅画",
    desc: "周末画出你的梦想城堡",
    points: 15,
    deadline: "16:00",
    date: "2026-07-12",
    group: "later",
    status: "pending",
  },
  {
    id: "l2",
    title: "帮妈妈摆碗筷",
    desc: "吃饭前摆好一家人的碗筷",
    points: 6,
    deadline: "18:30",
    date: "2026-07-13",
    group: "later",
    status: "pending",
  },
]

const initialRewards: Reward[] = [
  { id: "r1", name: "看动画片 30 分钟", emoji: "📺", cost: 20, redeemed: false },
  { id: "r2", name: "一颗棒棒糖", emoji: "🍭", cost: 15, redeemed: false },
  { id: "r3", name: "去公园玩", emoji: "🎠", cost: 40, redeemed: false },
  { id: "r4", name: "买新贴纸", emoji: "✨", cost: 30, redeemed: false },
  { id: "r5", name: "小熊玩偶", emoji: "🧸", cost: 80, redeemed: false },
  { id: "r6", name: "冰淇淋一个", emoji: "🍦", cost: 25, redeemed: true },
]

const initialLedger: LedgerEntry[] = [
  { id: "g1", type: "earn", label: "完成「练琴小时光」", amount: 12, balance: 85, time: "今天 18:05" },
  { id: "g2", type: "spend", label: "兑换「冰淇淋一个」", amount: -25, balance: 73, time: "昨天 16:20" },
  { id: "g3", type: "earn", label: "完成「按时起床」", amount: 5, balance: 98, time: "昨天 07:30" },
  { id: "g4", type: "expire", label: "错过「浇花小任务」", amount: 0, balance: 93, time: "前天 17:00" },
]

/* ---------- Context ---------- */
type Store = {
  nickname: string
  setNickname: (n: string) => void
  avatar: string
  setAvatar: (a: string) => void
  points: number
  streak: number
  tasks: Task[]
  rewards: Reward[]
  ledger: LedgerEntry[]
  completeTask: (id: string) => void
  undoTask: (id: string) => void
  redeemReward: (id: string) => void
  screenTimeUsed: number // 分钟
  screenTimeLimit: number
}

const StoreContext = createContext<Store | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [nickname, setNickname] = useState("小柠檬")
  const [avatar, setAvatar] = useState("/avatar-girl.png")
  const [points, setPoints] = useState(85)
  const [streak] = useState(7)
  const [tasks, setTasks] = useState<Task[]>(initialTasks)
  const [rewards, setRewards] = useState<Reward[]>(initialRewards)
  const [ledger, setLedger] = useState<LedgerEntry[]>(initialLedger)
  const [screenTimeUsed] = useState(18)
  const [screenTimeLimit] = useState(30)

  function nowLabel() {
    return "刚刚"
  }

  function completeTask(id: string) {
    setTasks((prev) => {
      const task = prev.find((t) => t.id === id)
      if (!task || task.status === "done") return prev
      setPoints((p) => {
        const next = p + task.points
        setLedger((l) => [
          { id: `e${Date.now()}`, type: "earn", label: `完成「${task.title}」`, amount: task.points, balance: next, time: nowLabel() },
          ...l,
        ])
        return next
      })
      return prev.map((t) => (t.id === id ? { ...t, status: "done" } : t))
    })
  }

  function undoTask(id: string) {
    setTasks((prev) => {
      const task = prev.find((t) => t.id === id)
      if (!task || task.status !== "done") return prev
      setPoints((p) => Math.max(0, p - task.points))
      return prev.map((t) => (t.id === id ? { ...t, status: "pending" } : t))
    })
  }

  function redeemReward(id: string) {
    setRewards((prev) => {
      const reward = prev.find((r) => r.id === id)
      if (!reward || reward.redeemed || points < reward.cost) return prev
      const next = points - reward.cost
      setPoints(next)
      setLedger((l) => [
        { id: `s${Date.now()}`, type: "spend", label: `兑换「${reward.name}」`, amount: -reward.cost, balance: next, time: nowLabel() },
        ...l,
      ])
      return prev.map((r) => (r.id === id ? { ...r, redeemed: true } : r))
    })
  }

  const value = useMemo(
    () => ({
      nickname,
      setNickname,
      avatar,
      setAvatar,
      points,
      streak,
      tasks,
      rewards,
      ledger,
      completeTask,
      undoTask,
      redeemReward,
      screenTimeUsed,
      screenTimeLimit,
    }),
    [nickname, avatar, points, streak, tasks, rewards, ledger, screenTimeUsed, screenTimeLimit],
  )

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error("useStore must be used within StoreProvider")
  return ctx
}
