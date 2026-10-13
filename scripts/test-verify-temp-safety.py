"""临时目录不可用时，验证脚本必须退出且不能清理工作树点文件。"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
bash = r"C:\Program Files\Git\bin\bash.exe" if os.name == "nt" else shutil.which("bash")
if not bash or not Path(bash).is_file():
    print("skipped: bash unavailable")
    raise SystemExit(0)
with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    (root / "scripts").mkdir()
    shutil.copyfile(ROOT / "scripts/verify-local.sh", root / "scripts/verify-local.sh")
    names = [".git", ".gitignore", ".nvmrc"]
    for name in names:
        (root / name).write_text("sentinel", encoding="utf-8")
    env = dict(os.environ, TMPDIR=(root / "missing-temp-directory").as_posix())
    result = subprocess.run([bash, "scripts/verify-local.sh", "--quick"], cwd=root,
                            env=env, capture_output=True)
    assert result.returncode != 0, "临时文件创建失败必须中止"
    assert all((root / name).read_text(encoding="utf-8") == "sentinel" for name in names)
print("verify-local: failed mktemp preserves worktree files")
