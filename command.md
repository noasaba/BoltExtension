# BoltExtension Commands and Permissions

## 一般ユーザー向け

通常操作では WorldEdit の選択範囲、WorldGuard のカスタムフラグ・BUILD判定・リージョン所属、対象保護の owner 判定が必要です。

| 権限名 | チャットコマンド | 使い方の例 | 具体的な解説 |
| --- | --- | --- | --- |
| `bolt.extension.use` | `/boltext public` | `/boltext public` | 選択範囲内で自分が owner の保護を `public` に変更します。未保護かつ Bolt が保護可能と判定したブロックには新規保護を作成します。連結ブロックの代表保護が範囲外なら、その場所のWorldGuard権限も確認します。 |
| `bolt.extension.use` | `/boltext private` | `/boltext private` | 選択範囲内で自分が owner の保護を `private` に変更します。未保護かつ保護可能なブロックには新規保護を作成します。 |
| `bolt.extension.use` | `/boltext transfer <targetPlayer>` | `/boltext transfer Steve` | 選択範囲内で自分が owner の保護を、指定したオンラインプレイヤーへ譲渡します。 |
| `bolt.extension.use` | `/boltext unlock` | `/boltext unlock` | 選択範囲内で自分が owner の既存保護を削除します。現在のブロックが保護対象外でも、既存保護が見つかれば削除できます。 |
| `bolt.extension.scan` | `/boltext scan public` | `/boltext scan public` | `public` 操作の作成・変更・スキップ件数を、実際に変更せず確認します。 |
| `bolt.extension.scan` | `/boltext scan private` | `/boltext scan private` | `private` 操作の作成・変更・スキップ件数を、実際に変更せず確認します。 |
| `bolt.extension.scan` | `/boltext scan transfer <targetPlayer>` | `/boltext scan transfer Steve` | transfer の対象・スキップ件数を、owner を変更せず確認します。 |
| `bolt.extension.scan` | `/boltext scan unlock` | `/boltext scan unlock` | unlock の削除対象・スキップ件数を、実際に削除せず確認します。 |

## 管理者・デバッグ向け

| 権限名 | チャットコマンド | 使い方の例 | 具体的な解説 |
| --- | --- | --- | --- |
| `bolt.extension.inspect` | `/boltext inspect` | 対象ブロックを見ながら `/boltext inspect` | Material、保護可能判定、exact/matched protection、owner、Bolt edit access、WorldGuard の許可結果と理由を表示します。変更は行いません。 |
| `bolt.extension.debug` | `/boltext debug status` | `/boltext debug status` | Java、サーバー、Bolt、WorldEdit、WorldGuard のバージョンと、現在読み込まれている主要設定を表示します。 |
| `bolt.extension.admin` | `/boltext admin unlock` | `/boltext admin unlock` | owner と WorldGuard をバイパスして、選択範囲内の保護を削除予定に登録します。実削除には confirm が必要です。 |
| `bolt.extension.admin` | `/boltext confirm` | `/boltext confirm` | 期限内の admin unlock plan を確定します。実行直前に現在の protection ID を再確認してから削除します。 |
| `bolt.extension.admin` | `/boltext scan admin unlock` | `/boltext scan admin unlock` | owner と WorldGuard をバイパスした場合の削除対象件数を、変更せず確認します。 |

## 権限ノード一覧

| 権限名 | デフォルト | 用途 |
| --- | --- | --- |
| `bolt.extension.use` | `true` | 一般の public/private/transfer/unlock |
| `bolt.extension.scan` | `true` | 一般操作の dry-run |
| `bolt.extension.inspect` | `op` | ブロック単位のBolt/WorldGuard診断 |
| `bolt.extension.debug` | `op` | バージョン・設定・エラー詳細の診断 |
| `bolt.extension.admin` | `op` | owner/WorldGuardをバイパスする管理者削除 |
