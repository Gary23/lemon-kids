#!/usr/bin/env python3
"""对固定的本地项目预检/应用；测试通过并获部署确认后才使用 --apply。"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from verify_local_parent_business import ROOT,SOURCE,psql,snapshot,compare_snapshot,migration_files,migration_bundle

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--apply',action='store_true',help='Only after explicit deployment approval')
    ap.add_argument('--receipt',type=Path,default=Path('/tmp/lemon-parent-business-deployment.json'))
    args=ap.parse_args()
    base=ROOT/'supabase/sql'
    print(psql(SOURCE,(base/'20261029_local_parent_business_preflight.sql').read_text()))
    if not args.apply:
        print('Read-only preflight complete. No changes applied.')
        return
    before=snapshot(SOURCE)
    scripts={path.name:path.read_text() for path in migration_files()}
    psql(SOURCE,migration_bundle(),user='supabase_admin')
    print('Applied all three migration sections in one transaction with explicit role changes.')
    compare_snapshot(SOURCE,before)
    receipt={'timestamp':datetime.now(timezone.utc).isoformat(),'container':SOURCE,
             'scripts':{name:hashlib.sha256(text.encode()).hexdigest() for name,text in scripts.items()},
             'preserved_counts':{name:value['count'] for name,value in before.items()},
             'original_values_unchanged':True}
    args.receipt.write_text(json.dumps(receipt,indent=2))
    print('Original core data unchanged; receipt:',args.receipt)

if __name__=='__main__':main()
