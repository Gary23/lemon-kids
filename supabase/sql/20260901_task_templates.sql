-- 家长端任务管理：任务定义与执行日程分离
CREATE TABLE IF NOT EXISTS task_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL,
    reward_points INT NOT NULL DEFAULT 5 CHECK (reward_points > 0),
    penalty_points INT NOT NULL DEFAULT 2,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_task_templates_family ON task_templates(family_id);

ALTER TABLE task_templates ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "task_templates_family_access" ON task_templates;
CREATE POLICY "task_templates_family_access" ON task_templates
    FOR ALL USING (family_id IN (
        SELECT family_id FROM users WHERE uid = auth.uid()
    ));

-- 如需实时显示模板改动，请在 Supabase Dashboard 的 Database Replication 中启用 task_templates。
