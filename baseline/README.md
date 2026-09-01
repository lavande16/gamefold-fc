# GameFold FC 1.1 baseline

Formal GameFold FC baseline promoted after user acceptance of the 1.0.12 candidate on vivo `V2436A` / `10AF3P0SDY001M6`.

- Application ID: `com.gamefold.gamefold`
- Version: `1.1`
- Version code: `13`
- APK: `GameFold-FC-baseline-v1.1-release.apk`
- SHA-256: `cfc6132dad0845a100622a4182883bb3ef410ded3b65d0a8a1aae888bc5e903c`
- Signature: APK Signature Scheme v2
- Certificate SHA-256: `24a37be7ed0f0431fde44018312271152545d689bb3d1fbe9fbf92914bdf84b4`

Accepted changes:

- Supplied 512 x 512 FC PNG used for launcher and round launcher icons.
- Game-library `GAMEFOLD FC` title uses the NS/PC 5 x 7 pixel wordmark at the existing 32-unit size.
- A/B and X/Y positions are swapped without changing button size or coordinates.

Verification:

- `:gamefold-fc:testReleaseUnitTest`: 2 tests, 0 failures, 0 errors, 0 skipped.
- `:gamefold-fc:assembleRelease`: successful, including release lint, R8, and resource shrinking.
- APK package/version metadata, icon resource wiring, v2 signature, certificate digest, and SHA-256 verified.
- Formal `1.1 / 13` install verified on vivo `V2436A` / `10AF3P0SDY001M6`; the pulled APK SHA-256 exactly matches this baseline APK.

Rollback: `artifacts/gamefold-fc-baselines/v1.0.10-release/`.
