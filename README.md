![License](https://img.shields.io/github/license/noasaba/BoltExtension)
![Last Commit](https://img.shields.io/github/last-commit/noasaba/BoltExtension)
![Release](https://img.shields.io/github/release/noasaba/BoltExtension)

# BoltExtension

## 概要

**BoltExtension** は、Minecraft のプラグイン **Bolt** の機能を拡張し、ワールド内のブロック保護を簡単に操作できるようにするプラグインです。WorldEdit を使用して選択した範囲のブロック保護を変更、削除、譲渡することができます。

## 必要条件

- **Minecraft サーバー (Paper, Spigot, Bukkit 互換)**
- **WorldEdit プラグイン** (必須)
- **Bolt プラグイン** (必須)

## インストール方法

1. `BoltExtension.jar` を `plugins/` フォルダに配置します。
2. サーバーを再起動またはリロードします。
3. `plugins/BoltExtension/config.yml` を編集し、設定をカスタマイズできます。

## 設定ファイル (`config.yml`)

```yaml
max-volume: 1000000  # 保護できる最大のブロック数
