# BoltExtension Commands and Permissions

## 一般ユーザー向け

通常操作では WorldEdit の選択範囲、WorldGuard のカスタムフラグ・BUILD判定・リージョン所属が必要です。既存Protectionのtype・access変更は owner または Bolt の `edit` access を持つ利用者だけが実行できます。譲渡・解除は owner のみです。

| 権限名 | チャットコマンド | 使い方の例 | 具体的な解説 |
| --- | --- | --- | --- |
| `bolt.extension.public` | `/boltext public` | `/boltext public` | ownerまたはBoltの`edit` accessを持つ既存保護を`public`へ変更し、保護可能な未保護ブロックだけを新規保護します。`bolt.command.lock`も必要です。 |
| `bolt.extension.private` | `/boltext private` | `/boltext private` | ownerまたはBoltの`edit` accessを持つ既存保護を`private`へ変更し、保護可能な未保護ブロックだけを新規保護します。`bolt.command.lock`も必要です。 |
| `bolt.extension.set` | `/boltext set <protectionType>` | `/boltext set donation` | Boltに登録済みの任意のprotection typeへ変更・新規作成します。type自体がrestrictedなら `bolt.type.protection.<type>` も必要です。`bolt.command.lock`も必要です。 |
| `bolt.extension.transfer` | `/boltext transfer <targetPlayer>` | `/boltext transfer Steve` | 自分がownerの既存保護を、Boltに登録済みのオンライン・オフラインプレイヤーへ譲渡します。`bolt.command.transfer`も必要です。 |
| `bolt.extension.unlock` | `/boltext unlock` | `/boltext unlock` | 自分がownerの既存保護を削除します。不正な既存Protectionも削除できます。`bolt.command.unlock`も必要です。 |
| `bolt.extension.access.add` | `/boltext access add <player> [accessType]` | `/boltext access add Steve normal` | 自分がownerの既存保護に対象プレイヤーのBolt Sourceを追加します。未保護ブロックは作成しません。`bolt.command.edit`も必要です。 |
| `bolt.extension.access.remove` | `/boltext access remove <player>` | `/boltext access remove Steve` | 指定プレイヤーのSourceだけを既存access mapから削除します。`user`は`access`のaliasです。`bolt.command.edit`も必要です。 |
| `bolt.extension.access.add` | `/boltext access add-group <group> [accessType]` | `/boltext access add-group builders normal` | Boltに登録済みのグループを `group:builders` 形式のSourceとして既存保護へ追加します。未保護ブロックは作成しません。`bolt.command.edit`も必要です。 |
| `bolt.extension.access.remove` | `/boltext access remove-group <group>` | `/boltext access remove-group builders` | 指定グループのSourceだけを既存access mapから削除します。`bolt.command.edit`も必要です。 |
| `bolt.extension.entity.set` | `/boltext entity set <protectionType>` | `/boltext entity set private` | 選択範囲にいる、現在ロード済みの保護可能Entityを保護・type変更します。各Entity位置でWorldGuard判定を行います。`bolt.command.lock`も必要です。 |
| `bolt.extension.entity.transfer` | `/boltext entity transfer <player>` | `/boltext entity transfer Steve` | 自分がownerのEntity Protectionを登録済みプレイヤーへ譲渡します。`bolt.command.transfer`も必要です。 |
| `bolt.extension.entity.unlock` | `/boltext entity unlock` | `/boltext entity unlock` | 自分がownerのEntity Protectionを削除します。`bolt.command.unlock`も必要です。 |
| `bolt.extension.scan` | `/boltext scan public` | `/boltext scan public` | `public` 操作の作成・変更・スキップ件数を、実際に変更せず確認します。 |
| `bolt.extension.scan` | `/boltext scan private` | `/boltext scan private` | `private` 操作の作成・変更・スキップ件数を、実際に変更せず確認します。 |
| `bolt.extension.scan` | `/boltext scan transfer <targetPlayer>` | `/boltext scan transfer Steve` | transfer の対象・スキップ件数を、owner を変更せず確認します。 |
| `bolt.extension.scan` | `/boltext scan unlock` | `/boltext scan unlock` | unlock の削除対象・スキップ件数を、実際に削除せず確認します。 |
| `bolt.extension.scan` | `/boltext scan set <protectionType>` | `/boltext scan set donation` | 任意typeへの変更・作成件数を、実際に変更せず確認します。 |
| `bolt.extension.scan` | `/boltext scan entity <...>` | `/boltext scan entity set private` | Entity操作をdry-runします。Entityの新規作成は外部イベントを発火しないため、実行時に結果が変わる可能性があります。 |

## 管理者・デバッグ向け

| 権限名 | チャットコマンド | 使い方の例 | 具体的な解説 |
| --- | --- | --- | --- |
| `bolt.extension.inspect` | `/boltext inspect` | 対象ブロックを見ながら `/boltext inspect` | Material、保護可能判定、exact/matched protection、owner、Bolt edit access、WorldGuard の許可結果と理由を表示します。変更は行いません。 |
| `bolt.extension.debug` | `/boltext debug status` | `/boltext debug status` | Java、サーバー、Bolt、WorldEdit、WorldGuard のバージョンと、現在読み込まれている主要設定を表示します。 |
| `bolt.extension.audit` | `/boltext audit invalid [page]` | `/boltext audit invalid 1` | 現在Boltが保護対象と見なさない既存Block Protectionを確認専用で表示します。自動削除はしません。 |
| `bolt.extension.admin.unlock` | `/boltext admin unlock` | `/boltext admin unlock` | ownerを問わず削除計画を作成します。`bolt.command.admin`も必要で、WorldGuardはバイパスせずconfirm時にも再確認します。 |
| `bolt.extension.admin.unlock` | `/boltext confirm` | `/boltext confirm` | 期限内で同一のWorldEdit選択だけを確定します。保護ID・位置・WorldGuardを削除直前に再確認します。 |

## 権限ノード一覧

| 権限名 | デフォルト | 用途 |
| --- | --- | --- |
| `bolt.extension.use` | `false` | 一般操作権限の親ノード |
| `bolt.extension.private` / `public` / `set` / `transfer` / `unlock` | `false` | 対応する通常操作 |
| `bolt.extension.access.add` / `remove` | `false` | 既存Protectionのaccess list操作 |
| `bolt.extension.entity.set` / `transfer` / `unlock` | `false` | 対応するEntity Protection操作 |
| `bolt.extension.scan` | `false` | 許可された一般操作のdry-run |
| `bolt.extension.inspect` | `op` | ブロック単位のBolt/WorldGuard診断 |
| `bolt.extension.debug` | `op` | バージョン・設定・エラー詳細の診断 |
| `bolt.extension.audit` | `op` | invalid Protectionの監査 |
| `bolt.extension.admin.unlock` | `op` | ownerをバイパスする確認付き管理者削除 |
| `bolt.extension.admin.access` | `op` | 将来の管理者access操作用予約ノード |
