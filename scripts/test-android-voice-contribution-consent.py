#!/usr/bin/env python3
"""本机改版不能从键盘或设置触发语音贡献上传。"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "platforms/android/java/app/msime/android/core/ImeVoiceEntry.java"


def main() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    page = (ROOT / "platforms/android/java/app/msime/android/home/VoicePage.java").read_text(encoding="utf-8")
    gradle = (ROOT / "platforms/android/gradle-app/app/build.gradle.kts").read_text(encoding="utf-8")
    for text in (source, page):
        if any(marker in text for marker in ("new VoiceContributionApi", "VOICE_CONTRIBUTE_AUDIO", "private void contribute(")):
            raise SystemExit("本机改版仍可触发贡献上传")
    if '"app/msime/android/voice/VoiceContributionApi.java"' not in gradle:
        raise SystemExit("贡献上传实现不能进入本机 APK")
    print("本机改版没有语音贡献入口或打包实现")


if __name__ == "__main__":
    main()
