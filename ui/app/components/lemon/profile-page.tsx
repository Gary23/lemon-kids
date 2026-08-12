"use client"

import { useState } from "react"
import { Star, Pencil, Calendar, Gift, Lock, Tablet, ChevronRight, LogOut, Check, X } from "lucide-react"
import { useStore } from "./store"

export function ProfilePage() {
  const { nickname, setNickname, avatar, setAvatar, points, screenTimeUsed, screenTimeLimit } = useStore()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(nickname)

  const avatarChoices = ["/avatar-girl.png", "🦄", "🐰", "🐱", "🌸", "🦋"]
  const [showAvatarPicker, setShowAvatarPicker] = useState(false)

  const pct = Math.min(100, Math.round((screenTimeUsed / screenTimeLimit) * 100))

  const entries = [
    { icon: Calendar, color: "text-lavender", bg: "bg-lavender-soft/50", title: "计划", desc: "查看未来日期的任务安排" },
    { icon: Gift, color: "text-pink", bg: "bg-pink-soft/50", title: "我的奖励", desc: "查看已兑换的奖励" },
    { icon: Lock, color: "text-coral", bg: "bg-coral-soft/50", title: "使用限制", desc: "微信 30 分钟 / 天" },
  ]

  return (
    <div className="min-h-full pb-4">
      <header className="relative overflow-hidden rounded-b-[2.5rem] bg-gradient-to-br from-pink-soft via-secondary to-lavender px-6 pb-9 pt-10 text-center shadow-[0_12px_30px_-12px_rgba(255,133,162,0.6)]">
        <div className="pointer-events-none absolute -left-4 top-6 text-5xl opacity-30">✨</div>
        <div className="pointer-events-none absolute -right-3 top-20 text-4xl opacity-30">🌸</div>

        <button
          type="button"
          onClick={() => setShowAvatarPicker(true)}
          className="relative mx-auto flex size-28 items-center justify-center overflow-hidden rounded-full border-4 border-card bg-card shadow-lg active:scale-95"
        >
          {avatar.startsWith("/") ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={avatar || "/placeholder.svg"} alt="我的头像" className="size-full object-cover" />
          ) : (
            <span className="text-6xl">{avatar}</span>
          )}
          <span className="absolute bottom-0 right-0 flex size-8 items-center justify-center rounded-full bg-pink text-primary-foreground shadow">
            <Pencil className="size-4" strokeWidth={2.5} />
          </span>
        </button>

        <div className="mt-3 flex items-center justify-center gap-2">
          {editing ? (
            <div className="flex items-center gap-2">
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                maxLength={8}
                autoFocus
                className="w-36 rounded-full bg-card px-4 py-1.5 text-center font-display text-xl font-extrabold text-foreground outline-none ring-2 ring-pink"
              />
              <button
                type="button"
                onClick={() => {
                  if (draft.trim()) setNickname(draft.trim())
                  setEditing(false)
                }}
                aria-label="保存昵称"
                className="flex size-9 items-center justify-center rounded-full bg-mint text-primary-foreground active:scale-90"
              >
                <Check className="size-5" strokeWidth={3} />
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => {
                setDraft(nickname)
                setEditing(true)
              }}
              className="flex items-center gap-1.5 active:scale-95"
            >
              <span className="font-display text-2xl font-extrabold text-primary-foreground drop-shadow-sm">{nickname}</span>
              <Pencil className="size-4 text-primary-foreground/80" strokeWidth={2.5} />
            </button>
          )}
        </div>

        <div className="mt-2 inline-flex items-center gap-1.5 rounded-full bg-card/90 px-4 py-1.5 shadow-sm">
          <Star className="size-5 fill-sunny text-sunny" strokeWidth={2} />
          <span className="font-display text-xl font-extrabold text-primary">{points}</span>
          <span className="text-sm font-bold text-muted-foreground">积分</span>
        </div>
        <p className="mt-2 text-xs font-semibold text-primary-foreground/80">点击头像换头像，点击名字改昵称</p>
      </header>

      <div className="space-y-3 px-4 pt-5">
        {entries.map((e) => (
          <button
            key={e.title}
            type="button"
            className="flex w-full items-center gap-3.5 rounded-3xl bg-card p-4 text-left shadow-[0_8px_24px_-16px_rgba(0,0,0,0.2)] active:scale-[0.99]"
          >
            <div className={`flex size-12 shrink-0 items-center justify-center rounded-2xl ${e.bg} ${e.color}`}>
              <e.icon className="size-6" strokeWidth={2.5} />
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-display text-lg font-extrabold text-foreground">{e.title}</p>
              <p className="truncate text-sm font-semibold text-muted-foreground">{e.desc}</p>
            </div>
            <ChevronRight className="size-6 shrink-0 text-muted-foreground" strokeWidth={2.5} />
          </button>
        ))}

        {/* 今日平板使用 */}
        <div className="rounded-3xl bg-card p-4 shadow-[0_8px_24px_-16px_rgba(0,0,0,0.2)]">
          <div className="flex items-center gap-3.5">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-mint-soft/50 text-mint">
              <Tablet className="size-6" strokeWidth={2.5} />
            </div>
            <div className="flex-1">
              <p className="font-display text-lg font-extrabold text-foreground">今日平板使用</p>
              <p className="text-sm font-semibold text-muted-foreground">
                已使用 {screenTimeUsed} 分钟 / {screenTimeLimit} 分钟
              </p>
            </div>
          </div>
          <div className="mt-3 h-3.5 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-gradient-to-r from-mint to-lavender transition-all"
              style={{ width: `${pct}%` }}
            />
          </div>
        </div>

        {/* 切换账号 */}
        <button
          type="button"
          className="mt-2 flex w-full items-center justify-center gap-2 rounded-full bg-coral py-3.5 font-display text-lg font-extrabold text-primary-foreground shadow-md active:scale-95"
        >
          <LogOut className="size-5" strokeWidth={2.5} />切换账号
        </button>
      </div>

      {/* 头像选择弹窗 */}
      {showAvatarPicker && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/30 p-6 backdrop-blur-sm"
          onClick={() => setShowAvatarPicker(false)}
        >
          <div
            className="animate-pop-in w-full max-w-sm rounded-[2rem] bg-card p-6 shadow-2xl"
            onClick={(ev) => ev.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <p className="font-display text-xl font-extrabold text-foreground">换个头像吧 🎨</p>
              <button
                type="button"
                onClick={() => setShowAvatarPicker(false)}
                aria-label="关闭"
                className="flex size-9 items-center justify-center rounded-full bg-muted text-muted-foreground active:scale-90"
              >
                <X className="size-5" strokeWidth={2.5} />
              </button>
            </div>
            <div className="mt-4 grid grid-cols-3 gap-3">
              {avatarChoices.map((a) => (
                <button
                  key={a}
                  type="button"
                  onClick={() => {
                    setAvatar(a)
                    setShowAvatarPicker(false)
                  }}
                  className={`flex aspect-square items-center justify-center overflow-hidden rounded-2xl bg-sunny/20 text-4xl active:scale-95 ${
                    avatar === a ? "ring-2 ring-pink" : ""
                  }`}
                >
                  {a.startsWith("/") ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={a || "/placeholder.svg"} alt="头像选项" className="size-full object-cover" />
                  ) : (
                    a
                  )}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
