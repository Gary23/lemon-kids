"use client"

import { useState } from "react"
import { Star, Check, Gift, X, ArrowUpRight, ArrowDownRight, Clock } from "lucide-react"
import { useStore, type Reward, type LedgerEntry } from "./store"

export function RewardsPage() {
  const { points, rewards, ledger, redeemReward } = useStore()
  const [confirmReward, setConfirmReward] = useState<Reward | null>(null)

  return (
    <div className="min-h-full pb-4">
      <header className="rounded-b-[2.5rem] bg-gradient-to-br from-lavender to-accent px-6 pb-9 pt-10 shadow-[0_12px_30px_-12px_rgba(195,174,214,0.7)]">
        <p className="font-display text-xl font-extrabold text-primary-foreground/90">我的星星银行 🏦</p>
        <div className="mt-3 flex items-center gap-3">
          <div className="animate-bounce-soft flex size-16 items-center justify-center rounded-full bg-card/95 shadow-md">
            <Star className="size-9 fill-sunny text-sunny" strokeWidth={2} />
          </div>
          <div>
            <p className="font-display text-5xl font-extrabold leading-none text-primary-foreground drop-shadow-sm">
              {points}
            </p>
            <p className="mt-1 text-base font-bold text-primary-foreground/90">当前积分</p>
          </div>
        </div>
        <p className="mt-3 text-sm font-semibold text-primary-foreground/85">继续完成任务赚取更多星星吧！✨</p>
      </header>

      <div className="px-4 pt-5">
        <h2 className="mb-3 px-1 font-display text-xl font-extrabold text-foreground">兑换奖励 🎁</h2>
        <div className="grid grid-cols-2 gap-3">
          {rewards.map((r) => {
            const affordable = points >= r.cost
            return (
              <div
                key={r.id}
                className="flex flex-col items-center rounded-3xl bg-card p-4 text-center shadow-[0_8px_24px_-16px_rgba(0,0,0,0.2)]"
              >
                <div className="flex size-16 items-center justify-center rounded-2xl bg-sunny/25 text-4xl">
                  {r.emoji}
                </div>
                <p className="mt-2 font-display text-lg font-extrabold text-foreground text-balance">{r.name}</p>
                <p className="mt-0.5 flex items-center gap-1 text-base font-bold text-primary">
                  <Star className="size-4 fill-sunny text-sunny" strokeWidth={2} />
                  {r.cost}
                </p>
                {r.redeemed ? (
                  <div className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-full bg-mint-soft/50 py-2.5 font-bold text-mint">
                    <Check className="size-4" strokeWidth={3} />已兑换
                  </div>
                ) : affordable ? (
                  <button
                    type="button"
                    onClick={() => setConfirmReward(r)}
                    className="mt-3 w-full rounded-full bg-pink py-2.5 font-display text-base font-extrabold text-primary-foreground shadow-md active:scale-95"
                  >
                    兑换
                  </button>
                ) : (
                  <div className="mt-3 w-full rounded-full bg-muted py-2.5 text-sm font-bold text-muted-foreground">
                    还差 {r.cost - points} 个 ⭐
                  </div>
                )}
              </div>
            )
          })}
        </div>

        {/* 积分记录 */}
        <h2 className="mb-3 mt-6 px-1 font-display text-xl font-extrabold text-foreground">积分记录 📒</h2>
        <div className="overflow-hidden rounded-3xl bg-card shadow-[0_8px_24px_-16px_rgba(0,0,0,0.2)]">
          {ledger.map((e, i) => (
            <LedgerRow key={e.id} entry={e} last={i === ledger.length - 1} />
          ))}
        </div>
      </div>

      {confirmReward && (
        <RedeemDialog
          reward={confirmReward}
          onCancel={() => setConfirmReward(null)}
          onConfirm={() => {
            redeemReward(confirmReward.id)
            setConfirmReward(null)
          }}
        />
      )}
    </div>
  )
}

function LedgerRow({ entry, last }: { entry: LedgerEntry; last: boolean }) {
  const config = {
    earn: { icon: ArrowUpRight, bg: "bg-mint-soft/50", color: "text-mint" },
    spend: { icon: ArrowDownRight, bg: "bg-pink-soft/50", color: "text-pink" },
    expire: { icon: Clock, bg: "bg-muted", color: "text-muted-foreground" },
  }[entry.type]
  const Icon = config.icon
  return (
    <div className={`flex items-center gap-3 px-4 py-3.5 ${last ? "" : "border-b border-border"}`}>
      <div className={`flex size-10 shrink-0 items-center justify-center rounded-full ${config.bg} ${config.color}`}>
        <Icon className="size-5" strokeWidth={2.5} />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate font-display text-base font-bold text-foreground">{entry.label}</p>
        <p className="text-xs font-semibold text-muted-foreground">
          {entry.time} · 余额 {entry.balance}
        </p>
      </div>
      <span
        className={`shrink-0 font-display text-lg font-extrabold ${
          entry.amount > 0 ? "text-mint" : entry.amount < 0 ? "text-pink" : "text-muted-foreground"
        }`}
      >
        {entry.amount > 0 ? `+${entry.amount}` : entry.amount < 0 ? entry.amount : "—"}
      </span>
    </div>
  )
}

function RedeemDialog({
  reward,
  onCancel,
  onConfirm,
}: {
  reward: Reward
  onCancel: () => void
  onConfirm: () => void
}) {
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
        <div className="animate-wiggle mx-auto -mt-2 flex size-20 items-center justify-center rounded-full bg-lavender-soft/60 text-4xl">
          {reward.emoji}
        </div>
        <p className="mt-3 font-display text-2xl font-extrabold text-foreground">兑换「{reward.name}」？</p>
        <p className="mt-1.5 flex items-center justify-center gap-1 text-base font-bold text-muted-foreground">
          将花费
          <span className="flex items-center gap-1 text-pink">
            <Star className="size-4 fill-sunny text-sunny" strokeWidth={2} />
            {reward.cost}
          </span>
          个星星哦
        </p>
        <div className="mt-6 flex gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-full bg-muted py-3.5 font-display text-lg font-extrabold text-muted-foreground active:scale-95"
          >
            再想想
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="flex-1 rounded-full bg-pink py-3.5 font-display text-lg font-extrabold text-primary-foreground shadow-md active:scale-95"
          >
            <Gift className="mr-1 inline size-5" strokeWidth={2.5} />兑换
          </button>
        </div>
      </div>
    </div>
  )
}
