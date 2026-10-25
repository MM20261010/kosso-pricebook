# こっそり価格帳 (Kosso Pricebook) · Android試作 v0.3

**Android実機未検証のソース試作。現段階ではAPKの完成やOCRの精度を保証しません。**

買い物前にメモを作成し、店内では過去の店別購入価格を確認。帰宅後はレシートを端末内OCRで読み取って、購入明細を一括保存し、次回の買い物に使います。

## プライバシー優先
- アプリは `INTERNET`・位置情報・カメラ権限を要求しません。外部カメラアプリ・システムの画像選択を利用します。
- レシート・認識テキスト・購入明細は運営者のサーバーへ送信しません。履歴はアプリ内SQLiteに保存します。
- Android自動バックアップは無効化し、JSONバックアップ・復元は利用者が明示操作します。クラウド保存先を選ぶとその提供者へ保存されます。
- OCR学習モデル `jpn`・`eng` は**ビルド時**にAPKへ同梱し、スマホ側では通信・ダウンロードしません。

## v0.3 実装コード
- 買い物メモ、チェック、数量変更、店別前回税込価格、最終購入日、特売マーク、価格履歴・税抜換算前回比
- システムカメラ・写真選択による端末内OCR、商品候補・数量・税率・値引きの確認、一括保存
- 手入力、SQLite保存・個別履歴削除、JSONバックアップ・復元

## ビルドと検証
JDK 17、Android SDK 35、Gradle 8.11.1、Android Gradle Plugin 8.9.2 を想定。

```sh
sh tests/test-price-math.sh
sh tests/test-parser.sh
sh tests/privacy-sanity.sh
sh scripts/fetch-ocr-models.sh
gradle :app:assembleDebug --no-daemon
```

`app/build/outputs/apk/debug/app-debug.apk` が成果物です。
GitHub Actions の `Test APK (manual run)` は**手動実行専用**。実行後はActionsのArtifactsからデバッグAPKを取得できます。

**未検証・要改善**: SDK上のビルド成否、Android実機のOCR精度、レシートの複雑な割引・単価・数量表記、重複登録、商品名の揺れと規格差、更新用署名鍵の固定。現在のOCRモデル取得はリモートmainを参照しているため、公開前にリビジョンとSHA256を固定してください。正式配布やF-Droid掲載は保留。

価格履歴は過去の購入価格で、店頭の現在価格ではありません。税抜換算の価格変化だけで「便乗値上げ」とは断定しないでください。

ライセンス: Apache-2.0。レシート画像・個人の購入データ・秘密鍵・署名鍵・SDK生成物はリポジトリに入れないこと。
