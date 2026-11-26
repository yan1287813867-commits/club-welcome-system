"""Read-only SQLite -> empty MySQL database migration (Python standard library)."""
import argparse, getpass, os, shutil, sqlite3, subprocess
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',required=True)
parser.add_argument('--host',default='127.0.0.1')
parser.add_argument('--port',default='3306')
parser.add_argument('--user',default='root')
parser.add_argument('--database',default='club_welcome',choices=['club_welcome','club_welcome_test'])
a=parser.parse_args()
exe=shutil.which('mysql')
if not exe: raise SystemExit('mysql client not found in PATH')
env=os.environ.copy()
if 'MYSQL_PWD' not in env: env['MYSQL_PWD']=getpass.getpass('MySQL password: ')
cmd=[exe,'--no-defaults','--default-character-set=utf8mb4','--batch','--skip-column-names','--host='+a.host,'--port='+a.port,'--user='+a.user,a.database]
def run(sql):
 r=subprocess.run(cmd,input=sql.encode('utf-8'),stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env)
 if r.returncode: raise RuntimeError(r.stderr.decode('utf-8',errors='replace'))
 return r.stdout.decode('utf-8').strip()
tables=['signups','votes','sessions','limits']
for table in tables:
 if run('SELECT COUNT(*) FROM `'+table+'`;')!='0': raise SystemExit('Target must be empty; no changes made')
source=Path(a.source).resolve(strict=True)
with sqlite3.connect(source.as_uri()+'?mode=ro',uri=True) as db:
 db.execute('BEGIN')
 if db.execute('PRAGMA integrity_check').fetchone()[0]!='ok': raise SystemExit('Source integrity check failed')
 if db.execute('PRAGMA foreign_key_check').fetchall(): raise SystemExit('Source foreign keys invalid')
 sql=['START TRANSACTION;'];counts={}
 def literal(value):
  if value is None:return 'NULL'
  if isinstance(value,int):return str(value)
  return "CONVERT(X'"+str(value).encode('utf-8').hex()+"' USING utf8mb4)"
 for table in tables:
  rows=db.execute('SELECT * FROM "'+table+'"').fetchall();counts[table]=len(rows)
  for row in rows:sql.append('INSERT INTO `'+table+'` VALUES ('+','.join(map(literal,row))+');')
 sql.append('COMMIT;')
 run('\n'.join(sql))
for table,n in counts.items():
 actual=int(run('SELECT COUNT(*) FROM `'+table+'`;'))
 if actual!=n:raise RuntimeError('Count mismatch: '+table)
 print(table+': '+str(actual)+' rows verified')
print('Migration completed; original SQLite file unchanged.')
