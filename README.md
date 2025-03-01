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
- **WorldGuard プラグイン** (オプション：WorldGuard を使用する場合、プラグイン内での権限チェックが有効になります)

## インストール方法

1. `BoltExtension.jar` をサーバーの `plugins/` フォルダに配置します。
2. サーバーを再起動またはリロードします。
3. `plugins/BoltExtension/config.yml` を編集して、必要に応じた設定にカスタマイズしてください。

## 設定ファイル (`config.yml`)

~~~yaml
max-volume: 1000000  # 保護できる最大のブロック数

worldguard:
  enabled: true         # WorldGuard を使用するかどうか
  flag-default: true    # フラグのデフォルト値（true = ALLOW, false = DENY）
  allow-no-region: false  # WorldGuard 管理外の領域も許可するか
~~~

## コマンド一覧

- **/boltext public**  
  選択範囲内のブロック保護を「public」に設定します。

- **/boltext private**  
  選択範囲内のブロック保護を「private」に設定します。

- **/boltext transfer `<targetPlayer>`**  
  自分が所有するブロック保護を指定したプレイヤーに譲渡します。

- **/boltext unlock**  
  自分が所有するブロック保護を解除（削除）します。

- **/boltext admin unlock**  
  管理者権限を持つユーザーが、他プレイヤーのブロック保護を解除するためのコマンドです。実行後、確認のために **/boltext confirm** を実行してください。

- **/boltext confirm**  
  管理者による保護解除の最終確認コマンドです。/boltext admin unlock 実行後に入力する必要があります。

## 注意事項

- 選択範囲内のブロック数が `max-volume` の設定値を超える場合、処理が中断されます。
- WorldGuard を使用している場合、対象エリアの WorldGuard 権限設定（フラグ、オーナー・メンバー設定等）も考慮されます。
- 管理者コマンドは誤操作による他プレイヤーの保護データ削除を防ぐため、確認手順が必須となっています。

## 開発者情報

Developed by **NOASABA (by nanosize)**
