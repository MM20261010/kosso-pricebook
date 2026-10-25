# v0.5 端末内OCR比較版

正式採用前の比較用デバッグ版。v0.4の購入履歴、買い物メモ、確認保存、JSON形式は維持します。実レシートの精度・操作性・処理時間・メモリはAndroid実機で判定してください。

## 使い方

1. 「記録」で Tesseract または PaddleOCR v5 / ncnn を選び、写真を選択または撮影する。
2. もう一方を選び、「同じ画像を選択中の方式で読む」を押す。画像を再選択する必要はありません。
3. 両方式の元テキストと商品候補、時間、メモリ標本を比較する。「確認・修正画面を開く」で各結果を確認する。
4. 比較は購入履歴へ自動保存しません。登録は従来どおり全候補未選択で、商品・数量・値引後税込単価・税率を本人が確認して保存します。終了時は比較画像と結果を破棄できます。

両方式に渡すのは、一度だけ展開・EXIF補正・白背景化した同一Bitmapです。Tesseract側は従来の画像補正候補とPSM選択を維持し、Paddle側は専用の検出・認識前処理を行います。画面回転・Activity終了では比較画像を破棄します。元テキストは編集用テキストとは分け、比較画面内のメモリだけに保持します。OSによる既存の編集テキスト状態復元は維持します。

時間はモデル検証・初期化・OCRを含み画像展開を除く壁時計時間。メモリは200ms間隔で取得する**プロセス全体のPSS最大標本**です。エンジン単体の消費量・正確なネイティブピークではなく、短時間のピークを取り逃す可能性があります。2方式のメモリには共通画像と前の実行の残存アロケータ等も影響します。画像サイズと端末、実行順を揃え、複数回試してください。測定値は端末内表示のみです。

## 構成・出所・再配布

| 部品 | 固定版・出所 | ライセンス・用途 |
|---|---|---|
| PP-OCRv5 Mobile det/rec | PaddlePaddle公式HF、固定リビジョンは `scripts/paddle-models.json` | 公式モデルカード Apache-2.0、APK同梱 |
| ncnn | Tencent公式 20261020 | BSD-3-Clauseと同梱第三者通知、CPU推論 |
| OpenCV | 公式 4.14.0、固定コミットは `scripts/native-sources.json` | Apache-2.0と同梱第三者通知、core/imgprocのみ |
| Clipper | 6.4.2、fonttools公式pyclipper 1.4.0内の固定ソース | Boost Software License 1.0、DBボックス拡張 |
| Tesseract4Android | 既存4.9.0 | 既存の基準方式、jpn/engモデル変更なし |

モデル/ネイティブソースの配布URL・SHA-256は上記JSONで固定します。変換用Python依存はCPython 3.12 Linux x86_64 wheelの全ハッシュを `scripts/paddle-conversion-requirements.txt` に固定。Paddle 3.3.1 → paddle2onnx 2.1.0 (opset13) → PNNX 20261020、FP32。PNNXは公式PyPI wheelと抽出実行ファイル双方のSHA-256を検証します。Torch、ONNX Runtime、MNNをアプリに導入していません。

入力はdet 1×3×960×960、rec 1×3×48×960。DB検出、Clipperによる拡張、台形補正、CTC復号、文字列の傾き/高さを用いた行順序復元を行います。GPU/Vulkan/FP16/BF16/INT8/OpenMPは無効、1スレッド。追加共有ライブラリはC++ランタイムを静的リンクし、Android標準のlibandroid/libjnigraphics/libz/libdl/libm/liblog/libcを参照します。これらは外部SDKの追加ではありません。モバイルの一時メモリを抑えるためWinograd/SGEMMは無効です。前回Python比較の高速化設定とは異なるため、Androidの速度は別に測定する必要があります。

`assets/paddle/manifest.json` に元モデルの出所と変換後5ファイルのSHA-256・サイズ・変換条件を格納。モデル準備、APK検査、端末の各読取前に整合性を確認します。派生ファイルのハッシュは変換後に算出し、公式配布元のチェックサムと区別します。変換はLinux seccompでsocket/connect/send系を遮断して実行します。取得・pip導入だけはビルド時通信です。アプリにPythonや変換ツール・ダウンローダーは含めません。ライセンス全文、モデルカード、OpenCV第三者著作権通知をAPKのassets/licensesへ収録します。

## 三原則の検査

- 機能: 同一画像の両方式比較、独立した元テキスト/候補、既存の保守的な確認保存を維持。`2026/10/2017:35` 等の日付時刻連結を扱い、曖昧な1桁日と時刻の連結、24時、不正な分・日付は拒否。PriceDb/PriceMathとDBスキーマ/JSON形式は変更しません。
- プライバシー: Manifestの権限・バックアップ・外部Intentに追加なし。OCR結果・画像・計測値をログ/通信/履歴へ自動出力しません。ネイティブコードはncnnのCPU演算とOpenCV core/imgprocのみ。動画・画像ファイルコーデック・GStreamer・FFmpeg・OpenCL・Vulkan・ADE・Carotene・KleidiCV・FastCV・IPP・ITT・trace・並列バックエンドプラグインを無効化。ARM向け補助HALの自動取得も避けます。OpenCVのダウンロード事前フックは追加取得要求をビルドエラーにし、未固定の依存を黙って取得できないようにします。選択モジュールのsocket/connect/sendto/getaddrinfo/telemetry呼出しをソース検索し、追加ライブラリ内に該当する通信/テレメトリー処理は見つかっていません。最終Manifestと追加ELFの外部シンボルでも確認します。外部カメラ・選択先ストレージ自体の動作は従来どおり各アプリの設定に依存します。
- セキュリティ: 外部モデルを読み込まず署名APK内の検証済みモデルのみ使用。400万画素、960角検出、候補80件、行/全文/辞書のサイズ境界、JNI例外処理、並行実行防止と終了時の画像解放を実装。検出領域が多すぎる場合は部分結果を黙って返さず失敗を案内します。ネイティブのNXスタック・RELRO・実行書込領域不在・16KiB LOAD整列を検査します。

2026-10-25、公式GitHub security-advisoriesを確認。ncnn `GHSA-jxmc-3mv6-7pwr` はモデルの負パラメータIDに関する問題で、固定ソースのテキスト/バイナリ両ローダーに下限/上限検査があることを確認、`NCNN_VALIDATION=ON` を明示します。`GHSA-c44p-qr97-jccv` は上流CIの問題で、そのワークフローを利用しません。OpenCVの同API公開一覧は空でした。公開情報の確認であり、未発見脆弱性がないことやネイティブ全体の安全性を保証する監査ではありません。不要な画像コーデックは組み込まず、デコードは既存Android側の境界検査を使用します。

## 再現

JDK17、Gradle8.11.1、SDK/Build Tools35、NDK27.2.12479018、CMake3.22.1、モデル変換用Python3.12。

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.2.12479018' 'cmake;3.22.1'
sh tests/test-price-math.sh
sh tests/test-parser.sh
sh tests/test-ocr-image.sh
sh tests/privacy-sanity.sh
sh scripts/fetch-ocr-models.sh
python3 scripts/prepare-native.py
python3 scripts/prepare-paddle.py
gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=2
python3 scripts/verify-debug-apk.py
```

手動Actionsはビルド/検査後にAPKを14日保存し、別ジョブの空のAOSP API35 x86_64エミュレータで架空画像だけを使用します。実レシートをCIに渡す入口は設けません。試験用APKは通常のインストールには不要です。署名は開発用debug鍵で、CIの別実行・既存インストールと一致することは保証しません。更新できないときはアンインストール前に管理画面からJSONを保存してください。

試験画像は `tests/generate-synthetic-receipts.py` 内の架空文字列とNoto Sans CJKで生成した6枚。Pillow/numpyがある開発用環境で `python3 tests/generate-synthetic-receipts.py /tmp/kosso-synthetic` として再生成できます。フォントとPNGのハッシュ、期待商品・金額・日付はandroidTest assetsのsynthetic-manifest.jsonに記録します。フォントのファイル自体や実レシートは収録しません。

## 検証結果

- ローカル基礎回帰: 価格計算7、解析40、画像補正5、計52件合格。静的privacy-sanity合格。
- 公式固定ソースからの新規モデル変換: 成功。前回独立比較実験の変換後5ファイルとSHA-256一致。
- Androidと共通のC++処理をLinux上で通信遮断して検証: 架空6枚の商品18件・行金額・日付がすべて一致。店名候補あり、印字数量2・単価100円も一致。Androidでの結果は別途確認します。
- ローカルAPK/試験APK生成: 成功。Lint 0 errors / 6 warnings。既存のExifInterface、AndroidX更新、固定縦画面、アイコン、文字列リソースの警告を維持し、今回の無関係な変更は行いません。
- ローカル最終APK: 署名、権限、バックアップ、jpn/engとPaddle全モデルハッシュ、ライセンス、3ABI、追加ELFの通信/プロセス起動API参照不在・NX/RELRO・実行書込領域不在・16KiB LOAD整列、zipalign -P16に合格。
- GitHub Actions: 最終構成の手動実行 [40169960201](https://github.com/MM20261010/kosso-pricebook/actions/runs/40169960201) 成功。基礎回帰52件、モデル/ソース取得と検証、APK・試験APK生成、Lint、最終APK検査、Artifactsアップロードを確認。
- Androidエミュレータ: 同じ実行のAOSP API35 x86_64で65項目合格。架空6枚、商品18件と金額・日付・店名候補、数量/印字単価、同一画素の保持、両OCRの実行、隔離したDBでのJSON往復を確認。Tesseractについては出力があることを確認するスモーク試験であり、実用精度の合格判定ではありません。
- 実機の精度、操作性、時間、正確なピークメモリ: 未検証。ARM実機でのネイティブ実行も未検証。

少数の合成画像の通過を実レシートの実用精度と同一視しません。長いレシート、湾曲、縦書き、逆さ写真、極小印字、複雑な割引・端数処理は残る検証対象です。

## 取得できる最終成果物

- [実機検証用APKのArtifacts](https://github.com/MM20261010/kosso-pricebook/actions/runs/40169960201/artifacts/12590495623) — GitHubへログインしてZIPを取得し、展開した `app-debug.apk` をインストール。試験用APKは不要です。保存期間14日。
- APKのアプリ/ワークフローソース: `b5e21d5ff68368f677aca2abb29649b8ea2bb09c`。この検証記録だけを後から更新しても、APKのソースコミットは変わりません。
- APK SHA-256: `6e47e419e64608aeb73c11099417c7c62316ed38fd41c486e67467da9077d0b9`
- 署名証明書SHA-256: `7836eec7e74173641f6e419c99bf2516bb17c08b9d5b89cce670bd416b2c4870`
- APK容量: 72,559,775 bytes (69.2 MiB)。取得したv0.4 APK（34,598,628 bytes）から 37,961,147 bytes (+36.2 MiB)。v0.4は4ABI、v0.5はARM64/ARM32/x86_64の3ABIで、圧縮・配置条件も含む実APK同士の差です。単一エンジンの純粋な増分ではありません。ローカルの増分ビルドAPKとはZIP内の未使用領域等でサイズが異なるため、このクリーンなGitHub成果物を配布値とします。
- ダウンロードした最終APKにも同じ検査を実行して合格。16KiB zipalignも確認しました。

既存v0.4配布APKの署名（`c555837266abca1b95199132049ba9e0c76b22080136220eba8d47592989d5f1`）とは異なります。既存データがある場合は、**旧版を削除する前に、管理画面でJSONを端末内へ保存してください**。署名不一致で上書きできない場合に限り、バックアップの保存を確認して旧版を削除し、v0.5をインストール後、管理画面からJSONを復元してください。アンインストールすると旧版のアプリ内データは消えます。DB/JSON形式自体は変更していません。

実機の認識時間・精度・ピークメモリは未検証です。アプリ画面で時間とPSS最大標本を比較できますが、正確なネイティブピークの測定とは区別してください。mainは自動マージしていません。PR #2は未マージのPR #1（v0.4とSDK修正）をbaseにしています。
