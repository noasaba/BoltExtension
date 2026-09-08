![License](https://img.shields.io/github/license/noasaba/BoltExtension)
![Last Commit](https://img.shields.io/github/last-commit/noasaba/BoltExtension)
![Release](https://img.shields.io/github/release/noasaba/BoltExtension)

# BoltExtension

## 概要

**BoltExtension** は、Minecraft のプラグイン **Bolt** の機能を拡張し、ワールド内のブロック保護を簡単に操作できるようにするプラグインです。WorldEdit を使用して選択した範囲のブロック保護を変更、削除、譲渡することができます。

## 必要条件

- **Minecraft サーバー (Paper, Spigot, Bukkit 互換)**
- **Minecraft Java Edition 26.1.2**
- **JDK 25 以降**
- **WorldEdit プラグイン** (必須)
- **Bolt プラグイン** (必須)
- **WorldGuard プラグイン** (必須：WorldGuard のリージョン権限チェックに使用します)

## インストール方法

1. Paper サーバーには `boltextension-paper-<version>.jar` を `plugins/` フォルダに配置します。
2. Velocity を使う場合は `boltextension-velocity-<version>.jar` を Velocity の `plugins/` フォルダに配置します。
3. Paper サーバーを再起動またはリロードします。
4. `plugins/BoltExtension/config.yml` を編集して、必要に応じた設定にカスタマイズしてください。

## リリース成果物

`./gradlew release` を実行すると、成果物はプラットフォーム別に出力されます。

~~~text
build/release/
  paper/
    boltextension-paper-<version>.jar
  velocity/
    boltextension-velocity-<version>.jar
~~~

Paper jar は実際の WorldEdit / WorldGuard / Bolt 連携と `/boltext` コマンドを提供します。Velocity jar は現時点ではロード通知だけを行うstubです。保護操作・同期・認可はすべてPaper側で実行されます。
Paper API は `26.1.2` を参照し、`plugin.yml` のAPI世代は `26.1` として宣言します。

## 設定ファイル (`config.yml`)

~~~yaml
max-volume: 1000000  # 保護できる最大のブロック数

worldguard:
  checks-enabled: true  # BoltExtensionによるWorldGuard認可チェックを使用するか
  flag-default: true    # フラグのデフォルト値（true = ALLOW, false = DENY）
  allow-no-region: false  # WorldGuard 管理外の領域も許可するか
  require-build-access: true  # WorldGuard の有効な BUILD 判定も要求するか
  require-membership: true  # 重なる全リージョンで owner/member を要求するか

permissions:
  require-bolt-command-permissions: true  # Bolt本体のコマンド権限も要求する
~~~

リージョン単位でBoltExtensionだけを許可・拒否する場合は、WorldGuardで次のように設定します。

~~~text
/rg flag <region> bolt-extension-allow allow
/rg flag <region> bolt-extension-allow deny
~~~

通常操作は、実際に処理する各ブロック位置で有効な `bolt-extension-allow`、WorldGuardの `BUILD`、必要ならowner/memberを順に確認します。選択範囲に離れて存在する高priorityリージョンが、別地点のDENYを打ち消すことはありません。リージョンデータを読み込めない場合は、設定にかかわらず安全側で拒否します。

## コマンド一覧

権限、使い方、具体例は [command.md](command.md) にまとめています。

- **/boltext public**  
  選択範囲内の自分が所有する保護を「public」に変更します。未保護かつ Bolt が保護対象として扱うブロックには新規保護を作成します。

- **/boltext private**  
  選択範囲内の自分が所有する保護を「private」に変更します。未保護かつ Bolt が保護対象として扱うブロックには新規保護を作成します。

- **/boltext set `<protectionType>`**
  Boltに登録されている任意のprotection typeへ、既存保護を変更または未保護ブロックを新規保護します。

- **/boltext transfer `<targetPlayer>`**  
  自分が所有するブロック保護を指定したプレイヤーに譲渡します。

- **/boltext unlock**  
  自分が所有するブロック保護を解除（削除）します。

- **/boltext access add `<player>` [accessType]** / **/boltext access remove `<player>`**
  既存保護のaccess listだけを変更します。操作にはownerまたはBoltの`edit` accessが必要です。`/boltext access add-group <group> [accessType]` と `/boltext access remove-group <group>` は、Boltに登録されたグループを対象にします。`user` は `access` のaliasです。未保護ブロックを新規保護しません。

- **/boltext entity `<set|public|private|transfer|unlock>` [args...]**
  選択範囲にいる現在ロード済みの保護可能Entityを対象に、同等の保護操作を行います。Entity位置ごとにWorldGuardを確認します。

- **/boltext audit invalid [page]**
  現在Boltでprotectableではないのに残っている既存Block Protectionを確認専用で表示します。

- **/boltext admin unlock**  
  管理者権限を持つユーザーが、他プレイヤーのブロック保護を解除するためのコマンドです。実行後、確認のために **/boltext confirm** を実行してください。

- **/boltext confirm**  
  管理者による保護解除の最終確認コマンドです。/boltext admin unlock 実行後に入力する必要があります。

- **/boltext inspect**  
  見ているブロックの Bolt 保護対象判定、既存保護、所有者一致、WorldGuard 判定を表示します。

- **/boltext debug status**
  Java、サーバー、依存プラグイン、WorldGuard連携設定の状態を表示します。

- **/boltext scan `<public|private|set|transfer|unlock|access|entity|admin unlock>` [args...]**
  実行せずに、選択範囲内で何件が対象・スキップ・失敗になりそうかを確認します。

## 注意事項

- 選択範囲内のブロック数が `max-volume` の設定値を超える場合、処理が中断されます。
- `max-volume` を `0` 以下にすると、範囲サイズ制限を無効化します。
- type・access変更は対象保護のownerまたはBoltの`edit` accessを持つ利用者が実行できます。譲渡・解除はownerのみです。
- 対象エリアの WorldGuard 権限設定（フラグ、オーナー・メンバー設定等）も考慮されます。
- WorldGuard の `bolt-extension-allow` と有効な `BUILD` 判定を使い、リージョンの優先度・継承・region groupを反映します。
- 非直方体のWorldEdit選択では、外接直方体ではなく実際に選択されたブロックだけを処理します。
- ドアや連結チェストなどの代表保護が選択外にある場合、その代表ブロック側のWorldGuard権限も確認します。
- 管理者のunlockだけがowner判定をバイパスできます。WorldGuard判定はバイパスせず、誤操作防止の確認手順と削除直前の再検証を必須にしています。
- `scan` は変更を行いません。新規作成については外部プラグインがキャンセルできる`LockBlockEvent`を発火しないため、`LOCK_EVENT_NOT_EVALUATED`が表示された件数は本実行時に変わる可能性があります。
- Entity操作は選択範囲内で現在ロード済みのEntityだけを対象にします。チャンクを強制ロードしません。

## 開発者情報

Developed by **NOASABA (by nanosize)**
