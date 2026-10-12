#!/usr/bin/env python3
"""本机改版关闭遥测，隐私重启仍使旧输入生命周期失效。"""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
VOICE = ROOT / "platforms/android/java/app/msime/android/core/ImeVoiceEntry.java"
SERVICE = ROOT / "platforms/android/java/app/msime/android/core/MSIMEInputService.java"


def method(source: str, signature: str, end_marker: str) -> str:
    start = source.index(signature)
    end = source.index(end_marker, start)
    return source[start:end]


def main() -> None:
    voice = VOICE.read_text(encoding="utf-8")
    service = SERVICE.read_text(encoding="utf-8")
    restart = method(service, "    private void restartSessionForPrivacy()", "    /** 功能面板的手写" )

    if "VoiceContributionApi" in voice:
        raise SystemExit("键盘不能引用贡献上传")
    telemetry = (ROOT / "platforms/android/java/app/msime/android/core/Telemetry.java").read_text(encoding="utf-8")
    for entry in ("start", "beginInputSession", "inputViewShown", "endInputSession", "setEnabled", "flush"):
        if not re.search(r"\bvoid\s+" + entry + r"\([^)]*\)\s*\{\s*if \(!LocalForkMode.accountServicesEnabled\(\)\) return;", telemetry):
            raise SystemExit(f"本机遥测入口 {entry} 未被禁用")
    stop = restart.index("stop(false);")
    if "++engineStartGeneration" not in restart[:stop]:
        raise SystemExit("privacy restart must invalidate queued contributions before stopping the session")
    print("本机遥测入口关闭，隐私重启使旧会话失效")


if __name__ == "__main__":
    main()
