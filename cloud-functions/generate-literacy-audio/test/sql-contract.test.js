'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const sqlRoot = path.resolve(__dirname, '../../../supabase/sql');
const generatorSql = fs.readFileSync(path.join(sqlRoot, '20260806_literacy_tts_generator_rpc.sql'), 'utf8');
const cleanupSql = fs.readFileSync(path.join(sqlRoot, '20260807_literacy_tts_cleanup.sql'), 'utf8');
const purgeDeletedAssetsSql = fs.readFileSync(path.join(sqlRoot, '20260809_literacy_tts_purge_deleted_assets.sql'), 'utf8');

test('队列 SQL 以唯一索引冲突处理和 SKIP LOCKED 防止重复合成', () => {
  const assetsSql = fs.readFileSync(path.join(sqlRoot, '20260806_literacy_tts_assets.sql'), 'utf8');
  assert.match(assetsSql, /create unique index if not exists literacy_tts_assets_root_item_voice_key/i);
  assert.match(assetsSql, /create unique index if not exists literacy_tts_assets_recognized_item_voice_key/i);
  assert.match(generatorSql, /on conflict \(root_literacy_character_id, item_type, item_order, voice_version\)/i);
  assert.match(generatorSql, /on conflict \(recognized_character_id, item_type, item_order, voice_version\)/i);
  assert.match(generatorSql, /for update skip locked/i);
});

test('归档生命周期先写字库再投递删除，失败任务可重试', () => {
  const knownCharacterWrite = cleanupSql.indexOf('insert into public.known_characters');
  const deletePending = cleanupSql.indexOf("set status = 'delete_pending'");
  assert.ok(knownCharacterWrite >= 0 && deletePending > knownCharacterWrite);
  assert.match(cleanupSql, /delete from public\.recognized_characters where id = cleanup_recognized_id/i);
  assert.match(cleanupSql, /not exists \(\s*select 1\s*from public\.literacy_tts_assets/i);
  assert.match(cleanupSql, /set status = 'delete_pending',[\s\S]*where id = p_asset_id[\s\S]*status = 'deleting'/i);
});

test('最后一个关联对象删除成功后自动清除已删除资产记录', () => {
  const purgeAssets = purgeDeletedAssetsSql.indexOf('delete from public.literacy_tts_assets a');
  const purgeRecognized = purgeDeletedAssetsSql.indexOf('delete from public.recognized_characters where id = cleanup_recognized_id');
  assert.ok(purgeAssets >= 0 && purgeRecognized > purgeAssets);
  assert.match(purgeDeletedAssetsSql, /where a\.status = 'deleted'/i);
  assert.match(purgeDeletedAssetsSql, /where a\.status <> 'deleted'/i);
});
