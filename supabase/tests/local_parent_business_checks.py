#!/usr/bin/env python3
"""对一次性副本做真实 Auth/PostgREST 与业务权限回归，不写源服务。"""
import base64
from datetime import date,timedelta
import hashlib
import hmac
import json
from pathlib import Path
import sys
import time
import uuid
from verify_local_parent_business import info,psql,request

ctx=json.loads(Path(sys.argv[1]).read_text()); db=ctx['db']
assert db.startswith('lemon-parent-verify-')
REST='http://127.0.0.1:54521'; AUTH='http://127.0.0.1:54524'
checks=[]
def mark(name): checks.append(name); print('PASS:',name,flush=True)
def expect(condition,label):
 if not condition: raise AssertionError(label)
def call(token,path,body=None,method=None,ok=(200,201,204),prefer=None):
 status,result=request(REST,path,body,token=token,method=method,prefer=prefer)
 if status not in ok:
  # 不输出请求、令牌或用户数据；错误只保留数据库错误代码与脱敏消息。
  code=result.get('code') if isinstance(result,dict) else None
  message=result.get('message','') if isinstance(result,dict) else ''
  raise AssertionError(f'{path.split("?")[0]} returned {status} {code}: {message[:160]}')
 return result

def jwt(secret,claims):
 enc=lambda x:base64.urlsafe_b64encode(json.dumps(x,separators=(',',':')).encode()).rstrip(b'=').decode()
 raw=enc({'alg':'HS256','typ':'JWT'})+'.'+enc(claims)
 return raw+'.'+base64.urlsafe_b64encode(hmac.new(secret.encode(),raw.encode(),hashlib.sha256).digest()).rstrip(b'=').decode()

authenv=dict(x.split('=',1) for x in info(ctx['auth'])['Config']['Env'])
admin=jwt(authenv['GOTRUE_JWT_SECRET'],{'role':'service_role','aud':'authenticated','exp':int(time.time())+3600})
fixtures=[]
for i in range(5):
 email=f'parent-business-{uuid.uuid4().hex}@example.invalid'; password=uuid.uuid4().hex
 status,res=request(AUTH,'/admin/users',{'email':email,'password':password,'email_confirm':True},token=admin)
 expect(status in (200,201),'temporary Auth user creation failed')
 uid=res['id']
 status,res=request(AUTH,'/token?grant_type=password',{'email':email,'password':password})
 expect(status==200,'temporary Auth password login failed')
 fixtures.append((uid,res['access_token']))
fa,fb=str(uuid.uuid4()),str(uuid.uuid4())
pa,pb,ca,cb,sibling=fixtures
psql(db,f"insert into public.families(id,name) values('{fa}','verify family A'),('{fb}','verify family B');")
for (uid,_),fam,role in [(pa,fa,'parent'),(pb,fb,'parent'),(ca,fa,'child'),(cb,fb,'child'),(sibling,fa,'child')]:
 psql(db,f"insert into public.users(uid,name,role,family_id) values('{uid}','verification','{role}','{fam}');")
P=pa[1]; B=pb[1]; C=ca[1]; S=sibling[1]
mark('real temporary Auth password login and JWT authorization')
expect(len(call(P,'/users?select=uid'))==3,'parent family users visibility')
for table in ['categories','task_templates','category_task_templates','rewards','badge_progress','device_status_logs','app_usage','app_limits']:
 expect(call(P,'/'+table)==[],table+' initial read must be a valid empty list')
 expect(request(REST,'/'+table)[0] in (401,403),table+' anonymous access must be denied')
mark('all management table reads and anonymous denial')
cat=call(P,'/categories',{'family_id':fa,'name':'verify category'},prefer='return=representation')[0]['id']
template=call(P,'/task_templates',{'family_id':fa,'title':'verify task','growth_domain':'reading','timer_enabled':True},prefer='return=representation')[0]['id']
other=call(B,'/task_templates',{'family_id':fb,'title':'other family'},prefer='return=representation')[0]['id']
call(P,'/rpc/set_category_task_templates',{'p_category_id':cat,'p_template_ids':[template]})
expect(call(B,'/categories')==[],'cross-family category read')
expect(request(REST,'/categories',{'family_id':fa,'name':'forbidden'},token=C)[0]>=400,'child category writes denied')
expect(request(REST,'/task_templates',{'family_id':fa,'title':'forbidden'},token=B)[0]>=400,'foreign parent catalogue insert denied')
expect(request(REST,'/rpc/set_category_task_templates',{'p_category_id':cat,'p_template_ids':[other]},token=P)[0]>=400,'cross-family assignment rejected')
expect(len(call(P,'/category_task_templates'))==1,'failed assignment is atomic')
expect(request(REST,'/category_task_templates',{'category_id':cat,'template_id':other,'family_id':fa},token=P)[0]>=400,'direct association writes denied')
mark('catalogue CRUD, atomic associations and family/child boundaries')
future=(date.today()+timedelta(days=2)).isoformat()
args={'p_child_id':ca[0],'p_category_id':cat,'p_template_id':None,'p_start_date':future,'p_end_date':future,'p_recurrence_type':'none','p_recurrence_weekdays':[],'p_selected_template_ids':[template]}
task=call(P,'/rpc/create_tasks_from_selection',args)[0]
expect(task['description']=='' and task['timer_enabled'],'task source snapshot')
call(P,'/rpc/update_daily_task_description',{'p_task_id':task['id'],'p_description':'daily content'})
call(P,'/rpc/create_tasks_from_selection',args)
expect(call(P,'/tasks?id=eq.'+task['id'])[0]['description']=='daily content','idempotent scheduling must preserve daily description')
call(P,'/rpc/rename_category',{'p_category_id':cat,'p_name':'renamed','p_color':'#4CAF50'})
expect(call(P,'/tasks?id=eq.'+task['id'])[0]['category']=='renamed','category rename snapshot')
expect(request(REST,'/rpc/create_tasks_from_selection',args,token=C)[0]>=400,'child cannot schedule tasks')
expect(request(REST,'/rpc/cancel_task',{'p_task_id':task['id']},token=B)[0]>=400,'foreign parent cannot cancel')
call(P,'/rpc/cancel_task',{'p_task_id':task['id']})
expect(call(P,'/tasks?id=eq.'+task['id'])[0]['deleted_at'] is not None,'cancel soft deletion')
call(P,'/rpc/restore_cancelled_task',{'p_task_id':task['id']})
expect(call(P,'/tasks?id=eq.'+task['id'])[0]['deleted_at'] is None,'restore preserves task')
expect(request(REST,'/tasks?id=eq.'+task['id'],{'deleted_at':'2030-01-01T00:00:00Z'},token=P,method='PATCH')[0]>=400,'direct history bypass rejected')
mark('task scheduling/deduplication, description, rename, cancellation and restore')
expect(call(P,'/rpc/get_task_duration_averages',{'p_family_id':fa})==[],'empty duration aggregate')
expect(call(P,'/rpc/get_task_duration_records',{'p_family_id':fa,'p_template_id':template})==[],'empty duration details')
call(C,'/rpc/task_timer_action',{'p_task_id':task['id'],'p_action':'start','p_mode':'countup','p_target_seconds':None})
psql(db,f"update public.task_timer_sessions set running_since=now()-interval '5 seconds',last_heartbeat=now() where task_id='{task['id']}';")
call(C,'/rpc/task_timer_action',{'p_task_id':task['id'],'p_action':'finish','p_mode':None,'p_target_seconds':None})
rows=call(P,'/rpc/get_task_duration_averages',{'p_family_id':fa})
expect(len(rows)==1 and rows[0]['sample_count']==1,'timer completion aggregate')
expect(call(S,'/task_duration_records')==[],'sibling duration privacy')
expect(request(REST,'/rpc/get_task_duration_averages',{'p_family_id':fa},token=B)[0]>=400,'foreign aggregate forbidden')
record=call(P,'/rpc/get_task_duration_records',{'p_family_id':fa,'p_template_id':template})[0]
call(P,'/rpc/delete_task_duration_record',{'p_record_id':record['id']})
expect(call(P,'/rpc/get_task_duration_averages',{'p_family_id':fa})==[],'duration deletion recalculates aggregate')
expect(request(REST,'/rpc/cancel_task',{'p_task_id':task['id']},token=P)[0]>=400,'completed task history retained')
call(C,'/rpc/undo_task_completion',{'p_task_id':task['id'],'p_child_id':ca[0]})
call(P,'/rpc/cancel_task',{'p_task_id':task['id']})
call(P,'/rpc/permanently_delete_recyclable_task',{'p_task_id':task['id']})
expect(call(P,'/tasks?id=eq.'+task['id'])==[],'recyclable task deletion')
expect(psql(db,"select count(*) from cron.job where jobname='task-duration-prune-hourly';").strip()=='1','single scheduled pruning job')
mark('timer completion/points transaction, privacy, deletion and history constraints')
progress={'p_child_id':ca[0],'p_badge_key':'reading','p_segment':'','p_value':12,'p_expected_version':0}
row=call(P,'/rpc/set_badge_progress',progress)
expect(row['version']==1 and row['value']==12,'badge versioned save')
expect(request(REST,'/rpc/set_badge_progress',progress,token=P)[0]>=400,'badge stale version conflict')
expect(request(REST,'/rpc/set_badge_progress',progress,token=C)[0]>=400,'child badge write forbidden')
expect(call(B,'/badge_progress')==[] and call(S,'/badge_progress')==[],'badge family/sibling isolation')
mark('badge progress, stale version conflict and auditing permissions')
reward=call(P,'/rewards',{'family_id':fa,'title':'verify reward','cost':2,'repeatable':True},prefer='return=representation')[0]
expect(call(B,'/rewards')==[],'reward family isolation')
expect(request(REST,'/rewards',{'family_id':fa,'title':'invalid','cost':0},token=P)[0]>=400,'reward non-positive cost rejected')
image=fa+'/'+str(uuid.uuid4())+'.jpg'
expect(call(P,'/rpc/can_access_reward_image',{'p_path':image,'p_write':True}) is True,'parent image upload permission')
expect(call(C,'/rpc/can_access_reward_image',{'p_path':image,'p_write':True}) is False,'child image writes denied')
expect(call(C,'/rpc/can_access_reward_image',{'p_path':image,'p_write':False}) is True,'child family image read permission')
expect(call(B,'/rpc/can_access_reward_image',{'p_path':image,'p_write':False}) is False,'foreign family image denied')
call(P,'/rewards?id=eq.'+reward['id'],{'image_path':image},method='PATCH')
expect(call(P,'/rpc/can_delete_reward_image',{'p_path':image}) is False,'referenced reward image retained')
expect(request(REST,'/rewards?id=eq.'+reward['id'],{'image_path':fb+'/'+str(uuid.uuid4())+'.jpg'},token=P,method='PATCH')[0]>=400,'cross-family image path rejected')
mark('reward catalogue and private image family/path restrictions')
# 真实兑换事务：请求幂等、余额不足时禁止撤销、取消返还、使用后不能取消。
args['p_start_date']=(date.today()+timedelta(days=3)).isoformat()
args['p_end_date']=args['p_start_date']
earning_task=call(P,'/rpc/create_tasks_from_selection',args)[0]
call(C,'/rpc/complete_task',{'p_task_id':earning_task['id'],'p_child_id':ca[0]})
redeem={'p_reward_id':reward['id'],'p_child_id':ca[0],'p_request_id':str(uuid.uuid4())}
held=call(C,'/rpc/redeem_reward',redeem)
expect(call(C,'/rpc/redeem_reward',redeem)==held,'reward request idempotency')
expect(call(C,'/users?uid=eq.'+ca[0])[0]['total_points']==3,'redemption deducts only once')
expect(request(REST,'/rpc/undo_task_completion',{'p_task_id':earning_task['id'],'p_child_id':ca[0]},token=C)[0]>=400,'spent points prevent task undo')
call(C,'/rpc/cancel_reward_redemption',{'p_redemption_id':held,'p_child_id':ca[0]})
expect(call(C,'/users?uid=eq.'+ca[0])[0]['total_points']==5,'cancelled redemption refunds points')
redeem['p_request_id']=str(uuid.uuid4())
held=call(C,'/rpc/redeem_reward',redeem)
call(C,'/rpc/use_reward_redemption',{'p_redemption_id':held,'p_child_id':ca[0]})
expect(request(REST,'/rpc/cancel_reward_redemption',{'p_redemption_id':held,'p_child_id':ca[0]},token=C)[0]>=400,'used reward cannot be refunded')
expect(call(B,'/reward_redemptions')==[] and call(S,'/reward_redemptions')==[],'redemption records remain private')
mark('reward redemption idempotency, points, refund/use transactions')
# 直接以请求角色访问 Storage 元数据，验证策略本身而非只调用辅助函数。
def storage_sql(uid,statement):
    return psql(db,"begin; set local storage.allow_delete_query='true'; set local role authenticated; select set_config('request.jwt.claim.sub','"+uid+"',true); select set_config('request.jwt.claim.role','authenticated',true); "+statement+"; rollback;")
unused_image=fa+'/'+str(uuid.uuid4())+'.jpg'
storage_sql(pa[0],"insert into storage.objects(bucket_id,name) values('reward-images','"+unused_image+"')")
storage_sql(pa[0],"insert into storage.objects(bucket_id,name) values('reward-images','"+unused_image+"'); delete from storage.objects where name='"+unused_image+"'; do $$ begin if exists(select 1 from storage.objects where name='"+unused_image+"') then raise exception 'Unused image could not be deleted'; end if; end $$")
for user_id in [ca[0],pb[0]]:
    failed=False
    try: storage_sql(user_id,"insert into storage.objects(bucket_id,name) values('reward-images','"+unused_image+"')")
    except RuntimeError: failed=True
    expect(failed,'Storage RLS must reject child/foreign image inserts')
# 未引用图片可新增、读取和删除；被奖励引用的图片不能删除，且不能覆盖。
storage_sql(pa[0],"insert into storage.objects(bucket_id,name) values('reward-images','"+image+"'); delete from storage.objects where bucket_id='reward-images' and name='"+image+"'; do $$ begin if not exists(select 1 from storage.objects where name='"+image+"') then raise exception 'Referenced image was deleted'; end if; end $$")
failed=False
try: storage_sql(pa[0],"insert into storage.objects(bucket_id,name) values('reward-images','"+unused_image+"'); update storage.objects set name='"+fa+"/"+str(uuid.uuid4())+".jpg' where name='"+unused_image+"'; do $$ begin if not exists(select 1 from storage.objects where name='"+unused_image+"') then raise exception 'Existing image was overwritten'; end if; end $$")
except RuntimeError: failed=True
expect(not failed,'Storage overwrite restrictive policy')
mark('Storage RLS rejects unauthorized insert, overwrite and referenced-image deletion')

call(C,'/device_status_logs',{'family_id':fa,'child_id':ca[0],'message':'verify heartbeat'})
expect(len(call(P,'/device_status_logs'))==1,'parent device log read')
expect(call(B,'/device_status_logs')==[] and call(S,'/device_status_logs')==[],'device log isolation')
expect(request(REST,'/device_status_logs',{'family_id':fb,'child_id':ca[0]},token=C)[0]>=400,'child report family mismatch rejected')
call(C,'/app_usage',{'family_id':fa,'child_id':ca[0],'package_name':'test.verify','app_name':'verify','date':future,'duration_seconds':30})
expect(len(call(P,'/app_usage'))==1 and call(S,'/app_usage')==[],'usage report read isolation')
call(P,'/app_limits',{'family_id':fa,'child_id':ca[0],'package_name':'test.verify','app_name':'verify','daily_limit_minutes':10})
expect(request(REST,'/app_limits',{'family_id':fa,'child_id':ca[0],'package_name':'bad','app_name':'bad'},token=C)[0]>=400,'child limit changes denied')
mark('device logs, app usage and parent limit management')
# Smoke check current seeded family copied from source with authenticated RLS, preserving source login data.
expect(psql(db,"select count(*) from storage.buckets where id='reward-images' and not public and file_size_limit=5242880;").strip()=='1','private image bucket definition')
expect(psql(db,"select count(*) from pg_policies where schemaname='storage' and policyname like 'reward_images_%';").strip()=='7','reward image storage policies')
mark('private Storage bucket and restrictive/permissive policies')
Path('/tmp/lemon-parent-business-check-results.json').write_text(json.dumps({'passed_groups':checks,'count':len(checks)},ensure_ascii=False,indent=2))
print('ALL PASSED:',len(checks),'verification groups',flush=True)
