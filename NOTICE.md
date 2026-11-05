# 第三方组件说明

MIT 协议只覆盖本仓库中作者编写的代码（`app/src/com/wxcode/studio/`、`app/build.sh`、`tools/` 等）。以下内容不属于 MIT 授权范围，版权归各自所有者：

- `app/jni/arm64-v8a/*.so`、`app/assets/qbar/`：微信扫码 / 葵花码相关的原生库与模型，版权归腾讯所有。`libwechatQrMod.so` 在偏移 0x78d40 处打了一个 4 字节补丁（见 `docs/technical-notes.md`）。这些文件沿用自上游项目 [SunXin121/wxcode-studio](https://github.com/SunXin121/wxcode-studio) 的 `vendor/qbar/`。
- `app/src/com/tencent/qbar/`：为了与上述原生库的 JNI 签名对接而声明的 Java 接口类。
- `tools/unidbg-bridge` 运行时依赖 [unidbg](https://github.com/zhkl0228/unidbg)（Apache-2.0），由 Maven 下载，不随仓库分发。

本项目仅供学习与研究，与腾讯、微信无任何关联。请勿用于违反微信相关协议或法律法规的用途。
