# Agent Instructions — Gboard Patches (shotgun-patches)

## Merge upstream
- `git merge --no-ff upstream/dev`
- Resolve merge conflicts manually

## Version bump
- Update `gradle.properties`: `version = <new>`
- Update `patches-list.json`: `"version": "<new>"`
- Update `patches-bundle.json`: `"version"`, `"created_at"`, `"download_url"`

## Build
- `./gradlew clean :patches:buildAndroid generatePatchesList`
- Requires `local.properties` with `sdk.dir`, or `gpr.user`/`gpr.key` (set in `local.properties` or `~/.gradle/gradle.properties`)
- Output: `patches/build/libs/patches-<version>.mpp`

## Release
- `git tag v<version>` and `git push origin v<version>`
- `gh release create v<version> --prerelease --repo Burhanverse/shotgun-patches`
- Upload `.mpp`: `gh release upload v<version> patches/build/libs/patches-<version>.mpp --repo Burhanverse/shotgun-patches --clobber`
