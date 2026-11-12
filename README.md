# Pocket Markdown 2.0

An Android app for reading and editing Markdown files.

**Reading:** outline drawer, find in document, reading progress, remembers where you stopped in each file, bars that slide away while you scroll, tap-to-zoom images.
**Rendering:** tables, task lists you can tick, code with syntax colours and a copy button, Mermaid diagrams, LaTeX math, GitHub callouts (`> [!NOTE]`), front matter.
**Views:** Read, Edit (with formatting bar) and Split.
**Display:** Auto, Light, Sepia, Dark and Black themes; serif, sans or mono text; text size, line spacing and page width; justify; keep screen on.
**Files:** open from the picker, from **Open with** in My Files, or via **Share**; save as `.md` or `.html`; share as text; recent files.

## Get the APK

GitHub Actions builds it on every push (tab **Actions**, about 3 minutes).
On your phone, open the repository's **Releases** page and tap `PocketMarkdown.apk`.

Builds are signed with `app/pocketmd.keystore`, so each new build installs over the last one.
This key is public in this repo, which is fine for a personal app but not for the Play Store.

## Build locally instead

Download the five libraries listed in `.github/workflows/build-apk.yml` into
`app/src/main/assets/`, then open the folder in Android Studio.
