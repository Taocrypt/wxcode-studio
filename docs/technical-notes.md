# 葵花码工坊 APK (com.wxcode.studio)
Source: github.com/SunXin121/wxcode-studio (server version uses unidbg). This APK loads the arm64 libs natively.
- libwechatQrMod.so patched at 0x78d40: 88424239 (ldrb w8,[x20,#0x90]) -> 28008052 (mov w8,#1): Encode(format 18) always outputs SVG into the byte[] output. Verified identical SVG via unidbg (wxtools/tb).
- SVG (image/circle/line) rendered on Android Canvas; badge = assets/badge.png; logo clipped circle r130 at (300,300).
- Round-trip check: QbarNative.Init/ScanImage/GetOneResult with models copied to filesDir/qbar.
- Build: see README（`VC=5 VN=1.4 bash app/build.sh`，签名通过 KS / KS_PASS / KS_ALIAS 环境变量指定）
