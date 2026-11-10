#!/usr/bin/env python3
"""Read-only by default. Use --apply only after deployment approval."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from local_kid_task_support import SQL, SOURCE, migration_bundle, preflight, snapshot_all, psql


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--receipt', type=Path, default=Path('/tmp/lemon-kid-task-deployment.json'))
    args = parser.parse_args()
    print(preflight())
    if not args.apply:
        print('Read-only preflight complete. No changes applied.')
        return
    before = snapshot_all(SOURCE)
    psql(SOURCE, migration_bundle())
    assert snapshot_all(SOURCE) == before, 'Business data changed; inspect source before further action'
    args.receipt.write_text(json.dumps({
        'timestamp': datetime.now(timezone.utc).isoformat(), 'container': SOURCE,
        'sql': SQL.name, 'sha256': hashlib.sha256(SQL.read_bytes()).hexdigest(),
        'public_data_unchanged': True, 'table_counts': {k: v['count'] for k, v in before.items()}
    }, indent=2) + '\n')
    print('Growth RPC and own-usage policy applied; all public business values preserved. Receipt:', args.receipt)


if __name__ == '__main__':
    main()
