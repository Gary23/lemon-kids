-- 移除家长端“驳回任务”能力。保留 rejected 状态和既有积分记录，以便历史数据可正常展示。
DROP FUNCTION IF EXISTS public.reject_task(UUID);
