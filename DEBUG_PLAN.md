# BoltExtension Code Review and Debug Plan

作成日: 2026-05-20

## 2.0 再実装状況（2026-08-02）

本計画をもとに、以下を再実装した。

- lifecycle、コマンド、WorldEdit選択、WorldGuard判定、Bolt操作、集計モデルをクラス単位に分離。
- 未保護ブロックの新規作成前に `BoltAPI.isProtectable` を必須化。
- プレイヤー別の新規保護可否として、Boltに登録された保護タイプ、`bolt.type.protection.<type>`、ブロック設定の `lockPermission` と `bolt.protection.lock.<material>`、`LockBlockEvent` のキャンセル結果を個別に判定する。
- 既存保護は `findProtection` と protection ID の重複排除を使い、通常操作を owner-only に統一。
- WorldEditの実際の選択点だけを走査し、非直方体選択の外接範囲を誤操作しないよう変更。
- WorldGuardの `ApplicableRegionSet` で、優先度、継承、region group、グローバルリージョンを含むフラグ計算を使用。
- `bolt-extension-allow`、標準 `BUILD`、全リージョンのowner/member、標準bypassを個別設定可能にした。
- 連結ブロックの代表Bolt保護が選択外にある場合、その代表ブロック側のWorldGuard判定も追加。
- admin unlockを対象ID、選択スナップショット、期限に紐づけ、confirm直前に現在のIDを再検証。
- dry-runの `scan`、ブロック診断の `inspect`、環境診断の `debug status`、操作ID、skip reason、失敗サンプルを追加。
- JDK 25、Gradle 9.1.0、Paper API 26.1.2向けに更新し、Paper/Velocity成果物を別ディレクトリへ出すrelease taskを追加。

## 2.1 Bolt機能互換の拡張（2026-09-09）

- `/boltext set <protectionType>` と対応するscanで、Boltの登録済み任意protection typeを指定できるようにする。
- 既存保護のtype変更とaccess変更は、ownerに加えてBoltの `edit` accessを持つ利用者にも許可する。譲渡と解除はowner-onlyのまま維持する。
- Bolt Storeに存在確認できるグループだけを `group:<name>` Sourceとして追加・削除する。パスワードはコマンド履歴・ログ・監査プラグインへ残る危険があるため、CLI引数では扱わない。
- `/boltext entity <set|public|private|transfer|unlock|access>` と対応するscanを追加し、選択範囲内の現在ロード済みEntityだけを処理する。新規保護の可否、type、Entity lock permission、`LockEntityEvent`、WorldGuardをEntityごとに確認する。
- group SourceはBolt Storeの非同期照会で存在確認し、完了後にサーバースレッドで操作する。`scan`のイベント未評価分は「作成見込み」として別集計する。

残る実サーバー確認:

- Boltで有効にしたEntity種別（例: armor stand, item frame）と、Entity種別ごとの `bolt.protection.lock.<entity>` を使い、新規作成・type変更・イベントキャンセルを確認する。
- WorldGuardのDENY領域境界をまたぐ選択で、Entity位置ごとに拒否されることを確認する。
- `edit` accessをプレイヤーSourceとgroup Sourceの両方で付与し、owner以外のtype/access変更だけが通り、譲渡・解除は通らないことを確認する。

検証状況:

- `git diff --check`: 成功。
- VelocityソースのJDK 25個別コンパイル: 成功。
- Gradle 9.1.0がJDK 25対応であることを公式互換表と照合済み。
- この実行環境ではGradleのユーザー領域書き込みと内部ロック通知ソケットが制限されるため、`./gradlew release` の完走確認は未実施。制限のない環境でreleaseビルドとPaper実サーバー試験が必要。

## 確認した範囲

- `src/main/java/com/noasaba/boltextension/BoltExtension.java`
- `src/main/resources/plugin.yml`
- `src/main/resources/config.yml`
- `build.gradle`
- `README.md`

## ビルド確認

### 結果

- デフォルトの `java version "25.0.2"` では `./gradlew build` が失敗。
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-22.jdk/Contents/Home ./gradlew build` では成功。
- テストソースは存在せず、Gradle 出力は `test NO-SOURCE`。

### 失敗内容

```text
BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_' Unsupported class file major version 69
```

原因は、Gradle 8.8 / Groovy 3.0.21 が JDK 25 の class file major version 69 に対応していないこと。コード本体のコンパイルエラーではない。

## Bolt API 確認メモ

Gradle キャッシュ上の `bolt-bukkit-1.1.52-noshade.jar` / `bolt-common-1.1.52-noshade.jar` を `javap` と同梱 `config.yml` で確認した。

重要な API:

- `BoltAPI.isProtectable(Block)`
- `BoltAPI.isProtected(Block)`
- `BoltAPI.isProtectedExact(Block)`
- `BoltAPI.loadProtection(Block)`
- `BoltAPI.findProtection(Block)`
- `BoltAPI.findProtections(World, BoundingBox)`
- `BoltAPI.canAccess(Protection, Player, String...)`
- `BoltAPI.createProtection(Block, UUID, String)`

確認できた挙動:

- `isProtectable(Block)` は Bolt の protectable block map に含まれる Material かを見ている。
- Bolt デフォルト設定の `blocks:` には chest, furnace, barrel, hopper, doors, signs などが並ぶが、すべての Material が保護対象になるわけではない。
- `createProtection(Block, UUID, String)` はブロック種別を検証せず、渡された block の world / x / y / z / material から `BlockProtection` を作る。

つまり、現行コードのように未保護ブロックへ直接 `createProtection` すると、Bolt 的には保護対象ではない任意のブロックにも保護データを作れてしまう可能性が高い。新規作成前には必ず `bolt.isProtectable(block)` を通すべき。

## レビュー所見

### P0: Bolt の保護対象判定を通さず新規保護を作成している

対象: `BoltExtension.java:240-267`

`handleProtection` は未保護ブロックに対して常に `bolt.createProtection(block, player.getUniqueId(), type)` を呼ぶ。Bolt API 側の `createProtection` は block が protectable かを検証しないため、本来 Bolt が保護対象として扱わない任意のブロックにも保護を作れる。

修正方針:

- 未保護ブロックに新規保護を作る前に `bolt.isProtectable(block)` を必ず確認する。
- `isProtectable == false` のブロックは `skippedNotProtectable` として集計する。
- 既存保護があるブロックの type 変更と、未保護ブロックの新規作成は別ルールに分ける。
- Bolt の保護対象外ブロックを含む選択範囲で `public/private` を実行しても、そのブロックに保護が作られないことを手動検証する。

### P2: owner-only 仕様の明文化が必要

対象: `BoltExtension.java:250-256`, `BoltExtension.java:289-295`, `BoltExtension.java:312-315`

通常ユーザーは、いったん `protection.getOwner().equals(player.getUniqueId())` の owner-only 仕様にする。Bolt API には `canAccess(Protection, Player, "edit")` があるが、これはデバッグ表示には出しつつ、通常操作の許可条件にはまだ使わない。

修正方針:

- 通常操作の許可条件は owner-only としてコードと README に明記する。
- `inspect` には `ownerMatch` と `bolt.canAccess(..., "edit")` の両方を表示し、将来 Bolt access model に寄せる判断材料を残す。
- admin 操作は owner-only をバイパスする。

### P0: WorldGuard 権限チェックが範囲全体を保証していない

対象: `BoltExtension.java:98-118`

`checkWorldGuardAccess` は選択範囲の 8 隅を確認し、どれか 1 点でも許可されると `true` を返す。これにより、選択範囲の一部だけ権限がある場合でも、権限がない領域を含めて一括操作できる可能性がある。また、8 隅だけでは範囲内部に存在する deny region を検出できない。

修正方針:

- `||` ではなく、選択範囲に重なる WorldGuard region 全体を評価する。
- WorldGuard の region manager から cuboid selection と交差する region set を取得し、全 region に対して flag / owner / member を判定する。
- 点チェックを残す場合でも、少なくとも全チェックポイントが許可されることを要求する。ただし内部 region を見逃すため、根本解決にはならない。

### P0: WorldGuard が「任意依存」として安全に扱われていない

対象: `BoltExtension.java:9-17`, `BoltExtension.java:41-55`, `config.yml:8-11`, `plugin.yml`

README では WorldGuard はオプション扱いだが、メインクラスが WorldGuard 型を直接参照し、設定のデフォルトも `worldguard.enabled: true`。サーバーに WorldGuard がない状態で起動した場合、設定やロード順によっては `NoClassDefFoundError` や flag 登録失敗でプラグインが起動できない可能性がある。

修正方針:

- WorldGuard を必須にするなら `plugin.yml` に `depend: [WorldEdit, Bolt, WorldGuard]` を明記する。
- 任意にするなら `softdepend: [WorldGuard]` を明記し、WorldGuard 連携クラスを分離する。
- 任意依存のままなら `worldguard.enabled` のデフォルトを `false` にするか、プラグイン存在確認後だけ WorldGuard API に触れる。

### P1: `plugin.yml` に依存関係が未宣言

対象: `plugin.yml`

コードは WorldEdit と Bolt を必須として扱っているが、`plugin.yml` に `depend` がない。ロード順が保証されず、`onLoad` / `onEnable` で依存プラグインやサービスがまだ準備できていないケースが起こり得る。

修正方針:

- 必須依存: `depend: [WorldEdit, Bolt]`
- WorldGuard を任意にする場合: `softdepend: [WorldGuard]`
- WorldGuard flag 登録のロード順要件を、実サーバーで確認する。

### P1: `max-volume: 0` のコメントと実装が矛盾

対象: `config.yml:5`, `BoltExtension.java:184-190`

設定コメントでは `0` が無制限と書かれているが、実装では `volume > maxVolumeThreshold` をそのまま評価するため、`max-volume: 0` にすると 1 ブロック以上の選択が拒否される。

修正方針:

- `maxVolumeThreshold <= 0` のときは上限チェックをスキップする。
- コメントと README も実装に合わせて更新する。

### P1: volume 計算が `int` でオーバーフローする

対象: `BoltExtension.java:181-190`

`int volume` で 3 次元の積を計算しているため、大きな WorldEdit 選択範囲では負数や小さい値にオーバーフローし、上限チェックをすり抜ける可能性がある。

修正方針:

- `long volume` で計算する。
- `max-volume` も `long` として読む。
- 可能なら積の途中で上限超過を検出し、巨大範囲での無駄な計算を避ける。

### P1: admin confirm が操作内容・範囲・期限に紐づいていない

対象: `BoltExtension.java:323-363`

`/boltext admin unlock` 後の確認状態は `UUID -> true` だけで保持される。確認前に選択範囲を変える、時間を空ける、権限状態が変わる、といったケースでも `/boltext confirm` が実行できる。

修正方針:

- 確認状態に world 名、選択範囲、操作種別、作成時刻を保存する。
- `/boltext confirm` 時に admin 権限を再確認する。
- 確認期限を短く設定する。例: 30 秒から 60 秒。
- confirm 実行時に、現在の選択が保存された範囲と一致するか確認する。

### P2: 引数不足でも先に WorldEdit 選択が必要になる

対象: `BoltExtension.java:170-194`, `BoltExtension.java:205-208`

`/boltext` や `/boltext transfer` のような使用方法確認だけのケースでも、先に WorldEdit selection を取得する。範囲未選択の場合、usage ではなく「範囲選択が不完全です」が返る。

修正方針:

- `args` を先に検証し、usage を返せるケースでは selection を要求しない。
- 範囲が必要なサブコマンドだけ selection を取得する。

### P2: 処理途中の例外で全体が中断し、失敗箇所が分かりにくい

対象: `BoltExtension.java:240-363`

各ブロック処理で `bolt.loadProtection`, `bolt.saveProtection`, `bolt.removeProtection` を呼び出しているが、ブロック単位の例外処理やエラー件数の集計がない。1 箇所の例外で処理全体が落ち、どの座標で失敗したか分かりにくい。

修正方針:

- ブロック単位で例外を捕捉し、失敗数と代表座標を集計する。
- `logging.debug` と `logging.max-errors` を実装する。
- ユーザーには成功数・スキップ数・失敗数を返す。

### P2: Bolt の exact protection と matched protection の扱いが未整理

対象: `BoltExtension.java:240-363`

現行コードは `loadProtection(block)` だけを見る。Bolt API には `findProtection(block)` と `isProtectedExact(block)` もあり、ドア・チェスト・ベッドなど、クリック位置や連結ブロックによって代表 protection が変わる可能性がある。

未整理の論点:

- 連結チェストの片側だけが範囲に入ったとき、どの protection を変更するか。
- ドアの上半分だけが範囲に入ったとき、下半分の protection を変更するか。
- `findProtection` を使う場合、同じ protection を複数ブロックから拾って二重処理しないよう ID で dedupe する必要がある。
- `unlock` は stale protection cleanup の意味もあるため、現在の block が `isProtectable == false` でも既存 exact protection は削除できたほうがよい可能性がある。

修正方針:

- 操作ごとに `loadProtection`, `findProtection`, `isProtectable` の使い分けを決める。
- 処理済み protection ID を `Set<UUID>` で保持し、matched protection の二重変更を防ぐ。
- 新規作成は `isProtectable` 必須、既存変更・削除は protection の存在と edit 権限を優先する、というルールを第一候補にする。

### P3: 例外ログが `printStackTrace` になっている

対象: `BoltExtension.java:198-200`

Bukkit/Paper のログ文脈に乗りにくく、ユーザー向けメッセージにも詳細がない。

修正方針:

- `getLogger().log(Level.SEVERE, "...", e)` を使う。
- 既存の `logging.debug` コメントを実装するか、コメントを削除して設定を簡素化する。

### P3: バージョン情報が二重管理になっている

対象: `build.gradle:5-6`, `build.gradle:55-62`, `plugin.yml:2`

`build.gradle` は `version = '1.1-SNAPSHOT'` だが、`plugin.yml` は `version: 1.0-SNAPSHOT`。`processResources` で `expand props` しているものの、`plugin.yml` 側が `${version}` になっていないため反映されない。

修正方針:

- `plugin.yml` を `version: ${version}` に変更する。
- README と config コメントのバージョン表記も必要に応じて更新する。

### P3: 生成物と OS ファイルがリポジトリに混ざりやすい

対象: `.gradle/`, `build/`, `.DS_Store`

`.gitignore` がなく、ビルド実行で `.gradle/` と `build/` の生成物が差分に出る。レビューやリリース時に不要な差分が混ざりやすい。

修正方針:

- `.gitignore` を追加し、少なくとも `.gradle/`, `build/`, `.DS_Store` を除外する。
- 既に追跡済みの生成物をリポジトリから外すかどうか決める。

## 踏み込んだ設計変更案

### 1. 1 枚岩のコマンドクラスを分割する

現状の `BoltExtension` は、プラグイン起動、依存確認、WorldGuard 判定、WorldEdit selection、Bolt 操作、集計、メッセージ、tab 補完をすべて持っている。デバッグするには責務が混ざりすぎている。

分割候補:

- `BoltExtension`: plugin lifecycle と service wiring だけ。
- `BoltextCommand`: 引数解析、usage、tab 補完。
- `SelectionService`: WorldEdit selection の取得と volume 計算。
- `WorldGuardAccessService`: region / flag / member / owner 判定。
- `BoltProtectionService`: Bolt API 呼び出しを薄く包む。
- `BlockEligibilityPolicy`: 新規作成・変更・削除してよい block / protection かを判定。
- `RegionScanner`: selection 内の block を走査し、対象候補を列挙する。
- `OperationRunner`: `public`, `private`, `transfer`, `unlock` を実行する。
- `OperationSummary`: success / skipped / failed / reasons を集計する。
- `ConfirmationService`: admin confirm の状態、期限、範囲一致を管理する。

### 2. 操作を preflight と execute に分ける

各コマンドをすぐ実行せず、まず `OperationPlan` を作る。

`OperationPlan` に入れる情報:

- 対象 world と selection bounds
- operation type
- target protection type または transfer target
- 対象 protection IDs
- 新規作成予定 block locations
- skip reasons
- estimated changes

流れ:

1. 引数を parse する。
2. selection と volume を検証する。
3. WorldGuard を検証する。
4. Bolt の protectable / protection / access を検証して `OperationPlan` を作る。
5. summary を表示する。
6. confirm が必要な操作なら `ConfirmationService` に保存する。
7. execute で plan を再検証してから保存・削除する。

これにすると「なぜこのブロックが保護対象外としてスキップされたか」「なぜこの chest は対象外か」を plan の段階で説明できる。

### 3. デバッグ専用コマンドを追加する

候補:

- `/boltext inspect`
- `/boltext scan <public|private|transfer|unlock> [args...]`
- `/boltext debug block`

`inspect` / `debug block` で見たい情報:

- block world / x / y / z / material
- `bolt.isProtectable(block)`
- `bolt.isProtectedExact(block)`
- `bolt.loadProtection(block)` の有無
- `bolt.findProtection(block)` の有無
- protection id / owner / type / block material
- owner match
- `bolt.canAccess(protection, player, "edit")`
- WorldGuard の applicable regions
- `bolt-extension-allow` flag の評価結果
- 最終判定: create / update / transfer / unlock 可能か、不可なら理由

`scan` で見たい summary:

- total scanned blocks
- protectable blocks
- existing exact protections
- matched protections
- editable protections
- new protections to create
- skipped not protectable
- skipped no permission
- `PROTECTION_TYPE_UNKNOWN`: Boltに存在しない保護タイプ
- `PROTECTION_TYPE_DENIED`: 制限付き保護タイプの権限不足
- `BLOCK_LOCK_PERMISSION_DENIED`: Material別lock権限不足
- `LOCK_EVENT_CANCELLED`: Bolt連携イベントで作成が拒否された
- skipped worldguard denied
- skipped duplicate protection
- failed blocks

### 4. skip reason をコード上の第一級データにする

文字列ログだけでなく、enum として扱う。

候補:

- `NOT_PROTECTABLE`
- `NO_PROTECTION`
- `NO_EDIT_ACCESS`
- `OWNER_MISMATCH`
- `WORLDGUARD_DENIED`
- `DUPLICATE_PROTECTION`
- `TYPE_ALREADY_SET`
- `TRANSFER_TARGET_SAME_AS_OWNER`
- `BOLT_ERROR`

これを summary と debug log の両方に使うと、調査時に「何が多くて何が少ないか」が一目で分かる。

### 5. 決定済み仕様

ユーザー回答を反映した暫定仕様:

- `public/private` は、選択範囲内の該当ブロックをそれぞれ指定 type に変更する。
- 既存 protection がある場合は type を変更する。
- 未保護かつ `bolt.isProtectable(block) == true` の場合は新規 protection を作成する。
- 未保護かつ not protectable の block はスキップする。
- 通常ユーザーの変更条件は owner-only にする。
- 既存 protection がある場合、現在の block が not protectable でも `unlock` で削除できるようにする。
- ドア、連結チェスト、ベッドなどは一部だけ選択範囲に入っていても扱う。実装では `findProtection(block)` を使い、protection ID で dedupe する。
- admin unlock は WorldGuard と owner-only を無視できる。admin 権限は基本的に op へだけ渡す前提。
- デバッグ機能は実装側に任せるが、方針として `/boltext inspect` と `/boltext scan ...` の両方を用意する。

未決または後で詰める論点:

- 大量の新規作成を confirm 必須にする閾値。
- owner-only から Bolt の `edit` access へ広げるかどうか。
- `scan` の出力をチャットだけにするか、ログにも詳細を出すか。

### 6. 操作別ルール案

`public/private`:

- 範囲内の各 block に対して `findProtection(block)` を確認する。
- 既存 protection が見つかったら、protection ID で dedupe したうえで owner-only を確認し、type を変更する。
- 既存 protection が見つからない場合のみ `isProtectable(block)` を確認し、true なら新規作成する。
- not protectable は `NOT_PROTECTABLE` としてスキップする。

`transfer`:

- `findProtection(block)` で見つかった既存 protection を対象にする。
- protection ID で dedupe する。
- 通常ユーザーは owner-only。
- target が現在 owner と同じ場合は `TRANSFER_TARGET_SAME_AS_OWNER` としてスキップする。

`unlock`:

- `findProtection(block)` で見つかった既存 protection を対象にする。
- protection ID で dedupe する。
- 通常ユーザーは owner-only。
- 既存 protection があるなら、現在の block が not protectable でも削除可能にする。

`admin unlock`:

- `findProtection(block)` で見つかった既存 protection を対象にする。
- protection ID で dedupe する。
- owner-only と WorldGuard をバイパスする。
- `OperationPlan` を保存して confirm で同じ plan を実行する。

## デバッグ計画

### Phase 1: 環境とビルドの安定化

1. 開発用 JDK を 21 または 22 に固定する。
2. JDK 25 を使う必要があるなら Gradle wrapper を JDK 25 対応版へ更新する。
3. `.gitignore` を追加して、生成物の差分を抑止する。
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-22.jdk/Contents/Home ./gradlew build` を再現コマンドとして記録する。

完了条件:

- clean checkout から同じコマンドでビルドが成功する。
- ビルド後にソース以外の不要差分が出ない。

### Phase 2: 依存関係と起動順の整理

1. WorldGuard を必須にするか任意にするか決める。
2. `plugin.yml` に `depend` / `softdepend` を追加する。
3. WorldGuard なし、WorldGuard あり、WorldGuard disabled の 3 ケースでサーバー起動を確認する。
4. custom flag `bolt-extension-allow` が期待どおり登録されることを確認する。

完了条件:

- 依存プラグイン不足時のエラーが明確。
- optional と書いている依存が、実際にも optional として動く。

### Phase 3: WorldGuard 権限判定の修正

1. 選択範囲と重なる region を取得する実装に変更する。
2. `allow-no-region`, `flag-default`, region owner/member の仕様をテストケース化する。
3. 8 隅だけ許可、内部だけ deny、複数 region overlap の手動検証ワールドを作る。
4. admin unlock では WorldGuard 判定を明示的にバイパスする。

完了条件:

- 権限のない region を含む選択範囲では一括操作できない。
- region なし範囲の扱いが config と一致する。
- admin unlock は WorldGuard deny region でも実行できる。

### Phase 4: コマンド安全性の修正

1. 引数検証を selection 取得より前に移動する。
2. volume 計算を `long` にし、`max-volume <= 0` の仕様を実装する。
3. admin confirm を操作内容・範囲・期限に紐づける。
4. confirm 時にも admin 権限と selection を再検証する。

完了条件:

- `/boltext` は範囲未選択でも usage を返す。
- 巨大範囲でも volume 上限をすり抜けない。
- admin confirm が別範囲や期限切れで実行されない。

### Phase 5: Bolt 操作の観測性と失敗耐性

1. ブロック単位の成功・スキップ・失敗数を集計する。
2. 代表的な失敗座標をログに出す。
3. `isProtectable`, `loadProtection`, `findProtection`, owner match, `canAccess` の結果を summary に出せるようにする。
4. `findProtection` の結果を protection ID で dedupe する。
5. public/private の対象ブロック種別を仕様として固定する。
6. 大量操作時の実行時間とサーバー負荷を測る。

完了条件:

- ユーザーが結果を見て何が起きたか判断できる。
- 1 ブロックの失敗で全体が不透明に落ちない。
- 一部だけ範囲に入ったドア、連結チェスト、ベッドでも二重処理されない。

### Phase 6: preflight / inspect の追加

1. `OperationPlan` と `OperationSummary` を導入する。
2. 実行せずに対象件数と skip reasons を出す `scan` 系コマンドを追加する。
3. 見ている block の Bolt / WorldGuard 判定を出す `inspect` 系コマンドを追加する。
4. admin confirm は `OperationPlan` を保存し、confirm 時に同じ plan を実行する形に変える。

完了条件:

- 任意のブロックがなぜ対象外か、1 コマンドで説明できる。
- 本実行前に「何件作る・何件変える・何件飛ばす」が分かる。
- confirm 後に別範囲へすり替わらない。

## 推奨する最初の修正順

1. `.gitignore` とビルド JDK の整理。
2. `plugin.yml` の依存関係と `${version}` 反映。
3. Bolt 操作ルールの実装。特に `isProtectable`, `findProtection`, owner-only, dedupe の扱い。
4. `max-volume` と `long volume` 修正。
5. WorldGuard 権限判定の修正。
6. `OperationSummary` と skip reason 集計の導入。
7. admin confirm の状態管理強化。
8. `inspect` / `scan` 系デバッグコマンド追加。

## 2026-09 セキュリティ検証

- WorldGuardの最終認可は選択範囲ではなく、操作対象の各Block位置で行う。離れたリージョン同士のpriorityを混在させない。
- 新規Protectionは `isProtectable`、ProtectableConfig、type権限、Material別lock権限、WorldGuard、LockBlockEventを通過した場合だけ作成する。
- `scan` はLockBlockEventを発火しないため、`LOCK_EVENT_NOT_EVALUATED` を結果に表示する。
- 通常操作はowner限定、admin unlockはconfirm時に選択・Protection ID・位置・WorldGuardを再確認する。
- 手動確認: 離れたDENY/ALLOWリージョン、STONE、CHEST、他人所有、Boltコマンド権限拒否、restricted access type、offline transferをそれぞれ確認する。
